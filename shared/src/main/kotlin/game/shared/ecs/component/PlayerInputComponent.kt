package game.shared.ecs.component

import com.badlogic.ashley.core.Component
import game.shared.input.GameInputState

/** Latest input intent for a player. The server may populate the same model from network commands. */
class PlayerInputComponent(
    val state: GameInputState = GameInputState(),
) : Component
