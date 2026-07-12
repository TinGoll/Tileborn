package game.server.network

import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
import game.shared.protocol.InputCommand
import game.shared.protocol.PingRequest
import game.shared.protocol.PongResponse
import game.shared.protocol.Protocol
import game.shared.protocol.ProtocolCodec
import game.shared.protocol.WorldSnapshot
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Line-delimited JSON TCP transport for the first client/server handshake. */
class TcpGameServer(
    private val port: Int,
    private val mapIdProvider: () -> String,
    private val serverTickProvider: () -> Long,
    private val initialSnapshotProvider: ((Int) -> WorldSnapshot)? = null,
    private val inputCommandHandler: ((Int, InputCommand) -> WorldSnapshot?)? = null,
    private val disconnectSnapshotProvider: ((Int) -> WorldSnapshot?)? = null,
    private val logger: (String) -> Unit = ::println,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val nextEntityId = AtomicInteger(FIRST_PLAYER_ENTITY_ID)
    private val clients = CopyOnWriteArrayList<Socket>()
    private val sessions = CopyOnWriteArrayList<ClientSession>()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val isRunning: Boolean
        get() = running.get()

    val localPort: Int
        get() = serverSocket?.localPort ?: port

    fun start() {
        check(running.compareAndSet(false, true)) { "TCP game server is already running." }
        val socket = ServerSocket(port)
        serverSocket = socket
        acceptThread = Thread({ acceptLoop(socket) }, "tcp-game-server-accept").apply {
            isDaemon = true
            start()
        }
        logger("TCP server listening on ${socket.inetAddress.hostAddress}:${socket.localPort}")
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        clients.forEach { it.closeQuietly() }
        clients.clear()
        sessions.clear()
        serverSocket.closeQuietly()
        serverSocket = null
        acceptThread?.join(JOIN_TIMEOUT_MILLIS)
        acceptThread = null
        logger("TCP server stopped")
    }

    /** Sends an authoritative full-world snapshot to every connected player. */
    fun broadcastSnapshot(snapshot: WorldSnapshot, excludedEntityId: Int? = null) {
        sessions.filter { it.entityId != excludedEntityId }.forEach { session ->
            try {
                session.send(snapshot)
            } catch (_: Exception) {
                session.socket.closeQuietly()
            }
        }
    }

    private fun acceptLoop(serverSocket: ServerSocket) {
        while (running.get()) {
            try {
                val client = serverSocket.accept()
                clients += client
                logger("TCP connection opened from ${client.remoteSocketAddress}")
                Thread({ handleClient(client) }, "tcp-game-server-client").apply {
                    isDaemon = true
                    start()
                }
            } catch (exception: SocketException) {
                if (running.get()) {
                    logger("TCP accept failed: ${exception.logName()}")
                }
            } catch (exception: Exception) {
                if (running.get()) {
                    logger("TCP accept failed: ${exception.logName()}")
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.newReader().use { reader ->
                socket.newWriter().use { writer ->
                    val payload = reader.readLine()
                    if (payload == null) {
                        logger("TCP connection closed before handshake from ${socket.remoteSocketAddress}")
                        return
                    }

                    val message = ProtocolCodec.decodeClient(payload)
                    if (message !is JoinRequest) {
                        sendRejected(writer, "Expected JoinRequest as first message.")
                        logger("Join rejected from ${socket.remoteSocketAddress}: expected JoinRequest")
                        return
                    }

                    if (message.protocolVersion != Protocol.PROTOCOL_VERSION) {
                        sendRejected(
                            writer,
                            "Unsupported protocol version ${message.protocolVersion}; expected ${Protocol.PROTOCOL_VERSION}.",
                        )
                        logger(
                            "Join rejected for '${message.playerName}' from ${socket.remoteSocketAddress}: " +
                                "protocol=${message.protocolVersion} expected=${Protocol.PROTOCOL_VERSION}",
                        )
                        return
                    }

                    val accepted = JoinAccepted(
                        playerEntityId = nextEntityId.getAndIncrement(),
                        mapId = mapIdProvider(),
                        serverTick = serverTickProvider(),
                    )
                    writer.writeLine(ProtocolCodec.encodeServer(accepted))
                    logger("Join accepted for '${message.playerName}' entity=${accepted.playerEntityId}")
                    val initialSnapshot = initialSnapshotProvider?.invoke(accepted.playerEntityId)
                    initialSnapshot?.let { snapshot ->
                        writer.writeLine(ProtocolCodec.encodeServer(snapshot))
                        logger(
                            "Initial snapshot sent to '${message.playerName}' " +
                                "entity=${accepted.playerEntityId} entities=${snapshot.entities.size}",
                        )
                    }

                    val session = ClientSession(accepted.playerEntityId, socket, writer)
                    sessions += session

                    // Existing clients need to learn about the newly spawned entity immediately.
                    // The joining client already received the initial full snapshot above.
                    initialSnapshot?.let { snapshot ->
                        sessions.filter { it.entityId != accepted.playerEntityId }.forEach { existing ->
                            existing.send(snapshot)
                        }
                    }

                    while (running.get()) {
                        val clientPayload = reader.readLine() ?: break
                        when (val clientMessage = ProtocolCodec.decodeClient(clientPayload)) {
                            is PingRequest -> session.send(
                                    PongResponse(
                                        pingSequence = clientMessage.pingSequence,
                                        clientTimeMillis = clientMessage.clientTimeMillis,
                                        serverTimeMillis = System.currentTimeMillis(),
                                    ),
                                )
                            is InputCommand -> {
                                inputCommandHandler?.invoke(accepted.playerEntityId, clientMessage)?.let { snapshot ->
                                    // The acknowledgement belongs only to this session; remote clients must not
                                    // discard input based on another player's sequence.
                                    session.send(snapshot)
                                    broadcastSnapshot(
                                        snapshot.copy(
                                            acknowledgedInputSequence = WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE,
                                        ),
                                        excludedEntityId = accepted.playerEntityId,
                                    )
                                }
                            }
                            else -> {
                                // JoinRequest is only valid as the first message in this line-delimited session.
                            }
                        }
                    }
                    logger("TCP connection closed from ${socket.remoteSocketAddress}")
                }
            }
        } catch (_: SocketException) {
            if (running.get()) {
                logger("TCP connection closed from ${socket.remoteSocketAddress}")
            }
        } catch (exception: Exception) {
            if (running.get()) {
                logger("TCP client error from ${socket.remoteSocketAddress}: ${exception.logName()}")
            }
        } finally {
            sessions.firstOrNull { it.socket == socket }?.let { session ->
                sessions -= session
                disconnectSnapshotProvider?.invoke(session.entityId)?.let(::broadcastSnapshot)
            }
            clients -= socket
            socket.closeQuietly()
        }
    }

    private fun sendRejected(writer: BufferedWriter, reason: String) {
        writer.writeLine(ProtocolCodec.encodeServer(JoinRejected(reason = reason)))
    }

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
        flush()
    }

    private fun Socket?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
        }
    }

    private fun ServerSocket?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
        }
    }

    private fun Socket.newReader(): BufferedReader =
        BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))

    private fun Socket.newWriter(): BufferedWriter =
        BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))

    private fun Exception.logName(): String =
        this::class.java.simpleName

    private class ClientSession(
        val entityId: Int,
        val socket: Socket,
        private val writer: BufferedWriter,
    ) {
        @Synchronized
        fun send(message: game.shared.protocol.ServerMessage) {
            writer.write(ProtocolCodec.encodeServer(message))
            writer.newLine()
            writer.flush()
        }
    }

    private companion object {
        const val FIRST_PLAYER_ENTITY_ID = 1
        const val JOIN_TIMEOUT_MILLIS = 500L
    }
}
