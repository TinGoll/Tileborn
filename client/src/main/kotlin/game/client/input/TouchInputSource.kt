package game.client.input

import game.shared.input.GameInputState

/** Android placeholder until on-screen controls are introduced. */
class TouchInputSource : GameInputSource {
    override fun update(state: GameInputState) {
        state.setMovement(0f, 0f)
        state.attack = false
        state.interact = false
        state.aimX = 0f
        state.aimY = 0f
    }
}
