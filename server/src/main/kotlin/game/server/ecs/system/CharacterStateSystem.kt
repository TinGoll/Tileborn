package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.VelocityComponent

/** Performs authoritative lifecycle transitions before gameplay and physics systems run. */
class CharacterStateSystem : IteratingSystem(FAMILY, PRIORITY) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        synchronizeState(entity)
    }

    fun synchronizeState(entity: Entity) {
        val state = STATE_MAPPER.get(entity) ?: error("Entity has no CharacterStateComponent.")
        val health = HEALTH_MAPPER.get(entity) ?: error("Entity has no HealthComponent.")
        if (state.state == CharacterState.ALIVE && health.currentHealth <= 0f) {
            state.state = CharacterState.DEAD
        }
        if (state.state != CharacterState.ALIVE) disableGameplayInput(entity)
    }

    private fun disableGameplayInput(entity: Entity) {
        INPUT_MAPPER.get(entity)?.state?.let { input ->
            input.moveX = 0f
            input.moveY = 0f
            input.attack = false
            input.interact = false
        }
        VELOCITY_MAPPER.get(entity)?.let { velocity ->
            velocity.x = 0f
            velocity.y = 0f
        }
    }

    private companion object {
        const val PRIORITY = 75
        val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        val HEALTH_MAPPER = ComponentMapper.getFor(HealthComponent::class.java)
        val INPUT_MAPPER = ComponentMapper.getFor(PlayerInputComponent::class.java)
        val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        val FAMILY: Family = Family.all(
            CharacterStateComponent::class.java,
            HealthComponent::class.java,
        ).get()
    }
}
