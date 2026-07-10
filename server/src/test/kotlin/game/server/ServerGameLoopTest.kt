package game.server

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerGameLoopTest {
    @Test
    fun `fixed timestep calculation uses 20 ticks per second`() {
        val loop = ServerGameLoop(logger = {})

        assertEquals(50_000_000L, loop.fixedTimeStepNanos)
        assertEquals(0, loop.calculateTicksToRun(49_999_999L))
        assertEquals(1, loop.calculateTicksToRun(50_000_000L))
        assertEquals(2, loop.calculateTicksToRun(125_000_000L))
    }

    @Test
    fun `server tick increases after update`() {
        val loop = ServerGameLoop(logger = {})
        var updates = 0

        loop.tick {
            assertEquals(0.05f, it, 0f)
            updates += 1
        }

        assertEquals(1L, loop.serverTick)
        assertEquals(1, updates)
    }
}
