package game.client.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import game.shared.input.GameInputState

/** Desktop keyboard mapping. Device-specific keys do not escape this class. */
class KeyboardInputSource(
    private val keyPressed: (Int) -> Boolean = { key -> Gdx.input.isKeyPressed(key) },
) : GameInputSource {
    override fun update(state: GameInputState) {
        val left = keyPressed(Input.Keys.A) || keyPressed(Input.Keys.LEFT)
        val right = keyPressed(Input.Keys.D) || keyPressed(Input.Keys.RIGHT)
        val down = keyPressed(Input.Keys.S) || keyPressed(Input.Keys.DOWN)
        val up = keyPressed(Input.Keys.W) || keyPressed(Input.Keys.UP)

        state.setMovement(
            x = direction(negative = left, positive = right),
            y = direction(negative = down, positive = up),
        )
        state.attack = keyPressed(Input.Keys.SPACE)
        state.interact = keyPressed(Input.Keys.E)
        state.aimX = 0f
        state.aimY = 0f
    }

    private fun direction(negative: Boolean, positive: Boolean): Float =
        positive.compareTo(negative).toFloat()
}
