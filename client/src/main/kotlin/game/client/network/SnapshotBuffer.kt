package game.client.network

import game.shared.protocol.EntitySnapshot
import kotlin.math.sqrt

/** Keeps a small, ordered history of one remote entity's authoritative snapshots. */
class SnapshotBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val snapshots = ArrayDeque<TimedSnapshot>()

    init {
        require(capacity >= 2) { "Snapshot buffer capacity must be at least two." }
    }

    fun add(serverTick: Long, snapshot: EntitySnapshot) {
        val latest = snapshots.lastOrNull()
        if (latest != null && serverTick < latest.serverTick) return
        if (latest != null && serverTick == latest.serverTick) {
            snapshots.removeLast()
        }
        snapshots.addLast(TimedSnapshot(serverTick, snapshot))
        while (snapshots.size > capacity) snapshots.removeFirst()
    }

    /** Returns the best safe render state for [renderServerTick], including a one-snapshot fallback. */
    fun sample(
        renderServerTick: Float,
        maxInterpolationDistance: Float = DEFAULT_MAX_INTERPOLATION_DISTANCE,
    ): EntitySnapshot? {
        val first = snapshots.firstOrNull() ?: return null
        if (snapshots.size == 1 || renderServerTick <= first.serverTick) return first.snapshot

        val last = snapshots.last()
        if (renderServerTick >= last.serverTick) return last.snapshot

        var previous = first
        for (next in snapshots.drop(1)) {
            if (renderServerTick <= next.serverTick) {
                if (distance(previous.snapshot, next.snapshot) > maxInterpolationDistance) return next.snapshot
                val progress = ((renderServerTick - previous.serverTick) /
                    (next.serverTick - previous.serverTick)).coerceIn(0f, 1f)
                return interpolate(previous.snapshot, next.snapshot, progress)
            }
            previous = next
        }
        return last.snapshot
    }

    private fun interpolate(first: EntitySnapshot, second: EntitySnapshot, progress: Float): EntitySnapshot =
        EntitySnapshot(
            entityId = second.entityId,
            x = lerp(first.x, second.x, progress),
            y = lerp(first.y, second.y, progress),
            velocityX = lerp(first.velocityX, second.velocityX, progress),
            velocityY = lerp(first.velocityY, second.velocityY, progress),
            currentHealth = second.currentHealth,
            maxHealth = second.maxHealth,
            movementSpeed = second.movementSpeed,
            characterState = second.characterState,
        )

    private fun distance(first: EntitySnapshot, second: EntitySnapshot): Float =
        sqrt((second.x - first.x) * (second.x - first.x) + (second.y - first.y) * (second.y - first.y))

    private fun lerp(first: Float, second: Float, progress: Float): Float = first + (second - first) * progress

    private data class TimedSnapshot(val serverTick: Long, val snapshot: EntitySnapshot)

    companion object {
        const val DEFAULT_CAPACITY = 20
        /** Distances above this are treated as teleports and rendered as a safe snap. */
        const val DEFAULT_MAX_INTERPOLATION_DISTANCE = 20f
    }
}
