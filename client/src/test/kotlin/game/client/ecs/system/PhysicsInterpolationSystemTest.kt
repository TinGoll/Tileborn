package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import game.client.ecs.component.PhysicsInterpolatedTransformComponent
import game.shared.constants.GameConstants
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.ecs.system.PhysicsSimulationSystem
import game.shared.physics.PhysicsWorldFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicsInterpolationSystemTest {
    @Test
    fun `visual position is interpolated between fixed physics steps`() {
        val world = PhysicsWorldFactory.create()
        val physics = PhysicsSimulationSystem(world)
        val interpolation = PhysicsInterpolationSystem(physics)
        val engine = Engine()
        try {
            val body = PhysicsWorldFactory.createDynamicPlayerBody(world, 0f, 0f)
            val visual = PhysicsInterpolatedTransformComponent()
            engine.addEntity(engine.createEntity().apply {
                add(TransformComponent())
                add(VelocityComponent(x = 4f))
                add(PhysicsBodyComponent(body))
                add(visual)
            })
            engine.addSystem(physics)
            engine.addSystem(interpolation)

            engine.update(GameConstants.PHYSICS_FIXED_TIME_STEP)
            engine.update(GameConstants.PHYSICS_FIXED_TIME_STEP * 0.5f)

            assertEquals(body.position.x * 0.5f, visual.x, 0.001f)
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(interpolation)
            engine.removeSystem(physics)
            physics.dispose()
            world.dispose()
        }
    }
}
