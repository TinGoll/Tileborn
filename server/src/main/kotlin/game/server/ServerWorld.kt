package game.server

import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.ServerEcsWorld
import game.shared.map.GameMapData
import game.shared.map.TiledGameplayMapParser

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

    fun update(deltaTime: Float) {
        engine.update(deltaTime)
    }

    override fun dispose() {
        ecsWorld.dispose()
    }
}
