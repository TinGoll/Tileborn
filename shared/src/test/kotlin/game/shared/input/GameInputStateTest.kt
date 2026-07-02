package game.shared.input

import kotlin.math.sqrt
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
}
