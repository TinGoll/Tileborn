package game.desktop

import game.client.debug.ConnectionState
import game.client.network.TcpGameClient
import game.server.network.TcpGameServer
import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
import game.shared.protocol.PingRequest
import game.shared.protocol.PongResponse
import game.shared.protocol.ProtocolCodec
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.InputCommand
import game.shared.protocol.WorldSnapshot
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConnectionSmokeTest {
    @Test
    fun `client receives JoinAccepted from local server`() {
        runningServer().use { server ->
            val client = TcpGameClient(port = server.localPort)

            client.connect()

            assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
            assertTrue(client.lastServerMessage is JoinAccepted)
            client.close()
        }
    }

    @Test
    fun `client waits for join response longer than ping poll timeout`() {
        runningServer(serverTickProvider = {
            Thread.sleep(200L)
            42L
        }).use { server ->
            val client = TcpGameClient(port = server.localPort)

            client.connect()

            assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
            assertTrue(client.lastServerMessage is JoinAccepted)
            client.close()
        }
    }

    @Test
    fun `invalid protocol version receives JoinRejected`() {
        runningServer().use { server ->
            val client = TcpGameClient(
                port = server.localPort,
                joinRequestFactory = { JoinRequest(playerName = "old-client", protocolVersion = -1) },
            )

            client.connect()

            assertTrue(waitUntil { client.connectionState == ConnectionState.REJECTED })
            val rejected = client.lastServerMessage as JoinRejected
            assertTrue(rejected.reason.contains("Unsupported protocol version"))
            client.close()
        }
    }

    @Test
    fun `client receives initial WorldSnapshot from local server`() {
        runningServer(
            initialSnapshotProvider = { playerEntityId ->
                WorldSnapshot(
                    serverTick = 42L,
                    entities = listOf(
                        EntitySnapshot(
                            entityId = playerEntityId,
                            x = 5f,
                            y = 6f,
                            velocityX = 0f,
                            velocityY = 0f,
                        ),
                    ),
                )
            },
        ).use { server ->
            val client = TcpGameClient(port = server.localPort)

            client.connect()

            assertTrue(waitUntil { client.lastServerMessage is WorldSnapshot })
            val snapshot = client.lastServerMessage as WorldSnapshot
            assertEquals(client.localPlayerEntityId, snapshot.entities.single().entityId)
            assertEquals(5f, snapshot.entities.single().x, 0f)
            assertEquals(6f, snapshot.entities.single().y, 0f)
            client.close()
        }
    }

    @Test
    fun `server keeps running after client disconnect`() {
        runningServer().use { server ->
            Socket("127.0.0.1", server.localPort).use { socket ->
                socket.close()
            }

            assertTrue(waitUntil { server.isRunning })
            assertEquals(true, server.isRunning)
        }
    }

    @Test
    fun `client records ping from server pong`() {
        runningServer().use { server ->
            val client = TcpGameClient(
                port = server.localPort,
                pingIntervalMillis = 25L,
            )

            client.connect()

            assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
            assertTrue(waitUntil { client.pingMillis != null })
            assertTrue(client.lastServerMessage is PongResponse)
            client.close()
        }
    }

    @Test
    fun `two clients receive each other movement while disconnected session is retained`() {
        val entities = linkedMapOf<Int, Float>()
        val lock = Any()
        fun snapshot(): WorldSnapshot = synchronized(lock) {
            WorldSnapshot(
                serverTick = 42L,
                entities = entities.map { (entityId, x) ->
                    EntitySnapshot(entityId = entityId, x = x, y = 6f, velocityX = 0f, velocityY = 0f)
                },
            )
        }

        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 42L },
            initialSnapshotProvider = { playerEntityId ->
                synchronized(lock) { entities[playerEntityId] = 5f }
                snapshot()
            },
            inputCommandHandler = { playerEntityId, command ->
                synchronized(lock) { entities[playerEntityId] = (entities[playerEntityId] ?: 0f) + command.moveX }
                snapshot()
            },
            disconnectSnapshotProvider = { playerEntityId ->
                synchronized(lock) { entities.remove(playerEntityId) }
                snapshot()
            },
            logger = {},
        ).use { server ->
            server.start()
            val first = TcpGameClient(port = server.localPort, playerName = "first-player", pingIntervalMillis = 60_000L)
            val second = TcpGameClient(port = server.localPort, playerName = "second-player", pingIntervalMillis = 60_000L)
            try {
                first.connect()
                assertTrue(waitUntil { first.connectionState == ConnectionState.CONNECTED })
                second.connect()
                assertTrue(waitUntil { second.connectionState == ConnectionState.CONNECTED })

                assertTrue(waitUntil { first.lastServerMessage.hasEntityIds(1, 2) })
                assertTrue(waitUntil { second.lastServerMessage.hasEntityIds(1, 2) })

                first.sendInput(InputCommand(inputSequence = 1L, clientTick = 1L, moveX = 1f, moveY = 0f))
                assertTrue(waitUntil {
                    (second.lastServerMessage as? WorldSnapshot)
                        ?.entities?.firstOrNull { it.entityId == first.localPlayerEntityId }?.x == 6f
                })

                second.close()
                assertTrue(waitUntil { first.lastServerMessage.hasEntityIds(1, 2) })
            } finally {
                first.close()
                second.close()
            }
        }
    }

    @Test
    fun `new login replaces retained entity for the same player`() {
        val entities = linkedMapOf<Int, Float>()
        val lock = Any()
        val disconnected = CountDownLatch(1)
        fun snapshot() = synchronized(lock) {
            WorldSnapshot(
                serverTick = 42L,
                entities = entities.map { (id, x) -> EntitySnapshot(id, x, 0f, 0f, 0f) },
            )
        }

        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 42L },
            initialSnapshotProvider = { id -> synchronized(lock) { entities[id] = 5f }; snapshot() },
            disconnectSnapshotProvider = { id -> synchronized(lock) { entities.remove(id) }; snapshot() },
            sessionDisconnectedHandler = { disconnected.countDown() },
            logger = {},
        ).use { server ->
            server.start()
            val first = TcpGameClient(port = server.localPort, playerName = "same-player", pingIntervalMillis = 60_000L)
            val second = TcpGameClient(port = server.localPort, playerName = "same-player", pingIntervalMillis = 60_000L)
            try {
                first.connect()
                assertTrue(waitUntil { first.connectionState == ConnectionState.CONNECTED })
                val oldEntityId = first.localPlayerEntityId
                first.close()
                assertTrue(disconnected.await(1, TimeUnit.SECONDS))

                second.connect()
                assertTrue(waitUntil { second.connectionState == ConnectionState.CONNECTED })
                assertTrue(waitUntil {
                    (second.lastServerMessage as? WorldSnapshot)?.entities?.let { snapshots ->
                        snapshots.size == 1 && snapshots.single().entityId == second.localPlayerEntityId &&
                            snapshots.none { it.entityId == oldEntityId }
                    } == true
                })
            } finally {
                first.close()
                second.close()
            }
        }
    }

    @Test
    fun `client marks reconnecting when server connection is lost`() {
        val server = runningServer()
        val client = TcpGameClient(port = server.localPort)
        try {
            client.connect()
            assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })

            server.close()

            assertTrue(waitUntil { client.connectionState == ConnectionState.RECONNECTING })
        } finally {
            client.close()
            server.close()
        }
    }

    @Test
    fun `client reconnects with its session token before timeout`() {
        val entities = linkedMapOf<Int, Float>()
        val lock = Any()
        fun snapshot() = synchronized(lock) {
            WorldSnapshot(
                serverTick = 42L,
                entities = entities.map { (id, x) -> EntitySnapshot(id, x, 0f, 0f, 0f) },
            )
        }
        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 42L },
            initialSnapshotProvider = { id -> synchronized(lock) { entities[id] = 5f }; snapshot() },
            reconnectSnapshotProvider = { snapshot() },
            disconnectSnapshotProvider = { id -> synchronized(lock) { entities.remove(id) }; snapshot() },
            logger = {},
        ).use { server ->
            server.start()
            val client = TcpGameClient(port = server.localPort, pingIntervalMillis = 60_000L)
            try {
                client.connect()
                assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
                val entityId = client.localPlayerEntityId
                val token = client.sessionToken

                client.onApplicationPaused()
                assertTrue(waitUntil { client.connectionState == ConnectionState.RECONNECTING })
                client.onApplicationResumed()

                assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
                assertEquals(entityId, client.localPlayerEntityId)
                assertEquals(token, client.sessionToken)
                assertTrue(waitUntil { client.lastServerMessage.hasEntityIds(entityId!!) })
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `expired reconnect session creates a new player session`() {
        val entities = linkedMapOf<Int, Float>()
        val lock = Any()
        fun snapshot() = synchronized(lock) {
            WorldSnapshot(42L, entities.map { (id, x) -> EntitySnapshot(id, x, 0f, 0f, 0f) })
        }
        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 42L },
            initialSnapshotProvider = { id -> synchronized(lock) { entities[id] = id.toFloat() }; snapshot() },
            reconnectSnapshotProvider = { snapshot() },
            disconnectSnapshotProvider = { id -> synchronized(lock) { entities.remove(id) }; snapshot() },
            sessionTimeoutMillis = 50L,
            logger = {},
        ).use { server ->
            server.start()
            val client = TcpGameClient(port = server.localPort, pingIntervalMillis = 60_000L)
            try {
                client.connect()
                assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
                val originalEntityId = client.localPlayerEntityId

                client.onApplicationPaused()
                assertTrue(waitUntil { client.connectionState == ConnectionState.RECONNECTING })
                Thread.sleep(75L)
                server.expireDisconnectedSessions()
                client.onApplicationResumed()

                assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
                assertTrue(client.localPlayerEntityId != originalEntityId)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `server responds to frequent ping without stopping`() {
        runningServer().use { server ->
            Socket("127.0.0.1", server.localPort).use { socket ->
                socket.newWriter().use { writer ->
                    socket.newReader().use { reader ->
                        writer.writeLine(ProtocolCodec.encodeClient(JoinRequest(playerName = "ping-test")))
                        val join = ProtocolCodec.decodeServer(reader.readLine())
                        assertTrue(join is JoinAccepted)

                        repeat(50) { index ->
                            writer.writeLine(
                                ProtocolCodec.encodeClient(
                                    PingRequest(
                                        pingSequence = index.toLong(),
                                        clientTimeMillis = index.toLong(),
                                    ),
                                ),
                            )
                            val pong = ProtocolCodec.decodeServer(reader.readLine())
                            assertTrue(pong is PongResponse)
                            assertEquals(index.toLong(), (pong as PongResponse).pingSequence)
                        }
                    }
                }
            }

            assertTrue(server.isRunning)
        }
    }

    private fun runningServer(
        serverTickProvider: () -> Long = { 42L },
        initialSnapshotProvider: ((Int) -> WorldSnapshot)? = null,
    ): TcpGameServer =
        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = serverTickProvider,
            initialSnapshotProvider = initialSnapshotProvider,
            logger = {},
        ).also { it.start() }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + WAIT_TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(WAIT_SLEEP_MILLIS)
        }
        return condition()
    }

    private fun game.shared.protocol.ServerMessage?.hasEntityIds(vararg expected: Int): Boolean =
        (this as? WorldSnapshot)?.entities?.map { it.entityId }?.toSet() == expected.toSet()

    private fun Socket.newReader(): BufferedReader =
        BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))

    private fun Socket.newWriter(): BufferedWriter =
        BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
        flush()
    }

    private companion object {
        const val WAIT_TIMEOUT_NANOS = 2_000_000_000L
        const val WAIT_SLEEP_MILLIS = 10L
    }
}
