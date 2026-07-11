package game.server.network

import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
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
    private val logger: (String) -> Unit = ::println,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val nextEntityId = AtomicInteger(FIRST_PLAYER_ENTITY_ID)
    private val clients = CopyOnWriteArrayList<Socket>()
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
        serverSocket.closeQuietly()
        serverSocket = null
        acceptThread?.join(JOIN_TIMEOUT_MILLIS)
        acceptThread = null
        logger("TCP server stopped")
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
                    initialSnapshotProvider?.invoke(accepted.playerEntityId)?.let { snapshot ->
                        writer.writeLine(ProtocolCodec.encodeServer(snapshot))
                        logger(
                            "Initial snapshot sent to '${message.playerName}' " +
                                "entity=${accepted.playerEntityId} entities=${snapshot.entities.size}",
                        )
                    }

                    while (running.get()) {
                        val clientPayload = reader.readLine() ?: break
                        when (val clientMessage = ProtocolCodec.decodeClient(clientPayload)) {
                            is PingRequest -> writer.writeLine(
                                ProtocolCodec.encodeServer(
                                    PongResponse(
                                        pingSequence = clientMessage.pingSequence,
                                        clientTimeMillis = clientMessage.clientTimeMillis,
                                        serverTimeMillis = System.currentTimeMillis(),
                                    ),
                                ),
                            )
                            else -> {
                                // Gameplay synchronization is intentionally out of scope for this iteration.
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

    private companion object {
        const val FIRST_PLAYER_ENTITY_ID = 1
        const val JOIN_TIMEOUT_MILLIS = 500L
    }
}
