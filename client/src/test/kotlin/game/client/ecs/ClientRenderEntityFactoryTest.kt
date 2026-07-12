package game.client.ecs

import com.badlogic.ashley.core.Engine
import game.client.ecs.component.CameraTargetComponent
import game.client.ecs.component.LocalPlayerComponent
import game.client.ecs.component.InterpolatedTransformComponent
import game.client.ecs.component.PrimitiveShape
import game.client.ecs.component.RenderPrimitiveComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.map.GameMapData
import game.shared.map.MapCollisionObject
import game.shared.physics.PhysicsWorldFactory
import game.shared.protocol.EntitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClientRenderEntityFactoryTest {
    @Test
    fun `test player has transform circle render data and camera target`() {
        val engine = Engine()
        val physicsWorld = PhysicsWorldFactory.create()

        val player = ClientRenderEntityFactory.createTestPlayer(engine, physicsWorld, x = 5f, y = 6f)

        val transform = player.getComponent(TransformComponent::class.java)
        val render = player.getComponent(RenderPrimitiveComponent::class.java)
        assertEquals(5f, transform.x, 0f)
        assertEquals(6f, transform.y, 0f)
        assertEquals(PrimitiveShape.CIRCLE, render.shape)
        assertNotNull(player.getComponent(CameraTargetComponent::class.java))
        assertNotNull(player.getComponent(LocalPlayerComponent::class.java))
        assertNotNull(player.getComponent(PlayerInputComponent::class.java))
        assertNotNull(player.getComponent(VelocityComponent::class.java))
        assertNotNull(player.getComponent(PhysicsBodyComponent::class.java))
        assertEquals(1, engine.entities.size())
        physicsWorld.dispose()
    }

    @Test
    fun `debug collision geometry renders map rectangles around their centers`() {
        val engine = Engine()
        val mapData = GameMapData(
            mapId = "debug",
            spawnPoints = emptyList(),
            collisionObjects = listOf(MapCollisionObject(id = 1, x = 2f, y = 3f, width = 4f, height = 2f)),
            triggers = emptyList(),
            portals = emptyList(),
        )

        val wall = ClientRenderEntityFactory.createDebugCollisionGeometry(engine, mapData).single()

        val transform = wall.getComponent(TransformComponent::class.java)
        val render = wall.getComponent(RenderPrimitiveComponent::class.java)
        assertEquals(4f, transform.x, 0f)
        assertEquals(4f, transform.y, 0f)
        assertEquals(PrimitiveShape.RECTANGLE, render.shape)
        assertEquals(4f, render.width, 0f)
        assertEquals(2f, render.height, 0f)
    }

    @Test
    fun `local player created from snapshot is visible and keeps server entity id`() {
        val engine = Engine()
        val physicsWorld = PhysicsWorldFactory.create()

        val player = ClientRenderEntityFactory.createLocalPlayerFromSnapshot(
            engine,
            physicsWorld,
            EntitySnapshot(
                entityId = 42,
                x = 8f,
                y = 9f,
                velocityX = 1f,
                velocityY = 0f,
            ),
        )

        val identity = player.getComponent(NetworkIdentityComponent::class.java)
        val transform = player.getComponent(TransformComponent::class.java)
        val render = player.getComponent(RenderPrimitiveComponent::class.java)
        assertEquals(42L, identity.networkEntityId)
        assertEquals(8f, transform.x, 0f)
        assertEquals(9f, transform.y, 0f)
        assertEquals(PrimitiveShape.CIRCLE, render.shape)
        assertNotNull(player.getComponent(CameraTargetComponent::class.java))
        assertNotNull(player.getComponent(LocalPlayerComponent::class.java))
        assertNotNull(player.getComponent(PhysicsBodyComponent::class.java))
        physicsWorld.dispose()
    }

    @Test
    fun `remote player has an interpolated render transform`() {
        val player = ClientRenderEntityFactory.createRemotePlayerFromSnapshot(
            Engine(),
            EntitySnapshot(entityId = 7, x = 2f, y = 3f, velocityX = 0f, velocityY = 0f),
        )

        val interpolated = player.getComponent(InterpolatedTransformComponent::class.java)
        assertNotNull(interpolated)
        assertEquals(2f, interpolated.x, 0f)
        assertEquals(3f, interpolated.y, 0f)
    }
}
