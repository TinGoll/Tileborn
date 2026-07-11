package game.shared.input

import kotlin.math.sqrt
import game.shared.input.InputCommandValidator
import game.shared.protocol.InputCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class GameInputStateTest {
    @Test
    fun `diagonal movement is normalized`() {
        val state = GameInputState()

        state.setMovement(1f, 1f)

        val expectedAxis = 1f / sqrt(2f)
        assertEquals(expectedAxis, state.moveX, 0.0001f)
        assertEquals(expectedAxis, state.moveY, 0.0001f)
        assertEquals(1f, state.moveX * state.moveX + state.moveY * state.moveY, 0.0001f)
    }

    @Test
    fun `input command movement is sanitized and normalized`() {
        val state = InputCommandValidator.toInputState(
            InputCommand(
                inputSequence = 1L,
                clientTick = 2L,
                moveX = Float.POSITIVE_INFINITY,
                moveY = 2f,
                aimX = Float.NaN,
                aimY = -2f,
            ),
        )

        assertEquals(0f, state.moveX, 0f)
        assertEquals(1f, state.moveY, 0f)
        assertEquals(0f, state.aimX, 0f)
        assertEquals(-1f, state.aimY, 0f)
    }
}
