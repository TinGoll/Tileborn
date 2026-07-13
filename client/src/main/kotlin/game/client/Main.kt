package game.client

import game.client.assets.GameAssetManager
import game.client.ecs.ClientEcsWorld
import game.client.input.GameInputSource
import game.client.input.KeyboardInputSource
import game.client.input.TouchControlsOverlay
import game.client.network.GameNetworkClient
import game.client.network.TcpGameClient
import game.client.screens.GameScreen
import game.client.screens.JoinScreen
import game.client.screens.LoadingScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen

class Main(
    private val inputSource: GameInputSource = KeyboardInputSource(),
    private val networkClientFactory: (String) -> GameNetworkClient = { nickname ->
        TcpGameClient(playerName = nickname)
    },
) : KtxGame<KtxScreen>() {
    private lateinit var assets: GameAssetManager
    private lateinit var ecsWorld: ClientEcsWorld
    private var networkClient: GameNetworkClient? = null

    override fun create() {
        assets = GameAssetManager()
        ecsWorld = ClientEcsWorld(inputSource)
        addScreen(LoadingScreen(assets) { showJoinScreen() })
        setScreen<LoadingScreen>()
    }

    private fun showJoinScreen() {
        addScreen(JoinScreen(networkClientFactory, ::showGameScreen))
        setScreen<JoinScreen>()
        removeScreen<LoadingScreen>()?.dispose()
    }

    private fun showGameScreen(client: GameNetworkClient) {
        networkClient = client
        addScreen(GameScreen(assets, ecsWorld, client, inputSource as? TouchControlsOverlay))
        setScreen<GameScreen>()
        removeScreen<JoinScreen>()?.dispose()
    }

    override fun dispose() {
        super.dispose()
        if (::ecsWorld.isInitialized) {
            ecsWorld.dispose()
        }
        if (::assets.isInitialized) {
            assets.dispose()
        }
        networkClient?.close()
    }

    override fun pause() {
        networkClient?.onApplicationPaused()
        super.pause()
    }

    override fun resume() {
        super.resume()
        networkClient?.onApplicationResumed()
    }
}
