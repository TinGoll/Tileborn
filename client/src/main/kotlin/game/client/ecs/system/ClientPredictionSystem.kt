package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.client.ecs.component.LocalPlayerComponent
import game.client.network.PredictedInputBuffer
import game.shared.constants.GameConstants
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
        apply(command, deltaTime.coerceAtLeast(0f), TRANSFORM_MAPPER.get(entity), VELOCITY_MAPPER.get(entity))
        predictedInputs.add(command, deltaTime.coerceAtLeast(0f))
        outgoingCommands.addLast(command)
    }

    companion object {
        const val PRIORITY = 125

        fun apply(command: InputCommand, deltaTime: Float, transform: TransformComponent, velocity: VelocityComponent) {
            velocity.x = command.moveX * GameConstants.PLAYER_MOVE_SPEED
            velocity.y = command.moveY * GameConstants.PLAYER_MOVE_SPEED
            transform.x += velocity.x * deltaTime
            transform.y += velocity.y * deltaTime
        }

        private val INPUT_MAPPER = ComponentMapper.getFor(PlayerInputComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        private val FAMILY: Family = Family.all(
            LocalPlayerComponent::class.java,
            PlayerInputComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
        ).get()
    }
}
