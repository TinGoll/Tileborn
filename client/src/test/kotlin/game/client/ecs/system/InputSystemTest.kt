package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import game.client.ecs.component.LocalPlayerComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.input.GameInputState
import org.junit.Assert.assertEquals
import org.junit.Test

class InputSystemTest {
    @Test
    fun `system updates only explicitly local player input`() {
        val engine = Engine()
        val localInput = PlayerInputComponent()
        val remoteInput = PlayerInputComponent()
        engine.addEntity(engine.createEntity().apply {
            add(localInput)
            add(LocalPlayerComponent())
        })
        engine.addEntity(engine.createEntity().apply { add(remoteInput) })
        engine.addSystem(InputSystem { state: GameInputState -> state.setMovement(0.25f, 0.75f) })

        engine.update(0f)

        assertEquals(0.25f, localInput.state.moveX, 0f)
        assertEquals(0.75f, localInput.state.moveY, 0f)
        assertEquals(0f, remoteInput.state.moveX, 0f)
        assertEquals(0f, remoteInput.state.moveY, 0f)
    }
}
