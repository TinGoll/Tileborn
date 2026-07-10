package game.client.network

import game.shared.protocol.PingRequest
import game.shared.protocol.PongResponse

/** Tracks periodic ping requests and calculates round-trip time from echoed pong data. */
class PingTracker(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val maxPendingMillis: Long = DEFAULT_MAX_PENDING_MILLIS,
) {
    private val pendingPings = linkedMapOf<Long, Long>()
    private var nextSequence = 1L
    private var nextPingAtMillis = 0L

    var lastRoundTripMillis: Long? = null
        private set

    fun delayNextPing(nowMillis: Long) {
        nextPingAtMillis = nowMillis + intervalMillis
    }

    fun nextPing(nowMillis: Long): PingRequest? {
        if (nowMillis < nextPingAtMillis) return null

        pruneExpired(nowMillis)
        val sequence = nextSequence++
        pendingPings[sequence] = nowMillis
        nextPingAtMillis = nowMillis + intervalMillis
        return PingRequest(
            pingSequence = sequence,
            clientTimeMillis = nowMillis,
        )
    }

    fun recordPong(pong: PongResponse, nowMillis: Long): Long? {
        val sentAtMillis = pendingPings.remove(pong.pingSequence)
            ?: return null
        val roundTripMillis = (nowMillis - sentAtMillis).coerceAtLeast(0L)
        lastRoundTripMillis = roundTripMillis
        return roundTripMillis
    }

    private fun pruneExpired(nowMillis: Long) {
        val iterator = pendingPings.iterator()
        while (iterator.hasNext()) {
            val (_, sentAtMillis) = iterator.next()
            if (nowMillis - sentAtMillis > maxPendingMillis) {
                iterator.remove()
            }
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_MAX_PENDING_MILLIS = 10_000L
    }
}
