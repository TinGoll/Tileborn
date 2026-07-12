package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
import game.shared.protocol.InputCommand
import game.shared.protocol.InteractCommand
import game.shared.protocol.GameEvent
import game.shared.protocol.NetworkDefaults
import game.shared.protocol.PongResponse
import game.shared.protocol.ProtocolCodec
import game.shared.protocol.ServerMessage
import game.shared.protocol.WorldSnapshot
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Line-delimited JSON TCP client for the first server handshake. */
class TcpGameClient(
    private val host: String = NetworkDefaults.HOST,
    private val port: Int = NetworkDefaults.PORT,
    private val playerName: String = NetworkDefaults.DEFAULT_PLAYER_NAME,
    private val joinRequestFactory: () -> JoinRequest = { JoinRequest(playerName = playerName) },
    private val pingIntervalMillis: Long = DEFAULT_PING_INTERVAL_MILLIS,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val logger: (String) -> Unit = { message -> println(message) },
) : GameNetworkClient {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val reconnectRequested = AtomicBoolean(false)
    private val applicationPaused = AtomicBoolean(false)
    private val connectionAttemptRunning = AtomicBoolean(false)
    private val lastReconnectAttemptMillis = AtomicLong(Long.MIN_VALUE)
    private var socket: Socket? = null
    private var thread: Thread? = null
    private val outgoingInput = ConcurrentLinkedQueue<InputCommand>()
    private val outgoingInteractions = ConcurrentLinkedQueue<InteractCommand>()
    private val receivedSnapshots = ConcurrentLinkedQueue<WorldSnapshot>()
    private val receivedGameEvents = ConcurrentLinkedQueue<GameEvent>()

    @Volatile
    override var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    @Volatile
    override var lastServerMessage: ServerMessage? = null
        private set

    @Volatile
    override var localPlayerEntityId: Int? = null
        private set

    @Volatile
    var sessionToken: String? = null
        private set

    @Volatile
    override var pingMillis: Long? = null
        private set

    override fun connect() {
        if (!started.compareAndSet(false, true)) {
            logger("TCP client connect ignored: already started")
            return
        }
        closed.set(false)
        applicationPaused.set(false)
        reconnectRequested.set(false)
        lastServerMessage = null
        localPlayerEntityId = null
        pingMillis = null
        outgoingInput.clear()
        outgoingInteractions.clear()
        receivedSnapshots.clear()
        receivedGameEvents.clear()
        connectionState = if (sessionToken == null) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
        logger("TCP client connecting to $host:$port")
        thread = Thread(::runClient, "tcp-game-client").apply {
            isDaemon = true
            start()
        }
    }

    override fun sendInput(command: InputCommand) {
        if (connectionState == ConnectionState.CONNECTED && !closed.get()) {
            outgoingInput += command
        }
    }

    override fun sendInteract(command: InteractCommand) {
        if (connectionState == ConnectionState.CONNECTED && !closed.get()) {
            outgoingInteractions += command
        }
    }

    override fun drainWorldSnapshots(): List<WorldSnapshot> = buildList {
        while (true) add(receivedSnapshots.poll() ?: break)
    }

    override fun drainGameEvents(): List<GameEvent> = buildList {
        while (true) add(receivedGameEvents.poll() ?: break)
    }

    override fun close() {
        logger("TCP client close requested")
        closed.set(true)
        reconnectRequested.set(false)
        socket.closeQuietly()
        socket = null
        thread?.join(JOIN_TIMEOUT_MILLIS)
        thread = null
        started.set(false)
        if (connectionState != ConnectionState.REJECTED) {
            connectionState = ConnectionState.DISCONNECTED
        }
    }

    override fun onApplicationPaused() {
        if (!closed.get() && started.get()) {
            applicationPaused.set(true)
            reconnectRequested.set(true)
            socket.closeQuietly()
            connectionState = ConnectionState.RECONNECTING
        }
    }

    override fun onApplicationResumed() {
        if (!closed.get() && started.get()) {
            applicationPaused.set(false)
            reconnectRequested.set(true)
            ensureReconnectScheduled()
        }
    }

    private fun runClient() {
        if (!connectionAttemptRunning.compareAndSet(false, true)) return
        val clientSocket = Socket()
        try {
            clientSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            // Input commands and snapshots are small, latency-sensitive messages. Do not wait for
            // TCP to coalesce them into a larger packet.
            clientSocket.tcpNoDelay = true
            socket = clientSocket
            logger("Connected to server $host:$port")

            clientSocket.newWriter().use { writer ->
                clientSocket.newReader().use { reader ->
                    val initialJoin = joinRequestFactory()
                    writer.writeLine(
                        ProtocolCodec.encodeClient(initialJoin.copy(sessionToken = sessionToken)),
                    )

                    val responsePayload = reader.readLine()
                    if (responsePayload == null) {
                        connectionState = ConnectionState.DISCONNECTED
                        logger("Connection closed before join response from $host:$port")
                        return
                    }

                    val response = ProtocolCodec.decodeServer(responsePayload)
                    lastServerMessage = response
                    when (response) {
                        is JoinAccepted -> {
                            localPlayerEntityId = response.playerEntityId
                            sessionToken = response.sessionToken
                            reconnectRequested.set(false)
                            clientSocket.soTimeout = READ_POLL_TIMEOUT_MILLIS
                            connectionState = ConnectionState.CONNECTED
                            logger(
                                "Join accepted entity=${response.playerEntityId} " +
                                    "map=${response.mapId} tick=${response.serverTick}",
                            )
                            runConnectedLoop(reader, writer)
                        }
                        is JoinRejected -> {
                            connectionState = ConnectionState.REJECTED
                            logger("Join rejected: ${response.reason}")
                        }
                        else -> {
                            connectionState = ConnectionState.DISCONNECTED
                            logger("Unexpected join response: ${response.type}")
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            if (!closed.get()) {
                connectionState = ConnectionState.RECONNECTING
                logger("TCP client error for $host:$port: ${exception.logName()}")
            }
        } finally {
            clientSocket.closeQuietly()
            val isCurrentSocket = socket === clientSocket
            if (isCurrentSocket) socket = null
            connectionAttemptRunning.set(false)
            if (isCurrentSocket && !closed.get() && connectionState != ConnectionState.REJECTED) {
                connectionState = ConnectionState.RECONNECTING
                logger("Disconnected from server $host:$port; reconnecting")
                lastReconnectAttemptMillis.set(Long.MIN_VALUE)
                ensureReconnectScheduled()
            }
        }
    }

    private fun runConnectedLoop(reader: BufferedReader, writer: BufferedWriter) {
        val pingTracker = PingTracker(intervalMillis = pingIntervalMillis)
        pingTracker.delayNextPing(clockMillis())
        while (!closed.get()) {
            drainOutgoingInput(writer)
            drainOutgoingInteractions(writer)

            pingTracker.nextPing(clockMillis())?.let { ping ->
                writer.writeLine(ProtocolCodec.encodeClient(ping))
            }

            val payload: String? = try {
                reader.readLine() ?: run {
                    logger("TCP server closed connection $host:$port")
                    return
                }
            } catch (_: SocketTimeoutException) {
                null
            }

            if (payload == null) continue

            val message = ProtocolCodec.decodeServer(payload)
            lastServerMessage = message
            if (message is WorldSnapshot) receivedSnapshots += message
            if (message is GameEvent) receivedGameEvents += message
            if (message is PongResponse) {
                pingTracker.recordPong(message, clockMillis())?.let { roundTripMillis ->
                    pingMillis = roundTripMillis
                }
            }
        }
    }

    private fun drainOutgoingInput(writer: BufferedWriter) {
        while (true) {
            val command = outgoingInput.poll() ?: return
            writer.writeLine(ProtocolCodec.encodeClient(command))
        }
    }

    private fun drainOutgoingInteractions(writer: BufferedWriter) {
        while (true) {
            val command = outgoingInteractions.poll() ?: return
            writer.writeLine(ProtocolCodec.encodeClient(command))
        }
    }

    private fun ensureReconnectScheduled() {
        if (closed.get() || applicationPaused.get() || !started.get() || connectionState == ConnectionState.REJECTED) return
        val now = clockMillis()
        val previous = lastReconnectAttemptMillis.get()
        if (
            (previous != Long.MIN_VALUE && now - previous < RECONNECT_DELAY_MILLIS) ||
            !lastReconnectAttemptMillis.compareAndSet(previous, now)
        ) return
        reconnectRequested.set(true)
        Thread({
            try {
                Thread.sleep(RECONNECT_DELAY_MILLIS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            if (!closed.get() && !applicationPaused.get() && started.get() && reconnectRequested.get()) {
                runClient()
            }
        }, "tcp-game-client-reconnect").apply {
            isDaemon = true
            start()
        }
    }

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
        flush()
    }

    private fun Socket.newReader(): BufferedReader =
        BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))

    private fun Socket.newWriter(): BufferedWriter =
        BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))

    private fun Socket?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
        }
    }

    private fun Exception.logName(): String =
        this::class.java.simpleName

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 1_000
        const val JOIN_TIMEOUT_MILLIS = 500L
        // The network thread sends queued input before each read. Keep this short so outgoing
        // intent is never held behind an idle socket read for a visible amount of time.
        const val READ_POLL_TIMEOUT_MILLIS = 10
        const val DEFAULT_PING_INTERVAL_MILLIS = 1_000L
        const val RECONNECT_DELAY_MILLIS = 250L
    }
}
