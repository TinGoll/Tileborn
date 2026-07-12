package game.client.input

import game.shared.input.GameInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class TouchInputSourceTest {
    private val screenWidth = 1920
    private val screenHeight = 1080
    private val layout = TouchControlLayout.forScreen(screenWidth, screenHeight)

    @Test
    fun `joystick touch produces normalized shared movement intent`() {
        val state = updateWith(
            TouchPoint(
                0,
                layout.joystickCenterX + layout.joystickRadius / sqrt(2f),
                layout.joystickCenterY + layout.joystickRadius / sqrt(2f),
            ),
        )

        assertEquals(1f / sqrt(2f), state.moveX, 0.0001f)
        assertEquals(1f / sqrt(2f), state.moveY, 0.0001f)
        assertFalse(state.attack)
        assertFalse(state.interact)
    }

    @Test
    fun `buttons and joystick may be used with separate touches`() {
        val state = updateWith(
            TouchPoint(0, layout.joystickCenterX - layout.joystickRadius, layout.joystickCenterY),
            TouchPoint(1, layout.attackCenterX, layout.attackCenterY),
            TouchPoint(2, layout.interactCenterX, layout.interactCenterY),
        )

        assertEquals(-1f, state.moveX, 0f)
        assertEquals(0f, state.moveY, 0f)
        assertTrue(state.attack)
        assertTrue(state.interact)
    }

    @Test
    fun `touch outside controls clears a prior input state`() {
        val state = GameInputState(moveX = 1f, moveY = 1f, attack = true, interact = true)
        val source = TouchInputSource(
            touchReader = TouchInputReader { listOf(TouchPoint(0, layout.width / 2f, layout.height / 2f)) },
            screenSize = { screenWidth to screenHeight },
        )

        source.update(state)

        assertEquals(0f, state.moveX, 0f)
        assertEquals(0f, state.moveY, 0f)
        assertFalse(state.attack)
        assertFalse(state.interact)
    }

    private fun updateWith(vararg touches: TouchPoint): GameInputState {
        val source = TouchInputSource(
            touchReader = TouchInputReader { touches.toList() },
            screenSize = { screenWidth to screenHeight },
        )
        return GameInputState().also(source::update)
    }
}
