package game.server

import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.component.ServerAuthorityComponent
import game.server.ecs.ServerEcsWorld
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.map.GameMapData
import game.shared.map.TiledGameplayMapParser
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.WorldSnapshot

/** Authoritative server world state: ECS engine plus gameplay-only map metadata. */
class ServerWorld(
    mapId: String,
    mapPath: String,
    private val ecsWorld: ServerEcsWorld = ServerEcsWorld(),
) : Disposable {
    val gameMapData: GameMapData
    val engine = ecsWorld.engine

    init {
        val tiledMap = TmxMapLoader().load(mapPath)
        try {
            gameMapData = TiledGameplayMapParser(::println).parse(mapId, tiledMap)
        } finally {
            tiledMap.dispose()
        }
    }

    @Synchronized
    fun update(deltaTime: Float) {
        engine.update(deltaTime)
    }

    @Synchronized
    fun spawnPlayer(serverEntityId: Int, spawnId: String = DEFAULT_SPAWN_ID) =
        gameMapData.requireSpawnPoint(spawnId).let { spawn ->
            engine.createEntity().apply {
                add(TransformComponent(x = spawn.x, y = spawn.y))
                add(NetworkIdentityComponent(networkEntityId = serverEntityId.toLong()))
                add(ServerAuthorityComponent())
            }.also(engine::addEntity)
        }

    @Synchronized
    fun buildSnapshot(serverTick: Long): WorldSnapshot =
        WorldSnapshot(
            serverTick = serverTick,
            entities = engine.entities
                .mapNotNull { entity ->
                    val identity = entity.getComponent(NetworkIdentityComponent::class.java) ?: return@mapNotNull null
                    val transform = entity.getComponent(TransformComponent::class.java) ?: return@mapNotNull null
                    val velocity = entity.getComponent(VelocityComponent::class.java)
                    EntitySnapshot(
                        entityId = identity.networkEntityId.toInt(),
                        x = transform.x,
                        y = transform.y,
                        velocityX = velocity?.x ?: 0f,
                        velocityY = velocity?.y ?: 0f,
                    )
                },
        )

    override fun dispose() {
        ecsWorld.dispose()
    }

    private companion object {
        const val DEFAULT_SPAWN_ID = "default"
    }
}
