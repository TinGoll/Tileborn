package game.client

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import game.client.ecs.ClientEcsWorld
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely
import ktx.graphics.use

class Main : KtxGame<KtxScreen>() {
    private lateinit var assets: GameAssetManager
    private lateinit var ecsWorld: ClientEcsWorld

    override fun create() {
        assets = GameAssetManager()
        ecsWorld = ClientEcsWorld()
        assets.queueCommonAssets()
        assets.queueMapAssets(AssetDescriptors.DEBUG_MAP_ID)
        addScreen(LoadingScreen(assets) { showGameScreen() })
        setScreen<LoadingScreen>()
    }

    private fun showGameScreen() {
        addScreen(FirstScreen(assets))
        setScreen<FirstScreen>()
        removeScreen<LoadingScreen>()?.dispose()
    }

    override fun dispose() {
        super.dispose()
        if (::ecsWorld.isInitialized) {
            ecsWorld.engine.removeAllEntities()
        }
        if (::assets.isInitialized) {
            assets.dispose()
        }
    }
}

class LoadingScreen(
    private val assets: GameAssetManager,
    private val onFinished: () -> Unit,
) : KtxScreen {
    private var transitionStarted = false

    override fun render(delta: Float) {
        clearScreen(red = 0.1f, green = 0.1f, blue = 0.1f)
        if (!transitionStarted && assets.update()) {
            transitionStarted = true
            onFinished()
        }
    }
}

class FirstScreen(assets: GameAssetManager) : KtxScreen {
    private val image = assets.get(AssetDescriptors.LOGO)
    private val batch = SpriteBatch()

    override fun render(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        batch.use {
            it.draw(image, 100f, 160f)
        }
    }

    override fun dispose() {
        batch.disposeSafely()
    }
}
