package game.client.screens

import com.badlogic.gdx.Gdx
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import game.client.debug.DebugOverlay
import game.client.debug.DebugOverlaySnapshotBuilder
import game.client.ecs.ClientEcsWorld
import game.client.ecs.ClientRenderEntityFactory
import game.client.ecs.system.CameraFollowSystem
import game.client.ecs.system.MapRenderSystem
import game.client.ecs.system.PrimitiveRenderSystem
import game.client.network.GameNetworkClient
import game.client.network.NoopGameNetworkClient
import game.shared.map.GameMapData
import game.shared.map.TiledGameplayMapParser
import game.shared.math.WorldUnits
import game.shared.physics.TiledCollisionLoader
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely

class GameScreen(
    internal val assets: GameAssetManager,
    internal val ecsWorld: ClientEcsWorld,
    private val networkClient: GameNetworkClient = NoopGameNetworkClient,
) : KtxScreen {
    private val camera = OrthographicCamera()
    private var mapRenderer: OrthogonalTiledMapRenderer? = null
    internal var mapData: GameMapData? = null
        private set
    private var cameraFollowSystem: CameraFollowSystem? = null
    private var mapRenderSystem: MapRenderSystem? = null
    private var primitiveRenderSystem: PrimitiveRenderSystem? = null
    private var physicsDebugRenderer: Box2DDebugRenderer? = null
    private var debugOverlay: DebugOverlay? = null
    private val collisionBodies = mutableListOf<Body>()
    private val screenEntities = mutableListOf<Entity>()

    init {
        check(assets.isFinished()) {
            "GameScreen requires a fully loaded GameAssetManager"
        }
    }

    override fun show() {
        if (mapRenderer == null) {
            networkClient.connect()
            val tiledMap = assets.get(AssetDescriptors.DEBUG_MAP)
            mapData = TiledGameplayMapParser { message ->
                Gdx.app?.error("GameScreen", message)
            }.parse(AssetDescriptors.DEBUG_MAP_ID, tiledMap)
            mapRenderer = OrthogonalTiledMapRenderer(
                tiledMap,
                WorldUnits.pixelsToMeters(1f),
            )

            val spawn = mapData!!.requireSpawnPoint("default")
            collisionBodies += TiledCollisionLoader(ecsWorld.physicsWorld).load(mapData!!)
            screenEntities += ClientRenderEntityFactory.createDebugCollisionGeometry(ecsWorld.engine, mapData!!)
            screenEntities += ClientRenderEntityFactory.createTestPlayer(
                ecsWorld.engine,
                ecsWorld.physicsWorld,
                spawn.x,
                spawn.y,
            )

            cameraFollowSystem = CameraFollowSystem(camera).also(ecsWorld.engine::addSystem)
            mapRenderSystem = MapRenderSystem(camera, mapRenderer!!).also(ecsWorld.engine::addSystem)
            primitiveRenderSystem = PrimitiveRenderSystem(camera).also(ecsWorld.engine::addSystem)
            physicsDebugRenderer = Box2DDebugRenderer()
            debugOverlay = DebugOverlay(
                DebugOverlaySnapshotBuilder(
                    engine = ecsWorld.engine,
                    mapIdProvider = { mapData?.mapId },
                    connectionStateProvider = { networkClient.connectionState },
                ),
            )
            cameraFollowSystem?.update(0f)
            resize(Gdx.graphics.width, Gdx.graphics.height)
        }
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        ecsWorld.engine.update(delta)
        physicsDebugRenderer?.render(ecsWorld.physicsWorld, camera.combined)
        debugOverlay?.render()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        camera.viewportWidth = WorldUnits.pixelsToMeters(width.toFloat())
        camera.viewportHeight = WorldUnits.pixelsToMeters(height.toFloat())
        camera.update()
    }

    override fun dispose() {
        screenEntities.forEach(ecsWorld.engine::removeEntity)
        screenEntities.clear()
        collisionBodies.forEach(ecsWorld.physicsWorld::destroyBody)
        collisionBodies.clear()
        cameraFollowSystem?.let(ecsWorld.engine::removeSystem)
        cameraFollowSystem = null
        mapRenderSystem?.let(ecsWorld.engine::removeSystem)
        mapRenderSystem = null
        primitiveRenderSystem?.let {
            ecsWorld.engine.removeSystem(it)
            it.dispose()
        }
        primitiveRenderSystem = null
        physicsDebugRenderer.disposeSafely()
        physicsDebugRenderer = null
        debugOverlay.disposeSafely()
        debugOverlay = null
        networkClient.close()
        mapRenderer.disposeSafely()
        mapRenderer = null
        mapData = null
    }
}
