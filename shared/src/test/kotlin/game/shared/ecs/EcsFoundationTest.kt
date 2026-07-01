package game.shared.ecs

import com.badlogic.ashley.core.Engine
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EcsFoundationTest {
    @Test
    fun `entity can be created with transform component`() {
        val engine = Engine()
        val transform = TransformComponent(x = 12f, y = 8f)
        val entity = engine.createEntity().apply {
            add(transform)
        }

        engine.addEntity(entity)

        assertTrue(engine.entities.contains(entity, true))
        assertSame(transform, entity.getComponent(TransformComponent::class.java))
    }

    @Test
    fun `velocity component can be added and retrieved`() {
        val engine = Engine()
        val entity = engine.createEntity()
        val velocity = VelocityComponent(x = 3f, y = -2f)

        entity.add(velocity)

        assertSame(velocity, entity.getComponent(VelocityComponent::class.java))
        assertEquals(3f, entity.getComponent(VelocityComponent::class.java).x, 0f)
        assertEquals(-2f, entity.getComponent(VelocityComponent::class.java).y, 0f)
    }

    @Test
    fun `entity removal keeps engine usable`() {
        val engine = Engine()
        val removedEntity = engine.createEntity()
        engine.addEntity(removedEntity)

        engine.removeEntity(removedEntity)
        val remainingEntity = engine.createEntity()
        engine.addEntity(remainingEntity)

        assertFalse(engine.entities.contains(removedEntity, true))
        assertTrue(engine.entities.contains(remainingEntity, true))
        assertEquals(1, engine.entities.size())
    }
}
