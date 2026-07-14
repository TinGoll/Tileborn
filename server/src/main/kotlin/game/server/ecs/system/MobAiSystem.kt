package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiState
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.HomePositionComponent
import game.server.ecs.component.MobComponent
import game.shared.ecs.component.AttackComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.CooldownComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PendingAttack
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent

/** Runs the authoritative mob state machine and emits server-authored attack intent. */
class MobAiSystem : IteratingSystem(MOB_FAMILY, PRIORITY) {
    override fun processEntity(mob: Entity, deltaTime: Float) {
        val ai = AI_MAPPER.get(mob)
        val targetComponent = TARGET_MAPPER.get(mob)
        val health = HEALTH_MAPPER.get(mob)
        val characterState = STATE_MAPPER.get(mob)
        if (characterState.state != CharacterState.ALIVE || health.currentHealth <= 0f) {
            ai.state = AiState.DEAD
            targetComponent.targetEntityId = null
            ATTACK_MAPPER.get(mob).pendingAttacks.clear()
            return
        }

        when (ai.state) {
            AiState.IDLE -> updateIdle(mob, ai, targetComponent)
            AiState.CHASE, AiState.ATTACK -> updateEngaged(mob, ai, targetComponent)
            AiState.RETURN -> updateReturn(mob, ai, targetComponent)
            AiState.DEAD -> {
                targetComponent.targetEntityId = null
            }
        }

        if (ai.state == AiState.ATTACK) queueAttack(mob, targetComponent.targetEntityId)
    }

    private fun updateIdle(mob: Entity, ai: AiStateComponent, target: AggroTargetComponent) {
        val targetEntity = findLivingPlayer(target.targetEntityId)
        if (targetEntity == null) {
            target.targetEntityId = null
            return
        }
        val distanceSquared = squaredDistance(mob, targetEntity)
        if (!distanceSquared.isFinite() || distanceSquared > ai.aggroRadius * ai.aggroRadius) {
            target.targetEntityId = null
            return
        }
        ai.state = if (distanceSquared <= ai.attackRadius * ai.attackRadius) AiState.ATTACK else AiState.CHASE
    }

    private fun updateEngaged(mob: Entity, ai: AiStateComponent, target: AggroTargetComponent) {
        val targetEntity = findLivingPlayer(target.targetEntityId)
        if (targetEntity == null) {
            loseTarget(mob, ai, target)
            return
        }
        val distanceSquared = squaredDistance(mob, targetEntity)
        if (!distanceSquared.isFinite() || distanceSquared > ai.aggroRadius * ai.aggroRadius) {
            loseTarget(mob, ai, target)
            return
        }
        ai.state = if (distanceSquared <= ai.attackRadius * ai.attackRadius) AiState.ATTACK else AiState.CHASE
    }

    private fun updateReturn(mob: Entity, ai: AiStateComponent, target: AggroTargetComponent) {
        target.targetEntityId = null
        if (squaredDistanceToHome(mob) <= HOME_ARRIVAL_RADIUS * HOME_ARRIVAL_RADIUS) {
            ai.state = AiState.IDLE
        }
    }

    private fun loseTarget(mob: Entity, ai: AiStateComponent, target: AggroTargetComponent) {
        target.targetEntityId = null
        ai.state = if (squaredDistanceToHome(mob) <= HOME_ARRIVAL_RADIUS * HOME_ARRIVAL_RADIUS) {
            AiState.IDLE
        } else {
            AiState.RETURN
        }
    }

    private fun queueAttack(mob: Entity, targetEntityId: Long?) {
        val targetEntity = findLivingPlayer(targetEntityId) ?: return
        val cooldown = COOLDOWN_MAPPER.get(mob)
        val attack = ATTACK_MAPPER.get(mob)
        if (cooldown.remainingSeconds > 0f || attack.pendingAttacks.isNotEmpty()) return

        val mobTransform = TRANSFORM_MAPPER.get(mob)
        val targetTransform = TRANSFORM_MAPPER.get(targetEntity)
        var aimX = targetTransform.x - mobTransform.x
        var aimY = targetTransform.y - mobTransform.y
        if (aimX * aimX + aimY * aimY <= SAME_POSITION_EPSILON) {
            aimX = 1f
            aimY = 0f
        }
        val sequence = attack.lastReceivedInputSequence + 1L
        attack.lastReceivedInputSequence = sequence
        attack.pendingAttacks.addLast(
            PendingAttack(
                inputSequence = sequence,
                clientTick = SERVER_AUTHORED_TICK,
                aimX = aimX,
                aimY = aimY,
                optionalTargetEntityId = targetEntityId?.toInt(),
            ),
        )
    }

    private fun findLivingPlayer(targetEntityId: Long?): Entity? {
        if (targetEntityId == null) return null
        for (candidate in engine.entities) {
            if (PLAYER_MAPPER.get(candidate) == null) continue
            if (IDENTITY_MAPPER.get(candidate)?.networkEntityId != targetEntityId) continue
            if (STATE_MAPPER.get(candidate)?.state != CharacterState.ALIVE) return null
            if ((HEALTH_MAPPER.get(candidate)?.currentHealth ?: 0f) <= 0f) return null
            return candidate
        }
        return null
    }

    private fun squaredDistance(first: Entity, second: Entity): Float {
        val firstTransform = TRANSFORM_MAPPER.get(first)
        val secondTransform = TRANSFORM_MAPPER.get(second)
        val deltaX = secondTransform.x - firstTransform.x
        val deltaY = secondTransform.y - firstTransform.y
        return deltaX * deltaX + deltaY * deltaY
    }

    private fun squaredDistanceToHome(mob: Entity): Float {
        val transform = TRANSFORM_MAPPER.get(mob)
        val home = HOME_MAPPER.get(mob)
        val deltaX = home.x - transform.x
        val deltaY = home.y - transform.y
        return deltaX * deltaX + deltaY * deltaY
    }

    companion object {
        const val PRIORITY = 85
        const val HOME_ARRIVAL_RADIUS = 0.05f
        private const val SAME_POSITION_EPSILON = 0.000001f
        private const val SERVER_AUTHORED_TICK = 0L
        private val AI_MAPPER = ComponentMapper.getFor(AiStateComponent::class.java)
        private val TARGET_MAPPER = ComponentMapper.getFor(AggroTargetComponent::class.java)
        private val HOME_MAPPER = ComponentMapper.getFor(HomePositionComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val HEALTH_MAPPER = ComponentMapper.getFor(HealthComponent::class.java)
        private val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        private val ATTACK_MAPPER = ComponentMapper.getFor(AttackComponent::class.java)
        private val COOLDOWN_MAPPER = ComponentMapper.getFor(CooldownComponent::class.java)
        private val IDENTITY_MAPPER = ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        private val PLAYER_MAPPER = ComponentMapper.getFor(PlayerInputComponent::class.java)
        private val MOB_FAMILY = Family.all(
            MobComponent::class.java,
            AiStateComponent::class.java,
            AggroTargetComponent::class.java,
            HomePositionComponent::class.java,
            TransformComponent::class.java,
            HealthComponent::class.java,
            CharacterStateComponent::class.java,
            AttackComponent::class.java,
            CooldownComponent::class.java,
        ).get()
    }
}
