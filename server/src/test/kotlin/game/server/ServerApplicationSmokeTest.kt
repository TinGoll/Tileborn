package game.server

import org.junit.Assert.assertTrue
import org.junit.Test

class ServerApplicationSmokeTest {
    @Test
    fun `server application starts loads map and stops after one tick`() {
        val messages = mutableListOf<String>()
        val application = ServerApplication(networkPort = 0, logger = messages::add)

        application.run(maxTicks = 1)

        assertTrue(messages.any { it.contains("Loaded gameplay definitions mobs=1 items=1") })
        assertTrue(messages.any { it.contains("Loaded gameplay map 'debug_map'") })
        assertTrue(messages.any { it.contains("Spawned map mobs=3") })
        assertTrue(messages.any { it.contains("Server stopped") })
    }
}
