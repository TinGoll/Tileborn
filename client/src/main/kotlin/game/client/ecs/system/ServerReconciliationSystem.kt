package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.client.ecs.component.LocalPlayerComponent
import game.client.network.PredictedInputBuffer
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
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

        transform.x = authoritative.x
        transform.y = authoritative.y
        velocity.x = authoritative.velocityX
        velocity.y = authoritative.velocityY
        predictedInputs.entries().forEach { entry ->
            ClientPredictionSystem.apply(entry.command, entry.deltaTime, transform, velocity)
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
        TRANSFORM_MAPPER.get(entity).let { transform ->
            transform.x += moveX
            transform.y += moveY
        }
        remainingCorrectionX -= moveX
        remainingCorrectionY -= moveY
    }

    private companion object {
        const val PRIORITY = 175
        const val DEFAULT_SNAP_DISTANCE = 1.5f
        const val DEFAULT_CORRECTION_RATE = 12f
        val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        val FAMILY: Family = Family.all(
            LocalPlayerComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
        ).get()
    }
}
