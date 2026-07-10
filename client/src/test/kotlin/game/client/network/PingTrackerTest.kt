package game.client.network

import game.shared.protocol.PongResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingTrackerTest {
    @Test
    fun `creates ping only when interval elapses`() {
        val tracker = PingTracker(intervalMillis = 1_000L)

        val firstPing = tracker.nextPing(nowMillis = 10_000L)
        val earlyPing = tracker.nextPing(nowMillis = 10_500L)
        val nextPing = tracker.nextPing(nowMillis = 11_000L)

        assertEquals(1L, firstPing?.pingSequence)
        assertNull(earlyPing)
        assertEquals(2L, nextPing?.pingSequence)
    }

    @Test
    fun `records round trip time from matching pong`() {
        val tracker = PingTracker(intervalMillis = 1_000L)
        val ping = tracker.nextPing(nowMillis = 25_000L)!!

        val roundTripMillis = tracker.recordPong(
            pong = PongResponse(
                pingSequence = ping.pingSequence,
                clientTimeMillis = ping.clientTimeMillis,
                serverTimeMillis = 25_030L,
            ),
            nowMillis = 25_075L,
        )

        assertEquals(75L, roundTripMillis)
        assertEquals(75L, tracker.lastRoundTripMillis)
    }

    @Test
    fun `ignores pong without matching pending ping`() {
        val tracker = PingTracker(intervalMillis = 1_000L)

        val roundTripMillis = tracker.recordPong(
            pong = PongResponse(
                pingSequence = 99L,
                clientTimeMillis = 1_000L,
                serverTimeMillis = 1_010L,
            ),
            nowMillis = 1_020L,
        )

        assertNull(roundTripMillis)
        assertNull(tracker.lastRoundTripMillis)
    }
}
