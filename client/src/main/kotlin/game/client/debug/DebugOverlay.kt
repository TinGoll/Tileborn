package game.client.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import java.util.Locale

/** Renders debug-only client state as a screen-space layer over the world. */
class DebugOverlay(
    private val snapshotBuilder: DebugOverlaySnapshotBuilder,
    private val toggleKeyJustPressed: () -> Boolean = { Gdx.input.isKeyJustPressed(Input.Keys.F3) },
    private val fpsProvider: () -> Int = { Gdx.graphics.framesPerSecond },
    private val renderer: DebugOverlayRenderer = GdxDebugOverlayRenderer(),
) : Disposable {
    var visible: Boolean = true
        private set

    fun render() {
        if (toggleKeyJustPressed()) {
            visible = !visible
        }
        if (!visible) return

        renderer.render(format(snapshotBuilder.build(fpsProvider())))
    }

    override fun dispose() = renderer.dispose()

    private fun format(snapshot: DebugOverlaySnapshot): List<String> {
        val playerPosition = if (snapshot.localPlayerX == null || snapshot.localPlayerY == null) {
            "none"
        } else {
            String.format(Locale.US, "%.2f, %.2f", snapshot.localPlayerX, snapshot.localPlayerY)
        }

        return listOf(
            "FPS: ${snapshot.fps}",
            "Player: $playerPosition",
            "Entities: ${snapshot.entityCount}",
            "Map: ${snapshot.mapId}",
            "Connection: ${snapshot.connectionState.name.lowercase(Locale.US)}",
            "Ping: ${snapshot.pingMillis?.let { "$it ms" } ?: "n/a"}",
        )
    }
}

interface DebugOverlayRenderer : Disposable {
    fun render(lines: List<String>)
}

private class GdxDebugOverlayRenderer : DebugOverlayRenderer {
    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val camera = OrthographicCamera()

    init {
        font.color = Color.WHITE
    }

    override fun render(lines: List<String>) {
        if (Gdx.graphics.width <= 0 || Gdx.graphics.height <= 0) return

        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        batch.projectionMatrix = camera.combined
        batch.begin()
        var y = Gdx.graphics.height - TOP_PADDING
        for (line in lines) {
            font.draw(batch, line, LEFT_PADDING, y)
            y -= LINE_HEIGHT
        }
        batch.end()
    }

    override fun dispose() {
        font.dispose()
        batch.dispose()
    }

    private companion object {
        const val LEFT_PADDING = 12f
        const val TOP_PADDING = 12f
        const val LINE_HEIGHT = 18f
    }
}
