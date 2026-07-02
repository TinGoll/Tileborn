package game.client.input

import game.shared.input.GameInputState

/** Converts a platform input device into shared gameplay intent. */
fun interface GameInputSource {
    fun update(state: GameInputState)
}
