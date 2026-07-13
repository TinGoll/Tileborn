package game.client.screens

import com.badlogic.gdx.Gdx
import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import game.client.debug.DebugOverlay
import game.client.debug.DebugOverlaySnapshotBuilder
import game.client.debug.ConnectionState
import game.client.ecs.ClientEcsWorld
import game.client.ecs.ClientEntityRegistry
import game.client.ecs.ClientRenderEntityFactory
import game.client.ecs.CharacterSnapshotApplier
import game.client.ecs.system.CameraFollowSystem
import game.client.ecs.system.MapRenderSystem
import game.client.ecs.system.PrimitiveRenderSystem
import game.client.network.GameNetworkClient
import game.client.network.NoopGameNetworkClient
import game.client.input.TouchControlsOverlay
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.map.GameMapData
import game.shared.map.TiledGameplayMapParser
import game.shared.map.MapInteractable
import game.shared.map.MapInteractableType
import game.shared.math.WorldUnits
import game.shared.physics.TiledCollisionLoader
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.InteractCommand
import game.shared.protocol.WorldSnapshot
import ktx.app.KtxScreen
import ktx.app.clearScreen
import ktx.assets.disposeSafely

class GameScreen(
    internal val assets: GameAssetManager,
    internal val ecsWorld: ClientEcsWorld,
    private val networkClient: GameNetworkClient = NoopGameNetworkClient,
    private val touchControls: TouchControlsOverlay? = null,
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
    private val networkEntities = ClientEntityRegistry()
    private var appliedSnapshot: WorldSnapshot? = null
    private var nextInteractionSequence = 1L
    private var wasInteractPressed = false
    private var lastGameEvent: String? = null

    init {
        check(assets.isFinished()) {
            "GameScreen requires a fully loaded GameAssetManager"
        }
    }

    override fun show() {
        if (mapRenderer == null) {
            if (networkClient.connectionState == ConnectionState.DISCONNECTED) networkClient.connect()
            val tiledMap = assets.get(AssetDescriptors.DEBUG_MAP)
            mapData = TiledGameplayMapParser { message ->
                Gdx.app?.error("GameScreen", message)
            }.parse(AssetDescriptors.DEBUG_MAP_ID, tiledMap)
            mapRenderer = OrthogonalTiledMapRenderer(
                tiledMap,
                WorldUnits.pixelsToMeters(1f),
            )

            collisionBodies += TiledCollisionLoader(ecsWorld.physicsWorld).load(mapData!!)
            screenEntities += ClientRenderEntityFactory.createDebugCollisionGeometry(ecsWorld.engine, mapData!!)

            cameraFollowSystem = CameraFollowSystem(camera).also(ecsWorld.engine::addSystem)
            mapRenderSystem = MapRenderSystem(camera, mapRenderer!!).also(ecsWorld.engine::addSystem)
            primitiveRenderSystem = PrimitiveRenderSystem(camera).also(ecsWorld.engine::addSystem)
            physicsDebugRenderer = Box2DDebugRenderer()
            debugOverlay = DebugOverlay(
                DebugOverlaySnapshotBuilder(
                    engine = ecsWorld.engine,
                    mapIdProvider = { mapData?.mapId },
                    connectionStateProvider = { networkClient.connectionState },
                    pingMillisProvider = { networkClient.pingMillis },
                    serverTickProvider = { appliedSnapshot?.serverTick },
                    visibleEntityCountProvider = networkEntities::size,
                    lastGameEventProvider = { lastGameEvent },
                ),
            )
            cameraFollowSystem?.update(0f)
            resize(Gdx.graphics.width, Gdx.graphics.height)
        }
    }

    override fun render(delta: Float) {
        clearScreen(red = 0.7f, green = 0.7f, blue = 0.7f)
        applyLatestSnapshot()
        applyGameEvents()
        ecsWorld.engine.update(delta)
        sendLocalInput()
        sendInteractionIfRequested()
        physicsDebugRenderer?.render(ecsWorld.physicsWorld, camera.combined)
        debugOverlay?.render()
        touchControls?.render()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        camera.viewportWidth = WorldUnits.pixelsToMeters(width.toFloat())
        camera.viewportHeight = WorldUnits.pixelsToMeters(height.toFloat())
        camera.update()
    }

    override fun dispose() {
        Gdx.app?.log("GameScreen", "Disposing game screen; closing network client")
        screenEntities.forEach(ecsWorld.engine::removeEntity)
        screenEntities.clear()
        networkEntities.clear().forEach(ecsWorld.engine::removeEntity)
        ecsWorld.snapshotInterpolationSystem.clearSnapshots()
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
        appliedSnapshot = null
        lastGameEvent = null
        wasInteractPressed = false
        ecsWorld.predictedInputBuffer.clear()
        touchControls?.dispose()
    }

    private fun sendLocalInput() {
        ecsWorld.clientPredictionSystem.drainOutgoingCommands().forEach(networkClient::sendInput)
    }

    private fun applyLatestSnapshot() {
        networkClient.drainWorldSnapshots().forEach(::applySnapshot)
    }

    private fun applyGameEvents() {
        networkClient.drainGameEvents().forEach { event ->
            lastGameEvent = event.message
            Gdx.app?.log("GameScreen", "Interaction event ${event.eventType}: ${event.message}")
        }
    }

    private fun sendInteractionIfRequested() {
        val interactPressed = networkClient.localPlayerEntityId
            ?.let(::findNetworkEntity)
            ?.getComponent(PlayerInputComponent::class.java)
            ?.state
            ?.interact == true
        if (!interactPressed || wasInteractPressed) {
            wasInteractPressed = interactPressed
            return
        }
        wasInteractPressed = true
        val playerId = networkClient.localPlayerEntityId ?: return
        val transform = findNetworkEntity(playerId)?.getComponent(TransformComponent::class.java) ?: return
        val target = mapData?.let { data ->
            (data.triggers.map { MapInteractable(it.id, MapInteractableType.TRIGGER, it.x, it.y, it.width, it.height) } +
                data.portals.map { MapInteractable(it.id, MapInteractableType.PORTAL, it.x, it.y, it.width, it.height) })
                .minByOrNull { squaredDistanceToRectangle(transform.x, transform.y, it) }
        } ?: return
        networkClient.sendInteract(InteractCommand(nextInteractionSequence++, target.id))
    }

    private fun squaredDistanceToRectangle(x: Float, y: Float, target: MapInteractable): Float {
        val nearestX = x.coerceIn(target.x, target.x + target.width)
        val nearestY = y.coerceIn(target.y, target.y + target.height)
        val deltaX = x - nearestX
        val deltaY = y - nearestY
        return deltaX * deltaX + deltaY * deltaY
    }

    private fun applySnapshot(snapshot: WorldSnapshot) {
        if (snapshot === appliedSnapshot) return
        val localPlayerId = networkClient.localPlayerEntityId ?: snapshot.entities.firstOrNull()?.entityId
        val snapshotEntityIds = snapshot.entities.mapTo(mutableSetOf()) { it.entityId }
        networkEntities.serverEntityIds()
            .filterNot(snapshotEntityIds::contains)
            .forEach(::removeNetworkEntity)

        snapshot.entities.forEach { entitySnapshot ->
            val entity = networkEntities.get(entitySnapshot.entityId)
            if (entity == null && entitySnapshot.entityId == localPlayerId) {
                val created = ClientRenderEntityFactory.createLocalPlayerFromSnapshot(
                    ecsWorld.engine,
                    ecsWorld.physicsWorld,
                    entitySnapshot,
                )
                networkEntities.put(entitySnapshot.entityId, created)
            } else if (entity == null) {
                val created = ClientRenderEntityFactory.createRemoteEntityFromSnapshot(
                    ecsWorld.engine,
                    entitySnapshot,
                )
                networkEntities.put(entitySnapshot.entityId, created)
                ecsWorld.snapshotInterpolationSystem.recordSnapshot(snapshot.serverTick, entitySnapshot)
            } else if (entity != null) {
                CharacterSnapshotApplier.apply(entity, entitySnapshot)
                if (entitySnapshot.entityId == localPlayerId) {
                    applySnapshotToEntity(entity, entitySnapshot, snapshot.acknowledgedInputSequence)
                } else {
                    applyRemoteSnapshotToEntity(entity, snapshot.serverTick, entitySnapshot)
                }
            }
        }
        appliedSnapshot = snapshot
        cameraFollowSystem?.update(0f)
    }

    private fun findNetworkEntity(serverEntityId: Int): Entity? =
        networkEntities.get(serverEntityId)

    private fun removeNetworkEntity(serverEntityId: Int) {
        ecsWorld.snapshotInterpolationSystem.removeEntity(serverEntityId)
        networkEntities.remove(serverEntityId)?.let(ecsWorld.engine::removeEntity)
    }

    private fun applySnapshotToEntity(entity: Entity, snapshot: EntitySnapshot, acknowledgedInputSequence: Long) {
        ecsWorld.serverReconciliationSystem.reconcile(
            entity,
            snapshot,
            acknowledgedInputSequence,
        )
    }

    private fun applyRemoteSnapshotToEntity(entity: Entity, serverTick: Long, snapshot: EntitySnapshot) {
        TRANSFORM_MAPPER.get(entity)?.let { transform ->
            transform.x = snapshot.x
            transform.y = snapshot.y
        }
        VELOCITY_MAPPER.get(entity)?.let { velocity ->
            velocity.x = snapshot.velocityX
            velocity.y = snapshot.velocityY
        }
        ecsWorld.snapshotInterpolationSystem.recordSnapshot(serverTick, snapshot)
    }

    private companion object {
        val TRANSFORM_MAPPER: ComponentMapper<TransformComponent> =
            ComponentMapper.getFor(TransformComponent::class.java)
        val VELOCITY_MAPPER: ComponentMapper<VelocityComponent> =
            ComponentMapper.getFor(VelocityComponent::class.java)
    }
}
