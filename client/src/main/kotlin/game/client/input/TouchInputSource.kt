package game.client.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Disposable
import game.shared.input.GameInputState
import kotlin.math.sqrt

/** A currently active touch, expressed in bottom-left screen coordinates. */
data class TouchPoint(
    val pointer: Int,
    val x: Float,
    val y: Float,
)

/** Isolates libGDX polling so touch-direction conversion can be tested without an Android device. */
fun interface TouchInputReader {
    fun touches(): List<TouchPoint>
}

/** Screen-space geometry for the scalable touch controls. */
data class TouchControlLayout(
    val width: Float,
    val height: Float,
    val joystickCenterX: Float,
    val joystickCenterY: Float,
    val joystickRadius: Float,
    val attackCenterX: Float,
    val attackCenterY: Float,
    val interactCenterX: Float,
    val interactCenterY: Float,
    val buttonRadius: Float,
) {
    companion object {
        fun forScreen(width: Int, height: Int): TouchControlLayout {
            val safeWidth = width.coerceAtLeast(1).toFloat()
            val safeHeight = height.coerceAtLeast(1).toFloat()
            val shortestSide = minOf(safeWidth, safeHeight)
            val joystickRadius = (shortestSide * 0.15f).coerceIn(48f, 120f)
            val buttonRadius = (shortestSide * 0.105f).coerceIn(42f, 92f)
            val margin = (shortestSide * 0.045f).coerceAtLeast(16f)
            val rightX = safeWidth - margin - buttonRadius
            val bottomY = margin + buttonRadius

            return TouchControlLayout(
                width = safeWidth,
                height = safeHeight,
                joystickCenterX = margin + joystickRadius,
                joystickCenterY = margin + joystickRadius,
                joystickRadius = joystickRadius,
                attackCenterX = rightX,
                attackCenterY = bottomY,
                interactCenterX = rightX,
                interactCenterY = bottomY + buttonRadius * 2.35f,
                buttonRadius = buttonRadius,
            )
        }
    }
}

/**
 * Converts a left virtual joystick and two right-side buttons into shared gameplay intent.
 *
 * The control layout is screen-relative, so it retains usable physical size across common
 * Android landscape resolutions.  It deliberately emits the same [GameInputState] as keyboard
 * input; no gameplay system needs to know which device produced it.
 */
class TouchInputSource(
    private val touchReader: TouchInputReader = GdxTouchInputReader,
    private val screenSize: () -> Pair<Int, Int> = { Gdx.graphics.width to Gdx.graphics.height },
) : GameInputSource, TouchControlsOverlay {
    override fun update(state: GameInputState) {
        val (width, height) = screenSize()
        val layout = TouchControlLayout.forScreen(width, height)
        val touches = touchReader.touches()
        val joystickTouch = touches.firstOrNull { layout.isInJoystickActivationArea(it) }

        if (joystickTouch == null) {
            state.setMovement(0f, 0f)
        } else {
            val direction = joystickDirection(
                joystickTouch.x - layout.joystickCenterX,
                joystickTouch.y - layout.joystickCenterY,
                layout.joystickRadius,
            )
            state.setMovement(direction.first, direction.second)
        }
        state.attack = touches.any { layout.isInside(it.x, it.y, layout.attackCenterX, layout.attackCenterY, layout.buttonRadius) }
        state.interact = touches.any { layout.isInside(it.x, it.y, layout.interactCenterX, layout.interactCenterY, layout.buttonRadius) }
        state.aimX = 0f
        state.aimY = 0f
    }

    override fun render() {
        val (width, height) = screenSize()
        if (width <= 0 || height <= 0) return
        TouchControlsRenderer.render(TouchControlLayout.forScreen(width, height), touchReader.touches())
    }

    override fun dispose() {
        TouchControlsRenderer.dispose()
    }

    private fun joystickDirection(deltaX: Float, deltaY: Float, radius: Float): Pair<Float, Float> {
        val length = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (length <= radius * JOYSTICK_DEAD_ZONE) return 0f to 0f
        val scale = (length / radius).coerceAtMost(1f) / length
        return deltaX * scale to deltaY * scale
    }

    private fun TouchControlLayout.isInJoystickActivationArea(touch: TouchPoint): Boolean =
        isInside(touch.x, touch.y, joystickCenterX, joystickCenterY, joystickRadius * JOYSTICK_ACTIVATION_MULTIPLIER)

    private fun TouchControlLayout.isInside(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float): Boolean {
        val deltaX = x - centerX
        val deltaY = y - centerY
        return deltaX * deltaX + deltaY * deltaY <= radius * radius
    }

    private companion object {
        const val JOYSTICK_DEAD_ZONE = 0.12f
        const val JOYSTICK_ACTIVATION_MULTIPLIER = 1.35f
    }
}

/** Optional UI surface used by [game.client.screens.GameScreen], not by gameplay systems. */
interface TouchControlsOverlay : Disposable {
    fun render()
    override fun dispose() = Unit
}

private object GdxTouchInputReader : TouchInputReader {
    override fun touches(): List<TouchPoint> = buildList {
        for (pointer in 0 until Gdx.input.maxPointers) {
            if (Gdx.input.isTouched(pointer)) {
                add(TouchPoint(pointer, Gdx.input.getX(pointer).toFloat(), Gdx.graphics.height - Gdx.input.getY(pointer).toFloat()))
            }
        }
    }
}

private object TouchControlsRenderer {
    private val renderer = ShapeRenderer()
    private val camera = OrthographicCamera()

    fun render(layout: TouchControlLayout, touches: List<TouchPoint>) {
        camera.setToOrtho(false, layout.width, layout.height)
        renderer.projectionMatrix = camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        renderer.begin(ShapeRenderer.ShapeType.Filled)
        circle(layout.joystickCenterX, layout.joystickCenterY, layout.joystickRadius, Color(0.16f, 0.45f, 0.88f, 0.24f))
        circle(layout.attackCenterX, layout.attackCenterY, layout.buttonRadius, Color(0.88f, 0.22f, 0.18f, 0.42f))
        circle(layout.interactCenterX, layout.interactCenterY, layout.buttonRadius, Color(0.95f, 0.62f, 0.12f, 0.42f))
        touches.firstOrNull { layout.isInJoystickActivationArea(it) }?.let { touch ->
            val deltaX = touch.x - layout.joystickCenterX
            val deltaY = touch.y - layout.joystickCenterY
            val length = sqrt(deltaX * deltaX + deltaY * deltaY).coerceAtLeast(1f)
            val scale = (layout.joystickRadius / length).coerceAtMost(1f)
            circle(layout.joystickCenterX + deltaX * scale, layout.joystickCenterY + deltaY * scale, layout.joystickRadius * 0.38f, Color(0.8f, 0.9f, 1f, 0.65f))
        }
        renderer.end()
        renderer.begin(ShapeRenderer.ShapeType.Line)
        renderer.color = Color(0.75f, 0.88f, 1f, 0.7f)
        renderer.circle(layout.joystickCenterX, layout.joystickCenterY, layout.joystickRadius)
        renderer.color = Color(1f, 0.8f, 0.75f, 0.85f)
        renderer.circle(layout.attackCenterX, layout.attackCenterY, layout.buttonRadius)
        renderer.color = Color(1f, 0.92f, 0.7f, 0.85f)
        renderer.circle(layout.interactCenterX, layout.interactCenterY, layout.buttonRadius)
        renderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun circle(x: Float, y: Float, radius: Float, color: Color) {
        renderer.color = color
        renderer.circle(x, y, radius)
    }

    fun dispose() = renderer.dispose()

    private fun TouchControlLayout.isInJoystickActivationArea(touch: TouchPoint): Boolean {
        val deltaX = touch.x - joystickCenterX
        val deltaY = touch.y - joystickCenterY
        val activationRadius = joystickRadius * 1.35f
        return deltaX * deltaX + deltaY * deltaY <= activationRadius * activationRadius
    }
}
