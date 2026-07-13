package game.shared.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent

/** Applies player movement intent to velocity and integrates position in world units. */
class MovementSystem : IteratingSystem(FAMILY, PRIORITY) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val input = INPUT_MAPPER.get(entity).state
        val velocity = VELOCITY_MAPPER.get(entity)
        if (STATE_MAPPER.get(entity).state != CharacterState.ALIVE) {
            velocity.x = 0f
            velocity.y = 0f
            return
        }
        val movementSpeed = SPEED_MAPPER.get(entity).movementSpeed
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f
        velocity.x = input.moveX * movementSpeed
        velocity.y = input.moveY * movementSpeed

        if (!PHYSICS_BODY_MAPPER.has(entity)) {
            val transform = TRANSFORM_MAPPER.get(entity)
            transform.x += velocity.x * deltaTime
            transform.y += velocity.y * deltaTime
        }
    }

    private companion object {
        const val PRIORITY = 200
        val INPUT_MAPPER: ComponentMapper<PlayerInputComponent> =
            ComponentMapper.getFor(PlayerInputComponent::class.java)
        val VELOCITY_MAPPER: ComponentMapper<VelocityComponent> =
            ComponentMapper.getFor(VelocityComponent::class.java)
        val TRANSFORM_MAPPER: ComponentMapper<TransformComponent> =
            ComponentMapper.getFor(TransformComponent::class.java)
        val PHYSICS_BODY_MAPPER: ComponentMapper<PhysicsBodyComponent> =
            ComponentMapper.getFor(PhysicsBodyComponent::class.java)
        val SPEED_MAPPER: ComponentMapper<MovementSpeedComponent> =
            ComponentMapper.getFor(MovementSpeedComponent::class.java)
        val STATE_MAPPER: ComponentMapper<CharacterStateComponent> =
            ComponentMapper.getFor(CharacterStateComponent::class.java)
        val FAMILY: Family = Family.all(
            PlayerInputComponent::class.java,
            VelocityComponent::class.java,
            TransformComponent::class.java,
            MovementSpeedComponent::class.java,
            CharacterStateComponent::class.java,
        ).get()
    }
}
