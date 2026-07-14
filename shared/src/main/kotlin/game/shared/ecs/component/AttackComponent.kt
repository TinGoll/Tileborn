package game.shared.ecs.component

import com.badlogic.ashley.core.Component
import java.util.ArrayDeque

/** Immutable attack tuning plus queued, untrusted intent awaiting server validation. */
class AttackComponent(
    val range: Float,
    val damage: Float,
    val minimumDirectionDot: Float,
    var lastReceivedInputSequence: Long = NO_INPUT_SEQUENCE,
    val pendingAttacks: ArrayDeque<PendingAttack> = ArrayDeque(),
) : Component {
    companion object {
        const val NO_INPUT_SEQUENCE: Long = -1L
    }
}

/** Data-only copy of an attack request; protocol DTOs do not leak into gameplay systems. */
data class PendingAttack(
    val inputSequence: Long,
    val clientTick: Long,
    val aimX: Float,
    val aimY: Float,
    val optionalTargetEntityId: Int?,
)
