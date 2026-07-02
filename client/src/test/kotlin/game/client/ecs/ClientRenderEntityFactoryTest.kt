package game.client.ecs

import com.badlogic.ashley.core.Engine
import game.client.ecs.component.CameraTargetComponent
import game.client.ecs.component.PrimitiveShape
import game.client.ecs.component.RenderPrimitiveComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClientRenderEntityFactoryTest {
    @Test
    fun `test player has transform circle render data and camera target`() {
        val engine = Engine()

        val player = ClientRenderEntityFactory.createTestPlayer(engine, x = 5f, y = 6f)

        val transform = player.getComponent(TransformComponent::class.java)
        val render = player.getComponent(RenderPrimitiveComponent::class.java)
        assertEquals(5f, transform.x, 0f)
        assertEquals(6f, transform.y, 0f)
        assertEquals(PrimitiveShape.CIRCLE, render.shape)
        assertNotNull(player.getComponent(CameraTargetComponent::class.java))
        assertEquals(1, engine.entities.size())
    }
}
