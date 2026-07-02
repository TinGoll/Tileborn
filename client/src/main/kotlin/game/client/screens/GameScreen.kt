package game.client.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import game.shared.map.GameMapData
import game.shared.map.TiledGameplayMapParser
import game.shared.math.WorldUnits
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely

class GameScreen(
    internal val assets: GameAssetManager,
) : KtxScreen {
    private val camera = OrthographicCamera()
    private var mapRenderer: OrthogonalTiledMapRenderer? = null
    internal var mapData: GameMapData? = null
        private set

    init {
        check(assets.isFinished()) {
            "GameScreen requires a fully loaded GameAssetManager"
        }
    }

    override fun show() {
        if (mapRenderer == null) {
            val tiledMap = assets.get(AssetDescriptors.DEBUG_MAP)
            mapData = TiledGameplayMapParser { message ->
                Gdx.app?.error("GameScreen", message)
            }.parse(AssetDescriptors.DEBUG_MAP_ID, tiledMap)
            mapRenderer = OrthogonalTiledMapRenderer(
                tiledMap,
                WorldUnits.pixelsToMeters(1f),
            )

            camera.position.set(5f, 5f, 0f)
            resize(Gdx.graphics.width, Gdx.graphics.height)
        }
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        camera.update()
        mapRenderer?.setView(camera)
        mapRenderer?.render()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        camera.viewportWidth = WorldUnits.pixelsToMeters(width.toFloat())
        camera.viewportHeight = WorldUnits.pixelsToMeters(height.toFloat())
        camera.update()
    }

    override fun dispose() {
        mapRenderer.disposeSafely()
        mapRenderer = null
        mapData = null
    }
}
