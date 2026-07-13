package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.client.ecs.component.LocalPlayerComponent
import game.client.network.PredictedInputBuffer
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.protocol.InputCommand

/** Applies local intent immediately and records the exact commands needed for reconciliation. */
class ClientPredictionSystem(
    private val predictedInputs: PredictedInputBuffer,
) : IteratingSystem(FAMILY, PRIORITY) {
    private val outgoingCommands = ArrayDeque<InputCommand>()
    private var nextInputSequence = 0L
    private var clientTick = 0L

    fun drainOutgoingCommands(): List<InputCommand> = buildList {
        while (outgoingCommands.isNotEmpty()) add(outgoingCommands.removeFirst())
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val input = INPUT_MAPPER.get(entity).state
        val isAlive = STATE_MAPPER.get(entity).state == CharacterState.ALIVE
        if (!isAlive) {
            input.moveX = 0f
            input.moveY = 0f
            input.attack = false
        }
        val command = InputCommand(
            inputSequence = nextInputSequence++,
            clientTick = clientTick++,
            moveX = input.moveX,
            moveY = input.moveY,
            attack = input.attack,
            interact = input.interact,
            aimX = input.aimX,
            aimY = input.aimY,
        )
        apply(
            command,
            deltaTime.coerceAtLeast(0f),
            TRANSFORM_MAPPER.get(entity),
            VELOCITY_MAPPER.get(entity),
            SPEED_MAPPER.get(entity).movementSpeed,
        )
        predictedInputs.add(command, deltaTime.coerceAtLeast(0f))
        outgoingCommands.addLast(command)
    }

    companion object {
        const val PRIORITY = 125

        fun apply(
            command: InputCommand,
            deltaTime: Float,
            transform: TransformComponent,
            velocity: VelocityComponent,
            movementSpeed: Float,
        ) {
            val safeSpeed = movementSpeed.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
            velocity.x = command.moveX * safeSpeed
            velocity.y = command.moveY * safeSpeed
            transform.x += velocity.x * deltaTime
            transform.y += velocity.y * deltaTime
        }

        private val INPUT_MAPPER = ComponentMapper.getFor(PlayerInputComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        private val SPEED_MAPPER = ComponentMapper.getFor(MovementSpeedComponent::class.java)
        private val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        private val FAMILY: Family = Family.all(
            LocalPlayerComponent::class.java,
            PlayerInputComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
            MovementSpeedComponent::class.java,
            CharacterStateComponent::class.java,
        ).get()
    }
}
