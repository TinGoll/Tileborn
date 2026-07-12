package game.client

import game.client.assets.GameAssetManager
import game.client.ecs.ClientEcsWorld
import game.client.input.GameInputSource
import game.client.input.KeyboardInputSource
import game.client.network.GameNetworkClient
import game.client.network.TcpGameClient
import game.client.screens.GameScreen
import game.client.screens.LoadingScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen

class Main(
    private val inputSource: GameInputSource = KeyboardInputSource(),
    private val networkClient: GameNetworkClient = TcpGameClient(),
) : KtxGame<KtxScreen>() {
    private lateinit var assets: GameAssetManager
    private lateinit var ecsWorld: ClientEcsWorld

    override fun create() {
        assets = GameAssetManager()
        ecsWorld = ClientEcsWorld(inputSource)
        addScreen(LoadingScreen(assets) { showGameScreen() })
        setScreen<LoadingScreen>()
    }

    private fun showGameScreen() {
        addScreen(GameScreen(assets, ecsWorld, networkClient))
        setScreen<GameScreen>()
        removeScreen<LoadingScreen>()?.dispose()
    }

    override fun dispose() {
        super.dispose()
        if (::ecsWorld.isInitialized) {
            ecsWorld.dispose()
        }
        if (::assets.isInitialized) {
            assets.dispose()
        }
        networkClient.close()
    }

    override fun pause() {
        networkClient.onApplicationPaused()
        super.pause()
    }

    override fun resume() {
        super.resume()
        networkClient.onApplicationResumed()
    }
}
