package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.client.ecs.component.LocalPlayerComponent
import game.client.network.PredictedInputBuffer
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.protocol.EntitySnapshot
import kotlin.math.exp
import kotlin.math.sqrt

/** Replays unacknowledged local input from server truth and smooths only small correction errors. */
class ServerReconciliationSystem(
    private val predictedInputs: PredictedInputBuffer,
    private val snapDistance: Float = DEFAULT_SNAP_DISTANCE,
    private val correctionRate: Float = DEFAULT_CORRECTION_RATE,
) : IteratingSystem(FAMILY, PRIORITY) {
    private var remainingCorrectionX = 0f
    private var remainingCorrectionY = 0f

    init {
        require(snapDistance > 0f) { "Snap distance must be positive." }
        require(correctionRate > 0f) { "Correction rate must be positive." }
    }

    fun reconcile(entity: Entity, authoritative: EntitySnapshot, acknowledgedSequence: Long) {
        val transform = TRANSFORM_MAPPER.get(entity)
        val velocity = VELOCITY_MAPPER.get(entity)
        val previousX = transform.x
        val previousY = transform.y
        predictedInputs.acknowledge(acknowledgedSequence)

        if (STATE_MAPPER.get(entity).state != CharacterState.ALIVE) {
            predictedInputs.clear()
            velocity.x = 0f
            velocity.y = 0f
            transform.x = authoritative.x
            transform.y = authoritative.y
            PHYSICS_BODY_MAPPER.get(entity)?.synchronizeTransformToBody = true
            remainingCorrectionX = 0f
            remainingCorrectionY = 0f
            return
        }

        val unacknowledgedInputs = predictedInputs.entries()
        velocity.x = authoritative.velocityX
        velocity.y = authoritative.velocityY
        PHYSICS_BODY_MAPPER.get(entity)?.let { physics ->
            var replayedX = authoritative.x
            var replayedY = authoritative.y
            val movementSpeed = SPEED_MAPPER.get(entity).movementSpeed
            unacknowledgedInputs.forEach { entry ->
                replayedX += entry.command.moveX * movementSpeed * entry.deltaTime
                replayedY += entry.command.moveY * movementSpeed * entry.deltaTime
            }
            val correctionX = replayedX - physics.body.position.x
            val correctionY = replayedY - physics.body.position.y
            val correctionDistance = sqrt(correctionX * correctionX + correctionY * correctionY)
            unacknowledgedInputs.lastOrNull()?.command?.let { command ->
                velocity.x = command.moveX * movementSpeed
                velocity.y = command.moveY * movementSpeed
            }
            if (correctionDistance >= snapDistance) {
                setPhysicsPosition(physics, transform, replayedX, replayedY)
                remainingCorrectionX = 0f
                remainingCorrectionY = 0f
            } else {
                // Player-player contacts exist only in the authoritative server world. Keep small
                // server displacements instead of discarding them, then converge the local body
                // smoothly so the pushed player cannot remain permanently desynchronized.
                remainingCorrectionX = correctionX
                remainingCorrectionY = correctionY
            }
            return
        }

        transform.x = authoritative.x
        transform.y = authoritative.y
        unacknowledgedInputs.forEach { entry ->
            ClientPredictionSystem.apply(
                entry.command,
                entry.deltaTime,
                transform,
                velocity,
                SPEED_MAPPER.get(entity).movementSpeed,
            )
        }

        val correctionX = transform.x - previousX
        val correctionY = transform.y - previousY
        if (sqrt(correctionX * correctionX + correctionY * correctionY) >= snapDistance) {
            remainingCorrectionX = 0f
            remainingCorrectionY = 0f
            return
        }
        // Preserve the current visual position and converge to the replayed one across subsequent frames.
        transform.x = previousX
        transform.y = previousY
        remainingCorrectionX = correctionX
        remainingCorrectionY = correctionY
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val fraction = 1f - exp((-correctionRate * deltaTime.coerceAtLeast(0f)).toDouble()).toFloat()
        val moveX = remainingCorrectionX * fraction
        val moveY = remainingCorrectionY * fraction
        val transform = TRANSFORM_MAPPER.get(entity)
        PHYSICS_BODY_MAPPER.get(entity)?.let { physics ->
            setPhysicsPosition(
                physics,
                transform,
                physics.body.position.x + moveX,
                physics.body.position.y + moveY,
            )
        } ?: transform.let {
            transform.x += moveX
            transform.y += moveY
        }
        remainingCorrectionX -= moveX
        remainingCorrectionY -= moveY
    }

    private fun setPhysicsPosition(
        physics: PhysicsBodyComponent,
        transform: TransformComponent,
        x: Float,
        y: Float,
    ) {
        physics.body.setTransform(x, y, physics.body.angle)
        physics.previousX = x
        physics.previousY = y
        transform.x = x
        transform.y = y
        physics.synchronizeTransformToBody = false
    }

    private companion object {
        const val PRIORITY = 175
        const val DEFAULT_SNAP_DISTANCE = 1.5f
        const val DEFAULT_CORRECTION_RATE = 12f
        val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        val PHYSICS_BODY_MAPPER = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
        val SPEED_MAPPER = ComponentMapper.getFor(MovementSpeedComponent::class.java)
        val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        val FAMILY: Family = Family.all(
            LocalPlayerComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
            MovementSpeedComponent::class.java,
            CharacterStateComponent::class.java,
        ).get()
    }
}
