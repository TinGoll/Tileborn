package game.client.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.physics.box2d.BodyDef
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
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.map.GameMapData
import game.shared.map.MapCollisionObject
import game.shared.physics.PhysicsWorldFactory
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.NetworkEntityKind
import game.shared.ecs.component.DefinitionIdComponent
import game.shared.constants.GameConstants
import game.shared.ecs.system.PhysicsSimulationSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
                currentHealth = 75f,
                maxHealth = 100f,
                movementSpeed = 4f,
                characterState = CharacterState.ALIVE,
                collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
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
        assertEquals(75f, player.getComponent(HealthComponent::class.java).currentHealth, 0f)
        assertEquals(CharacterState.ALIVE, player.getComponent(CharacterStateComponent::class.java).state)
        physicsWorld.dispose()
    }

    @Test
    fun `remote player has an interpolated render transform`() {
        val physicsWorld = PhysicsWorldFactory.create()
        val player = ClientRenderEntityFactory.createRemoteEntityFromSnapshot(
            Engine(),
            physicsWorld,
            EntitySnapshot(
                entityId = 7,
                x = 2f,
                y = 3f,
                velocityX = 0f,
                velocityY = 0f,
                currentHealth = 0f,
                maxHealth = 100f,
                movementSpeed = 4f,
                characterState = CharacterState.DEAD,
                collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
            ),
        )

        val interpolated = player.getComponent(InterpolatedTransformComponent::class.java)
        assertNotNull(interpolated)
        assertEquals(2f, interpolated.x, 0f)
        assertEquals(3f, interpolated.y, 0f)
        val physics = player.getComponent(PhysicsBodyComponent::class.java)
        assertNotNull(physics)
        assertEquals(BodyDef.BodyType.KinematicBody, physics.body.type)
        assertFalse(physics.synchronizeVelocityWithBody)
        physicsWorld.dispose()
    }

    @Test
    fun `remote mob rendering smoke uses green primitive and keeps its definition id`() {
        val physicsWorld = PhysicsWorldFactory.create()
        val mob = ClientRenderEntityFactory.createRemoteEntityFromSnapshot(
            Engine(),
            physicsWorld,
            EntitySnapshot(
                entityId = -1,
                x = 3f,
                y = 4f,
                velocityX = 0f,
                velocityY = 0f,
                currentHealth = 30f,
                maxHealth = 30f,
                movementSpeed = 2f,
                characterState = CharacterState.ALIVE,
                collisionRadius = 0.35f,
                entityKind = NetworkEntityKind.MOB,
                definitionId = "slime",
            ),
        )

        val render = mob.getComponent(RenderPrimitiveComponent::class.java)
        assertEquals("slime", mob.getComponent(DefinitionIdComponent::class.java).definitionId)
        assertEquals(0.25f, render.red, 0f)
        assertEquals(0.85f, render.green, 0f)
        assertEquals(0.3f, render.blue, 0f)
        val physics = mob.getComponent(PhysicsBodyComponent::class.java)
        assertNotNull(physics)
        assertEquals(BodyDef.BodyType.KinematicBody, physics.body.type)
        assertEquals(0.35f, physics.body.fixtureList.single().shape.radius, 0f)
        assertFalse(physics.synchronizeVelocityWithBody)
        physicsWorld.dispose()
    }

    @Test
    fun `local player does not pass through remote player collision proxy`() {
        val engine = Engine()
        val physicsWorld = PhysicsWorldFactory.create()
        val physicsSystem = PhysicsSimulationSystem(physicsWorld)
        try {
            val local = ClientRenderEntityFactory.createLocalPlayerFromSnapshot(
                engine,
                physicsWorld,
                playerSnapshot(entityId = 1, x = 0f, velocityX = 4f),
            )
            val remote = ClientRenderEntityFactory.createRemoteEntityFromSnapshot(
                engine,
                physicsWorld,
                playerSnapshot(entityId = 2, x = 2f, velocityX = 0f),
            )
            engine.addSystem(physicsSystem)

            repeat(120) { engine.update(GameConstants.PHYSICS_FIXED_TIME_STEP) }

            val localX = local.getComponent(TransformComponent::class.java).x
            val remoteX = remote.getComponent(PhysicsBodyComponent::class.java).body.position.x
            assertTrue(
                "Players overlapped on client: local=$localX, remote=$remoteX",
                remoteX - localX >= GameConstants.PLAYER_COLLISION_RADIUS * 2f - 0.0001f,
            )
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(physicsSystem)
            physicsSystem.dispose()
            physicsWorld.dispose()
        }
    }

    @Test
    fun `local player does not pass through remote mob collision proxy`() {
        val engine = Engine()
        val physicsWorld = PhysicsWorldFactory.create()
        val physicsSystem = PhysicsSimulationSystem(physicsWorld)
        try {
            val local = ClientRenderEntityFactory.createLocalPlayerFromSnapshot(
                engine,
                physicsWorld,
                playerSnapshot(entityId = 1, x = 0f, velocityX = 4f),
            )
            val mobRadius = 0.35f
            val mob = ClientRenderEntityFactory.createRemoteEntityFromSnapshot(
                engine,
                physicsWorld,
                EntitySnapshot(
                    entityId = 2,
                    x = 2f,
                    y = 0f,
                    velocityX = 0f,
                    velocityY = 0f,
                    currentHealth = 30f,
                    maxHealth = 30f,
                    movementSpeed = 2f,
                    characterState = CharacterState.ALIVE,
                    collisionRadius = mobRadius,
                    entityKind = NetworkEntityKind.MOB,
                    definitionId = "slime",
                ),
            )
            engine.addSystem(physicsSystem)

            repeat(120) { engine.update(GameConstants.PHYSICS_FIXED_TIME_STEP) }

            val localX = local.getComponent(TransformComponent::class.java).x
            val mobX = mob.getComponent(PhysicsBodyComponent::class.java).body.position.x
            assertTrue(
                "Player and mob overlapped on client: local=$localX, mob=$mobX",
                mobX - localX >= GameConstants.PLAYER_COLLISION_RADIUS + mobRadius - 0.01f,
            )
        } finally {
            engine.removeAllEntities()
            engine.removeSystem(physicsSystem)
            physicsSystem.dispose()
            physicsWorld.dispose()
        }
    }

    private fun playerSnapshot(entityId: Int, x: Float, velocityX: Float) = EntitySnapshot(
        entityId = entityId,
        x = x,
        y = 0f,
        velocityX = velocityX,
        velocityY = 0f,
        currentHealth = 100f,
        maxHealth = 100f,
        movementSpeed = GameConstants.PLAYER_MOVE_SPEED,
        characterState = CharacterState.ALIVE,
        collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
    )
}
