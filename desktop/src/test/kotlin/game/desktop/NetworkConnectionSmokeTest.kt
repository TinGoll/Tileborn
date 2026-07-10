package game.desktop

import game.client.debug.ConnectionState
import game.client.network.TcpGameClient
import game.server.network.TcpGameServer
import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRejected
import game.shared.protocol.JoinRequest
import java.net.Socket
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
    fun `server keeps running after client disconnect`() {
        runningServer().use { server ->
            Socket("127.0.0.1", server.localPort).use { socket ->
                socket.close()
            }

            assertTrue(waitUntil { server.isRunning })
            assertEquals(true, server.isRunning)
        }
    }

    private fun runningServer(): TcpGameServer =
        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 42L },
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

    private companion object {
        const val WAIT_TIMEOUT_NANOS = 2_000_000_000L
        const val WAIT_SLEEP_MILLIS = 10L
    }
}
