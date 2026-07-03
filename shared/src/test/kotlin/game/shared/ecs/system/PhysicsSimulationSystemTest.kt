package game.shared.ecs.system

import com.badlogic.ashley.core.Engine
import game.shared.constants.GameConstants
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.map.MapCollisionObject
import game.shared.physics.PhysicsWorldFactory
import game.shared.physics.TiledCollisionLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsSimulationSystemTest {
    @Test
    fun `dynamic player collides with tiled wall and transform follows body`() {
        val world = PhysicsWorldFactory.create()
        val engine = Engine()
        val physicsSystem = PhysicsSimulationSystem(world)
        try {
            TiledCollisionLoader(world).createStaticBody(
                MapCollisionObject(id = 1, x = 2f, y = -1f, width = 1f, height = 2f),
            )
            val transform = TransformComponent(x = 1f, y = 0f)
            val velocity = VelocityComponent(x = 4f, y = 0f)
            val body = PhysicsWorldFactory.createDynamicPlayerBody(world, transform.x, transform.y)
            engine.addEntity(engine.createEntity().apply {
                add(transform)
                add(velocity)
                add(PhysicsBodyComponent(body))
            })
            engine.addSystem(physicsSystem)

            repeat(120) { engine.update(GameConstants.PHYSICS_FIXED_TIME_STEP) }

            assertTrue("Player crossed the wall: x=${transform.x}", transform.x <= 1.61f)
            assertEquals(body.position.x, transform.x, EPSILON)
            assertEquals(body.position.y, transform.y, EPSILON)
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(physicsSystem)
            physicsSystem.dispose()
            world.dispose()
        }
    }

    @Test
    fun `removing physics entity destroys its body`() {
        val world = PhysicsWorldFactory.create()
        val engine = Engine()
        val physicsSystem = PhysicsSimulationSystem(world)
        try {
            val body = PhysicsWorldFactory.createDynamicPlayerBody(world, 0f, 0f)
            val entity = engine.createEntity().apply {
                add(TransformComponent())
                add(VelocityComponent())
                add(PhysicsBodyComponent(body))
            }
            engine.addSystem(physicsSystem)
            engine.addEntity(entity)

            engine.removeEntity(entity)

            assertEquals(0, world.bodyCount)
        } finally {
            engine.removeSystem(physicsSystem)
            physicsSystem.dispose()
            world.dispose()
        }
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
