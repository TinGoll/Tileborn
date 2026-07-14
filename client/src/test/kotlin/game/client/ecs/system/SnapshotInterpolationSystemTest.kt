package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.physics.box2d.BodyDef
import game.client.ecs.component.InterpolatedTransformComponent
import game.shared.constants.GameConstants
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.ecs.system.PhysicsSimulationSystem
import game.shared.physics.PhysicsWorldFactory
import game.shared.protocol.EntitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotInterpolationSystemTest {
    @Test
    fun `default interpolation stays two server ticks behind latest snapshot`() {
        val engine = Engine()
        val system = SnapshotInterpolationSystem()
        val interpolated = InterpolatedTransformComponent()
        engine.addEntity(engine.createEntity().apply {
            add(NetworkIdentityComponent(networkEntityId = 4L))
            add(interpolated)
        })
        engine.addSystem(system)
        system.recordSnapshot(0, snapshot(x = 0f))
        system.recordSnapshot(10, snapshot(x = 10f))

        engine.update(0.5f)

        assertEquals(8f, interpolated.x, 0.001f)
    }

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

    @Test
    fun `remote player collision proxy follows interpolated transform`() {
        val world = PhysicsWorldFactory.create()
        val engine = Engine()
        val interpolationSystem = SnapshotInterpolationSystem(interpolationDelayTicks = 0f)
        val physicsSystem = PhysicsSimulationSystem(world)
        val body = PhysicsWorldFactory.createKinematicPlayerBody(world, 0f, 0f)
        try {
            engine.addEntity(engine.createEntity().apply {
                add(NetworkIdentityComponent(networkEntityId = 4L))
                add(InterpolatedTransformComponent())
                add(TransformComponent())
                add(VelocityComponent())
                add(PhysicsBodyComponent(body = body, synchronizeVelocityWithBody = false))
            })
            engine.addSystem(interpolationSystem)
            engine.addSystem(physicsSystem)
            interpolationSystem.recordSnapshot(0, snapshot(x = 0f))
            interpolationSystem.recordSnapshot(10, snapshot(x = 10f))

            engine.update(0.25f)

            assertEquals(BodyDef.BodyType.KinematicBody, body.type)
            assertEquals(5f, body.position.x, 0.001f)
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(interpolationSystem)
            engine.removeSystem(physicsSystem)
            physicsSystem.dispose()
            world.dispose()
        }
    }

    private fun snapshot(x: Float) = EntitySnapshot(
        entityId = 4,
        x = x,
        y = 0f,
        velocityX = 0f,
        velocityY = 0f,
        currentHealth = 100f,
        maxHealth = 100f,
        movementSpeed = 4f,
        characterState = CharacterState.ALIVE,
        collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
    )
}
