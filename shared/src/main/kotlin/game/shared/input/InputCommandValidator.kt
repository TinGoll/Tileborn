package game.shared.input

import game.shared.protocol.InputCommand

/** Converts untrusted network input into bounded gameplay intent. */
object InputCommandValidator {
    fun toInputState(command: InputCommand): GameInputState =
        GameInputState(
            attack = command.attack,
            interact = command.interact,
            aimX = command.aimX.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
            aimY = command.aimY.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
        ).apply {
            setMovement(command.moveX, command.moveY)
        }
}
