package game.client.input

import com.badlogic.gdx.Input
import game.shared.input.GameInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardInputSourceTest {
    @Test
    fun `up key produces positive move y`() {
        val source = sourceWith(Input.Keys.W)
        val state = GameInputState()

        source.update(state)

        assertTrue(state.moveY > 0f)
        assertEquals(0f, state.moveX, 0f)
    }

    @Test
    fun `opposite directions cancel without invalid speed`() {
        val source = sourceWith(Input.Keys.A, Input.Keys.D, Input.Keys.W, Input.Keys.S)
        val state = GameInputState()

        source.update(state)

        assertEquals(0f, state.moveX, 0f)
        assertEquals(0f, state.moveY, 0f)
    }

    private fun sourceWith(vararg keys: Int): KeyboardInputSource {
        val pressed = keys.toSet()
        return KeyboardInputSource { it in pressed }
    }
}
