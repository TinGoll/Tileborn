package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.shared.ecs.component.AttackComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.CooldownComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PendingAttack
import game.shared.ecs.component.TransformComponent
import game.shared.protocol.GameEvent
import game.shared.protocol.GameEventType
import java.util.ArrayDeque
import kotlin.math.sqrt

/** Selects melee hits and applies fixed server-owned damage after all command validation. */
class AttackValidationSystem(
    private val healthSystem: HealthSystem,
    private val characterStateSystem: CharacterStateSystem,
) : IteratingSystem(FAMILY, PRIORITY) {
    private val resolvedEvents = ArrayDeque<GameEvent>()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val attack = ATTACK_MAPPER.get(entity)
        while (attack.pendingAttacks.isNotEmpty()) {
            validateAndResolve(entity, attack, attack.pendingAttacks.removeFirst())
        }
    }

    fun drainEvents(): List<GameEvent> = buildList {
        while (resolvedEvents.isNotEmpty()) add(resolvedEvents.removeFirst())
    }

    private fun validateAndResolve(attacker: Entity, attack: AttackComponent, intent: PendingAttack) {
        if (STATE_MAPPER.get(attacker).state != CharacterState.ALIVE) return
        val cooldown = COOLDOWN_MAPPER.get(attacker)
        if (cooldown.remainingSeconds > 0f) return

        val aimLengthSquared = intent.aimX * intent.aimX + intent.aimY * intent.aimY
        if (!aimLengthSquared.isFinite() || aimLengthSquared < MIN_AIM_LENGTH_SQUARED) return
        val inverseAimLength = 1f / sqrt(aimLengthSquared)
        val aimX = intent.aimX * inverseAimLength
        val aimY = intent.aimY * inverseAimLength

        // A valid attack attempt consumes cooldown regardless of whether it hits.
        cooldown.remainingSeconds = cooldown.durationSeconds
        val attackerId = IDENTITY_MAPPER.get(attacker).networkEntityId.toInt()
        val attackerTransform = TRANSFORM_MAPPER.get(attacker)
        val target = selectTarget(attacker, attackerTransform, attack, aimX, aimY, intent.optionalTargetEntityId)
        if (target == null) {
            resolvedEvents.addLast(
                GameEvent(
                    eventType = GameEventType.ATTACK_MISSED,
                    objectId = intent.optionalTargetEntityId ?: NO_TARGET_OBJECT_ID,
                    message = "entity $attackerId attack missed",
                    sourceEntityId = attackerId,
                    targetEntityId = null,
                ),
            )
            return
        }

        val targetId = IDENTITY_MAPPER.get(target).networkEntityId.toInt()
        healthSystem.applyDamage(target, attack.damage)
        characterStateSystem.synchronizeState(target)
        resolvedEvents.addLast(
            GameEvent(
                eventType = GameEventType.ATTACK_HIT,
                objectId = targetId,
                message = "entity $attackerId hit entity $targetId",
                sourceEntityId = attackerId,
                targetEntityId = targetId,
                amount = attack.damage,
            ),
        )
    }

    private fun selectTarget(
        attacker: Entity,
        attackerTransform: TransformComponent,
        attack: AttackComponent,
        aimX: Float,
        aimY: Float,
        optionalTargetEntityId: Int?,
    ): Entity? {
        val candidates = if (optionalTargetEntityId != null) {
            listOfNotNull(
                engine.entities.firstOrNull { candidate ->
                    IDENTITY_MAPPER.get(candidate)?.networkEntityId == optionalTargetEntityId.toLong()
                },
            )
        } else {
            engine.entities.toList()
        }
        return candidates.asSequence()
            .filter { it !== attacker && isAttackable(it) }
            .mapNotNull { target ->
                val targetTransform = TRANSFORM_MAPPER.get(target)
                val deltaX = targetTransform.x - attackerTransform.x
                val deltaY = targetTransform.y - attackerTransform.y
                val distanceSquared = deltaX * deltaX + deltaY * deltaY
                if (!distanceSquared.isFinite() || distanceSquared > attack.range * attack.range) return@mapNotNull null
                val directionDot = if (distanceSquared <= SAME_POSITION_EPSILON) {
                    1f
                } else {
                    (deltaX * aimX + deltaY * aimY) / sqrt(distanceSquared)
                }
                if (directionDot < attack.minimumDirectionDot) null else target to distanceSquared
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun isAttackable(entity: Entity): Boolean =
        IDENTITY_MAPPER.get(entity) != null &&
            TRANSFORM_MAPPER.get(entity) != null &&
            HEALTH_MAPPER.get(entity) != null &&
            STATE_MAPPER.get(entity)?.state == CharacterState.ALIVE

    companion object {
        const val PRIORITY = 125
        private const val MIN_AIM_LENGTH_SQUARED = 0.0001f
        private const val SAME_POSITION_EPSILON = 0.000001f
        private const val NO_TARGET_OBJECT_ID = -1
        private val ATTACK_MAPPER = ComponentMapper.getFor(AttackComponent::class.java)
        private val COOLDOWN_MAPPER = ComponentMapper.getFor(CooldownComponent::class.java)
        private val IDENTITY_MAPPER = ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val HEALTH_MAPPER = ComponentMapper.getFor(HealthComponent::class.java)
        private val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        private val FAMILY: Family = Family.all(
            AttackComponent::class.java,
            CooldownComponent::class.java,
            NetworkIdentityComponent::class.java,
            TransformComponent::class.java,
            CharacterStateComponent::class.java,
        ).get()
    }
}
