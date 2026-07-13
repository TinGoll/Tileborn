package game.server.network

import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
import game.shared.protocol.InputCommand
import game.shared.protocol.InteractCommand
import game.shared.protocol.GameEvent
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
import java.util.UUID

/** Line-delimited JSON TCP transport for the first client/server handshake. */
class TcpGameServer(
    private val port: Int,
    private val mapIdProvider: () -> String,
    private val serverTickProvider: () -> Long,
    private val initialSnapshotProvider: ((Int) -> WorldSnapshot)? = null,
    private val inputCommandHandler: ((Int, InputCommand) -> WorldSnapshot?)? = null,
    private val interactCommandHandler: ((Int, InteractCommand) -> GameEvent?)? = null,
    private val disconnectSnapshotProvider: ((Int) -> WorldSnapshot?)? = null,
    private val reconnectSnapshotProvider: ((Int) -> WorldSnapshot?)? = null,
    private val snapshotForRecipient: (Int, WorldSnapshot) -> WorldSnapshot = { _, snapshot -> snapshot },
    private val sessionEstablishedHandler: ((EstablishedSession) -> Unit)? = null,
    private val sessionDisconnectedHandler: ((Int) -> Unit)? = null,
    private val sessionTimeoutMillis: Long = SESSION_TIMEOUT_MILLIS,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val logger: (String) -> Unit = ::println,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val nextEntityId = AtomicInteger(FIRST_PLAYER_ENTITY_ID)
    private val clients = CopyOnWriteArrayList<Socket>()
    private val sessions = CopyOnWriteArrayList<ClientSession>()
    private val retainedSessionsByToken = mutableMapOf<String, RetainedSession>()
    private val sessionLock = Any()
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

    /** Sends a recipient-filtered authoritative snapshot to every connected player. */
    fun broadcastSnapshot(snapshot: WorldSnapshot, excludedEntityId: Int? = null) {
        expireDisconnectedSessions()
        sessions.filter { it.entityId != excludedEntityId }.forEach { session ->
            try {
                session.send(snapshotForRecipient(session.entityId, snapshot))
            } catch (_: Exception) {
                session.socket.closeQuietly()
            }
        }
    }

    /** Removes entities whose disconnected reconnect grace period has elapsed. */
    fun expireDisconnectedSessions() {
        val expiredEntityIds = synchronized(sessionLock) {
            val now = clockMillis()
            retainedSessionsByToken.values
                .filter { it.disconnectedAtMillis?.let { disconnectedAt -> now - disconnectedAt >= sessionTimeoutMillis } == true }
                .onEach { retainedSessionsByToken.remove(it.token) }
                .map { it.entityId }
        }
        expiredEntityIds.forEach { entityId ->
            logger("Session expired entity=$entityId")
            disconnectSnapshotProvider?.invoke(entityId)?.let(::broadcastSnapshot)
        }
    }

    private fun acceptLoop(serverSocket: ServerSocket) {
        while (running.get()) {
            try {
                val client = serverSocket.accept()
                // The protocol sends small, real-time input and snapshot messages. Disabling
                // Nagle avoids avoidable coalescing delays for these writes.
                client.tcpNoDelay = true
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

                    expireDisconnectedSessions()

                    val resumedSession = resumeOrCreateSession(message)
                    val retainedSession = resumedSession.session
                    resumedSession.supersededConnection?.socket.closeQuietly()
                    resumedSession.supersededEntityId?.let { entityId ->
                        logger("Session replaced for '${message.playerName}' oldEntity=$entityId")
                        // The current account identity is the player name. A new login without a
                        // valid reconnect token must not leave its old authoritative entity in the
                        // world during the reconnect grace period.
                        sessionDisconnectedHandler?.invoke(entityId)
                        disconnectSnapshotProvider?.invoke(entityId)?.let(::broadcastSnapshot)
                    }
                    val isReconnect = retainedSession.wasDisconnected
                    sessionEstablishedHandler?.invoke(
                        EstablishedSession(
                            entityId = retainedSession.entityId,
                            sessionToken = retainedSession.token,
                            playerName = message.playerName,
                            isReconnect = isReconnect,
                        ),
                    )
                    val accepted = JoinAccepted(
                        playerEntityId = retainedSession.entityId,
                        mapId = mapIdProvider(),
                        serverTick = serverTickProvider(),
                        sessionToken = retainedSession.token,
                    )
                    writer.writeLine(ProtocolCodec.encodeServer(accepted))
                    logger("${if (isReconnect) "Reconnect" else "Join"} accepted for '${message.playerName}' entity=${accepted.playerEntityId}")
                    val initialSnapshot = if (isReconnect) {
                        reconnectSnapshotProvider?.invoke(accepted.playerEntityId)
                    } else {
                        initialSnapshotProvider?.invoke(accepted.playerEntityId)
                    }
                    initialSnapshot?.let { snapshot ->
                        val recipientSnapshot = snapshotForRecipient(accepted.playerEntityId, snapshot)
                        writer.writeLine(ProtocolCodec.encodeServer(recipientSnapshot))
                        logger(
                            "Initial snapshot sent to '${message.playerName}' " +
                                "entity=${accepted.playerEntityId} entities=${recipientSnapshot.entities.size}",
                        )
                    }

                    val session = ClientSession(accepted.playerEntityId, retainedSession.token, socket, writer)
                    sessions += session
                    synchronized(sessionLock) {
                        retainedSession.activeConnection
                            ?.takeIf { it.socket != socket }
                            ?.socket
                            .closeQuietly()
                        retainedSession.activeConnection = session
                    }

                    // Existing clients need to learn about the newly spawned entity immediately.
                    // The joining client already received the initial full snapshot above.
                    initialSnapshot?.let { snapshot ->
                        sessions.filter { it.entityId != accepted.playerEntityId }.forEach { existing ->
                            existing.send(snapshotForRecipient(existing.entityId, snapshot))
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
                                    session.send(snapshotForRecipient(session.entityId, snapshot))
                                    broadcastSnapshot(
                                        snapshot.copy(
                                            acknowledgedInputSequence = WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE,
                                        ),
                                        excludedEntityId = accepted.playerEntityId,
                                    )
                                }
                            }
                            is InteractCommand -> {
                                interactCommandHandler?.invoke(accepted.playerEntityId, clientMessage)?.let(session::send)
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
                if (markDisconnected(session)) sessionDisconnectedHandler?.invoke(session.entityId)
            }
            clients -= socket
            socket.closeQuietly()
        }
    }

    private fun resumeOrCreateSession(request: JoinRequest): ResumedSession = synchronized(sessionLock) {
        val requested = request.sessionToken?.let(retainedSessionsByToken::get)
        val now = clockMillis()
        if (
            requested != null &&
            (requested.disconnectedAtMillis == null || now - requested.disconnectedAtMillis!! < sessionTimeoutMillis)
        ) {
            requested.wasDisconnected = requested.activeConnection != null || requested.disconnectedAtMillis != null
            requested.disconnectedAtMillis = null
            return@synchronized ResumedSession(session = requested)
        }
        val replaced = retainedSessionsByToken.values.firstOrNull { session ->
            session.playerName == request.playerName
        }
        replaced?.let { retainedSessionsByToken.remove(it.token) }
        val newSession = RetainedSession(
            token = UUID.randomUUID().toString(),
            entityId = nextEntityId.getAndIncrement(),
            playerName = request.playerName,
        ).also { retainedSessionsByToken[it.token] = it }
        ResumedSession(
            session = newSession,
            supersededEntityId = replaced?.entityId,
            supersededConnection = replaced?.activeConnection,
        )
    }

    private fun markDisconnected(session: ClientSession): Boolean =
        synchronized(sessionLock) {
            val retained = retainedSessionsByToken[session.sessionToken]
            if (retained?.activeConnection === session) {
                retained.activeConnection = null
                retained.disconnectedAtMillis = clockMillis()
                logger("Session retained entity=${session.entityId} timeout=${sessionTimeoutMillis}ms")
                true
            } else {
                false
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
        val sessionToken: String,
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

    private class RetainedSession(
        val token: String,
        val entityId: Int,
        val playerName: String,
        var disconnectedAtMillis: Long? = null,
        var activeConnection: ClientSession? = null,
        var wasDisconnected: Boolean = false,
    )

    private data class ResumedSession(
        val session: RetainedSession,
        val supersededEntityId: Int? = null,
        val supersededConnection: ClientSession? = null,
    )

    private companion object {
        const val FIRST_PLAYER_ENTITY_ID = 1
        const val JOIN_TIMEOUT_MILLIS = 500L
        const val SESSION_TIMEOUT_MILLIS = 10_000L
    }
}

/** Network session metadata supplied to server-only lifecycle integrations. */
data class EstablishedSession(
    val entityId: Int,
    val sessionToken: String,
    val playerName: String,
    val isReconnect: Boolean,
)
