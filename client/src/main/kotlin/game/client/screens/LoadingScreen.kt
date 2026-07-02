package game.client.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely

class LoadingScreen(
    private val assets: GameAssetManager,
    private val onFinished: () -> Unit,
) : KtxScreen {
    private var renderer: ShapeRenderer? = null
    private var assetsQueued = false
    private var transitionStarted = false
    private var loadingError: RuntimeException? = null

    override fun show() {
        if (renderer == null) {
            renderer = ShapeRenderer()
        }
        queueAssetsOnce()
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.08f, green = 0.09f, blue = 0.12f)

        if (advanceLoading()) {
            return
        }

        renderProgress()
    }

    internal fun queueAssetsOnce() {
        if (assetsQueued || loadingError != null) return

        try {
            assets.queueCommonAssets()
            assets.queueMapAssets(AssetDescriptors.DEBUG_MAP_ID)
            assetsQueued = true
        } catch (exception: RuntimeException) {
            recordLoadingError(exception)
        }
    }

    internal fun advanceLoading(): Boolean {
        if (!assetsQueued || transitionStarted || loadingError != null) return false

        return try {
            if (assets.update()) {
                transitionStarted = true
                onFinished()
                true
            } else {
                false
            }
        } catch (exception: RuntimeException) {
            recordLoadingError(exception)
            false
        }
    }

    private fun renderProgress() {
        val shapeRenderer = renderer ?: return
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val barWidth = width * 0.7f
        val barHeight = 24f
        val x = (width - barWidth) * 0.5f
        val y = (height - barHeight) * 0.5f
        val progress = assets.getProgress().coerceIn(0f, 1f)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color.DARK_GRAY
        shapeRenderer.rect(x, y, barWidth, barHeight)
        shapeRenderer.color = if (loadingError == null) Color.SKY else Color.FIREBRICK
        shapeRenderer.rect(x, y, barWidth * if (loadingError == null) progress else 1f, barHeight)
        shapeRenderer.end()
    }

    private fun recordLoadingError(exception: RuntimeException) {
        loadingError = exception
        Gdx.app?.error(
            "LoadingScreen",
            "Asset loading failed; the game screen will not be opened.",
            exception,
        )
    }

    override fun dispose() {
        renderer.disposeSafely()
        renderer = null
    }
}
