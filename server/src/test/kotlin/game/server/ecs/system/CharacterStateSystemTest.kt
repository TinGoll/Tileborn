package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.VelocityComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CharacterStateSystemTest {
    @Test
    fun `zero health transitions alive character to dead`() {
        val engine = Engine()
        val characterState = CharacterStateComponent(CharacterState.ALIVE)
        val input = PlayerInputComponent().apply {
            state.setMovement(1f, 0f)
            state.attack = true
        }
        val velocity = VelocityComponent(x = 4f)
        val entity = engine.createEntity().apply {
            add(HealthComponent(currentHealth = 0f, maxHealth = 100f))
            add(characterState)
            add(input)
            add(velocity)
        }
        engine.addEntity(entity)
        engine.addSystem(CharacterStateSystem())

        engine.update(0f)

        assertEquals(CharacterState.DEAD, characterState.state)
        assertEquals(0f, input.state.moveX, 0f)
        assertFalse(input.state.attack)
        assertEquals(0f, velocity.x, 0f)
    }
}
