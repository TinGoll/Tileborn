package game.desktop

import com.badlogic.gdx.Input
import game.client.ecs.ClientEcsWorld
import game.client.ecs.ClientRenderEntityFactory
import game.client.input.KeyboardInputSource
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopInputSmokeTest {
    @Test
    fun `desktop keyboard input moves local player upward`() {
        val source = KeyboardInputSource { key -> key == Input.Keys.W }
        val world = ClientEcsWorld(source)
        val player = ClientRenderEntityFactory.createTestPlayer(
            world.engine,
            world.physicsWorld,
            x = 3f,
            y = 4f,
        )

        world.engine.update(0.5f)

        val transform = player.getComponent(TransformComponent::class.java)
        assertEquals(3f, transform.x, 0f)
        assertTrue(transform.y > 4f)
        world.dispose()
    }
}
