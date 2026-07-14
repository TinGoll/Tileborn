package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import game.client.ecs.component.LocalPlayerComponent
import game.client.network.PredictedInputBuffer
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.constants.GameConstants
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.physics.PhysicsWorldFactory
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.InputCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerReconciliationSystemTest {
    @Test
    fun `correction replays unacknowledged input after server state`() {
        val buffer = PredictedInputBuffer().apply {
            add(command(1), 0.1f)
            add(command(2), 0.1f)
        }
        val system = ServerReconciliationSystem(buffer, snapDistance = 0.5f, correctionRate = 1000f)
        val entity = localEntity(x = 0.8f)
        val engine = Engine().apply { addEntity(entity); addSystem(system) }

        system.reconcile(entity, snapshot(x = 0.2f), acknowledgedSequence = 1)
        engine.update(0.1f)

        assertEquals(listOf(2L), buffer.entries().map { it.command.inputSequence })
        assertEquals(0.6f, entity.getComponent(TransformComponent::class.java).x, 0.001f)
    }

    @Test
    fun `large correction snaps to authoritative replayed position`() {
        val buffer = PredictedInputBuffer().apply { add(command(1), 0.1f) }
        val system = ServerReconciliationSystem(buffer, snapDistance = 0.5f)
        val entity = localEntity(x = 10f)

        system.reconcile(entity, snapshot(x = 0f), acknowledgedSequence = 1)

        assertEquals(0f, entity.getComponent(TransformComponent::class.java).x, 0f)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `prediction remains responsive while server snapshot is delayed`() {
        val buffer = PredictedInputBuffer()
        val system = ClientPredictionSystem(buffer)
        val entity = localEntity(x = 0f)
        entity.add(game.shared.ecs.component.PlayerInputComponent().apply { state.setMovement(1f, 0f) })
        val engine = Engine().apply { addEntity(entity); addSystem(system) }

        repeat(3) { engine.update(0.05f) } // Artificial three-frame network delay before any snapshot arrives.

        assertTrue(entity.getComponent(TransformComponent::class.java).x > 0f)
        assertEquals(3, buffer.size)
    }

    @Test
    fun `small server displacement smoothly corrects local physics body`() {
        val world = PhysicsWorldFactory.create()
        try {
            val entity = localEntity(x = 1f).apply {
                add(PhysicsBodyComponent(PhysicsWorldFactory.createDynamicPlayerBody(world, 1f, 0f), false))
            }
            val system = ServerReconciliationSystem(
                PredictedInputBuffer(),
                snapDistance = 1.5f,
                correctionRate = 1000f,
            )
            val engine = Engine().apply { addEntity(entity); addSystem(system) }

            system.reconcile(entity, snapshot(x = 0.8f), acknowledgedSequence = 0)
            engine.update(0.1f)

            assertEquals(0.8f, entity.getComponent(TransformComponent::class.java).x, 0.001f)
            assertEquals(0.8f, entity.getComponent(PhysicsBodyComponent::class.java).body.position.x, 0.001f)
            assertTrue(!entity.getComponent(PhysicsBodyComponent::class.java).synchronizeTransformToBody)
        } finally {
            world.dispose()
        }
    }

    @Test
    fun `physics correction replays unacknowledged input from authoritative position`() {
        val world = PhysicsWorldFactory.create()
        try {
            val buffer = PredictedInputBuffer().apply { add(command(2), 0.1f) }
            val entity = localEntity(x = 0.6f).apply {
                add(PhysicsBodyComponent(PhysicsWorldFactory.createDynamicPlayerBody(world, 0.6f, 0f), false))
            }
            val system = ServerReconciliationSystem(buffer, snapDistance = 0.5f)

            system.reconcile(entity, snapshot(x = 0.2f), acknowledgedSequence = 1)

            assertEquals(0.6f, entity.getComponent(PhysicsBodyComponent::class.java).body.position.x, 0.001f)
            assertEquals(listOf(2L), buffer.entries().map { it.command.inputSequence })
        } finally {
            world.dispose()
        }
    }

    private fun localEntity(x: Float): Entity = Entity().apply {
        add(LocalPlayerComponent())
        add(TransformComponent(x = x))
        add(VelocityComponent())
        add(MovementSpeedComponent(GameConstants.PLAYER_MOVE_SPEED))
        add(CharacterStateComponent())
    }

    private fun command(sequence: Long) = InputCommand(
        inputSequence = sequence,
        clientTick = sequence,
        moveX = 1f,
        moveY = 0f,
    )

    private fun snapshot(x: Float) = EntitySnapshot(
        entityId = 1,
        x = x,
        y = 0f,
        velocityX = 0f,
        velocityY = 0f,
        currentHealth = 100f,
        maxHealth = 100f,
        movementSpeed = GameConstants.PLAYER_MOVE_SPEED,
        characterState = CharacterState.ALIVE,
        collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
    )
}
