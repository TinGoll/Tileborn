package game.server

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConsoleTest {
    @Test
    fun `stop command requests shutdown`() {
        var stopRequests = 0
        val console = ServerConsole(
            input = ByteArrayInputStream("stop\n".toByteArray()),
            onStopRequested = { stopRequests += 1 },
            logger = {},
        )

        console.start().join(1_000L)

        assertEquals(1, stopRequests)
    }

    @Test
    fun `quit command requests shutdown after ignored input`() {
        var stopRequests = 0
        val console = ServerConsole(
            input = ByteArrayInputStream("status\nquit\n".toByteArray()),
            onStopRequested = { stopRequests += 1 },
            logger = {},
        )

        console.start().join(1_000L)

        assertEquals(1, stopRequests)
    }
}
