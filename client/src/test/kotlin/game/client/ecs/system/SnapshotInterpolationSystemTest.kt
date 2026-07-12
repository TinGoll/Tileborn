package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import game.client.ecs.component.InterpolatedTransformComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.protocol.EntitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotInterpolationSystemTest {
    @Test
    fun `remote entity uses interpolated transform`() {
        val engine = Engine()
        val system = SnapshotInterpolationSystem(interpolationDelayTicks = 0f)
        val interpolated = InterpolatedTransformComponent()
        engine.addEntity(engine.createEntity().apply {
            add(NetworkIdentityComponent(networkEntityId = 4L))
            add(interpolated)
        })
        engine.addSystem(system)
        system.recordSnapshot(0, snapshot(x = 0f))
        system.recordSnapshot(10, snapshot(x = 10f))

        engine.update(0.25f)

        assertEquals(5f, interpolated.x, 0.001f)
    }

    private fun snapshot(x: Float) = EntitySnapshot(
        entityId = 4,
        x = x,
        y = 0f,
        velocityX = 0f,
        velocityY = 0f,
    )
}
