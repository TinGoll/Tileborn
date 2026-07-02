package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.OrthographicCamera
import game.client.ecs.component.CameraTargetComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFollowSystemTest {
    @Test
    fun `camera centers on target transform`() {
        val engine = Engine()
        val camera = OrthographicCamera()
        engine.addEntity(engine.createEntity().apply {
            add(TransformComponent(x = 7f, y = 9f))
            add(CameraTargetComponent())
        })
        val system = CameraFollowSystem(camera)
        engine.addSystem(system)

        system.update(0f)

        assertEquals(7f, camera.position.x, 0f)
        assertEquals(9f, camera.position.y, 0f)
    }
}
