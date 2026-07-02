package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.client.ecs.component.LocalPlayerComponent
import game.client.input.GameInputSource
import game.shared.ecs.component.PlayerInputComponent

/** Updates the local player's shared input state from the active platform source. */
class InputSystem(
    private val inputSource: GameInputSource,
) : IteratingSystem(FAMILY, PRIORITY) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        inputSource.update(INPUT_MAPPER.get(entity).state)
    }

    private companion object {
        const val PRIORITY = 100
        val INPUT_MAPPER: ComponentMapper<PlayerInputComponent> =
            ComponentMapper.getFor(PlayerInputComponent::class.java)
        val FAMILY: Family = Family.all(
            LocalPlayerComponent::class.java,
            PlayerInputComponent::class.java,
        ).get()
    }
}
