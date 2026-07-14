package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import game.shared.ecs.component.AttackComponent
import game.shared.ecs.component.PendingAttack
import game.shared.protocol.AttackCommand

/** Deduplicates untrusted attack commands before they enter authoritative gameplay processing. */
class AttackCommandSystem : EntitySystem(PRIORITY) {
    fun enqueue(entity: Entity, command: AttackCommand): Boolean {
        val attack = ATTACK_MAPPER.get(entity) ?: return false
        if (command.inputSequence < 0L || command.clientTick < 0L) return false
        if (command.inputSequence <= attack.lastReceivedInputSequence) return false

        // Mark the sequence as seen even when the bounded queue is full. A client cannot replay
        // the same command later to bypass the server's processing/cooldown history.
        attack.lastReceivedInputSequence = command.inputSequence
        if (attack.pendingAttacks.size >= MAX_PENDING_ATTACKS) return false
        attack.pendingAttacks.addLast(
            PendingAttack(
                inputSequence = command.inputSequence,
                clientTick = command.clientTick,
                aimX = command.aimX,
                aimY = command.aimY,
                optionalTargetEntityId = command.optionalTargetEntityId,
            ),
        )
        return true
    }

    companion object {
        const val PRIORITY = 100
        private const val MAX_PENDING_ATTACKS = 8
        private val ATTACK_MAPPER = ComponentMapper.getFor(AttackComponent::class.java)
    }
}
