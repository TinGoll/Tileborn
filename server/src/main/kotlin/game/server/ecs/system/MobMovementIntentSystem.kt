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
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import kotlin.math.sqrt

/** Converts server AI decisions into velocity consumed by authoritative Box2D simulation. */
class MobMovementIntentSystem : IteratingSystem(MOB_FAMILY, PRIORITY) {
    override fun processEntity(mob: Entity, deltaTime: Float) {
        val ai = AI_MAPPER.get(mob)
        when (ai.state) {
            AiState.CHASE -> {
                val target = findTarget(TARGET_MAPPER.get(mob).targetEntityId)
                if (target == null) stop(mob) else {
                    val transform = TRANSFORM_MAPPER.get(target)
                    moveToward(mob, transform.x, transform.y, ai.attackRadius, deltaTime)
                }
            }
            AiState.RETURN -> {
                val home = HOME_MAPPER.get(mob)
                moveToward(mob, home.x, home.y, 0f, deltaTime)
            }
            AiState.IDLE, AiState.ATTACK, AiState.DEAD -> stop(mob)
        }
    }

    private fun moveToward(
        mob: Entity,
        destinationX: Float,
        destinationY: Float,
        stopDistance: Float,
        deltaTime: Float,
    ) {
        val transform = TRANSFORM_MAPPER.get(mob)
        val deltaX = destinationX - transform.x
        val deltaY = destinationY - transform.y
        val distanceSquared = deltaX * deltaX + deltaY * deltaY
        if (!distanceSquared.isFinite() || distanceSquared <= SAME_POSITION_EPSILON) {
            stop(mob)
            return
        }

        val distance = sqrt(distanceSquared)
        val remainingDistance = (distance - stopDistance).coerceAtLeast(0f)
        val configuredSpeed = SPEED_MAPPER.get(mob).movementSpeed
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f
        val elapsed = deltaTime.takeIf(Float::isFinite)?.takeIf { it > 0f }
        val speed = if (elapsed == null) configuredSpeed else minOf(configuredSpeed, remainingDistance / elapsed)
        val velocity = VELOCITY_MAPPER.get(mob)
        velocity.x = deltaX / distance * speed
        velocity.y = deltaY / distance * speed
    }

    private fun findTarget(targetEntityId: Long?): Entity? {
        if (targetEntityId == null) return null
        for (candidate in engine.entities) {
            if (PLAYER_MAPPER.get(candidate) == null) continue
            if (IDENTITY_MAPPER.get(candidate)?.networkEntityId == targetEntityId) return candidate
        }
        return null
    }

    private fun stop(mob: Entity) {
        val velocity = VELOCITY_MAPPER.get(mob)
        velocity.x = 0f
        velocity.y = 0f
    }

    companion object {
        const val PRIORITY = 150
        private const val SAME_POSITION_EPSILON = 0.000001f
        private val AI_MAPPER = ComponentMapper.getFor(AiStateComponent::class.java)
        private val TARGET_MAPPER = ComponentMapper.getFor(AggroTargetComponent::class.java)
        private val HOME_MAPPER = ComponentMapper.getFor(HomePositionComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        private val SPEED_MAPPER = ComponentMapper.getFor(MovementSpeedComponent::class.java)
        private val IDENTITY_MAPPER = ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        private val PLAYER_MAPPER = ComponentMapper.getFor(PlayerInputComponent::class.java)
        private val MOB_FAMILY = Family.all(
            MobComponent::class.java,
            AiStateComponent::class.java,
            AggroTargetComponent::class.java,
            HomePositionComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
            MovementSpeedComponent::class.java,
        ).get()
    }
}
