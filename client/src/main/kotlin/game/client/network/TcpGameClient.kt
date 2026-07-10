package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
import game.shared.protocol.NetworkDefaults
import game.shared.protocol.PongResponse
import game.shared.protocol.ProtocolCodec
import game.shared.protocol.ServerMessage
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

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
    private var socket: Socket? = null
    private var thread: Thread? = null

    @Volatile
    override var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    @Volatile
    override var lastServerMessage: ServerMessage? = null
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
        lastServerMessage = null
        pingMillis = null
        connectionState = ConnectionState.CONNECTING
        logger("TCP client connecting to $host:$port")
        thread = Thread(::runClient, "tcp-game-client").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        logger("TCP client close requested")
        closed.set(true)
        socket.closeQuietly()
        socket = null
        thread?.join(JOIN_TIMEOUT_MILLIS)
        thread = null
        started.set(false)
        if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.CONNECTED) {
            connectionState = ConnectionState.DISCONNECTED
        }
    }

    private fun runClient() {
        val clientSocket = Socket()
        try {
            clientSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            socket = clientSocket
            logger("Connected to server $host:$port")

            clientSocket.newWriter().use { writer ->
                clientSocket.newReader().use { reader ->
                    writer.writeLine(ProtocolCodec.encodeClient(joinRequestFactory()))

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
                connectionState = ConnectionState.DISCONNECTED
                logger("TCP client error for $host:$port: ${exception.logName()}")
            }
        } finally {
            clientSocket.closeQuietly()
            socket = null
            if (!closed.get() && connectionState == ConnectionState.CONNECTED) {
                connectionState = ConnectionState.DISCONNECTED
                logger("Disconnected from server $host:$port")
            }
        }
    }

    private fun runConnectedLoop(reader: BufferedReader, writer: BufferedWriter) {
        val pingTracker = PingTracker(intervalMillis = pingIntervalMillis)
        pingTracker.delayNextPing(clockMillis())
        while (!closed.get()) {
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
            if (message is PongResponse) {
                pingTracker.recordPong(message, clockMillis())?.let { roundTripMillis ->
                    pingMillis = roundTripMillis
                }
            }
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
        const val READ_POLL_TIMEOUT_MILLIS = 100
        const val DEFAULT_PING_INTERVAL_MILLIS = 1_000L
    }
}
