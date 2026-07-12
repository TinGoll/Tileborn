package game.client.debug

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import game.client.ecs.component.LocalPlayerComponent
import game.shared.ecs.component.TransformComponent

/** Reads current client state for debug rendering without changing gameplay data. */
class DebugOverlaySnapshotBuilder(
    private val engine: Engine,
    private val mapIdProvider: () -> String?,
    private val connectionStateProvider: () -> ConnectionState,
    private val pingMillisProvider: () -> Long? = { null },
    private val visibleEntityCountProvider: () -> Int = { engine.entities.size() },
    private val lastGameEventProvider: () -> String? = { null },
) {
    fun build(fps: Int): DebugOverlaySnapshot {
        val player = localPlayers.firstOrNull()
        val transform = player?.let(TRANSFORM_MAPPER::get)

        return DebugOverlaySnapshot(
            fps = fps,
            localPlayerX = transform?.x,
            localPlayerY = transform?.y,
            entityCount = engine.entities.size(),
            visibleEntityCount = visibleEntityCountProvider(),
            mapId = mapIdProvider() ?: "none",
            connectionState = connectionStateProvider(),
            pingMillis = pingMillisProvider(),
            lastGameEvent = lastGameEventProvider(),
        )
    }

    private val localPlayers
        get() = engine.getEntitiesFor(LOCAL_PLAYER_FAMILY)

    private companion object {
        val TRANSFORM_MAPPER: ComponentMapper<TransformComponent> =
            ComponentMapper.getFor(TransformComponent::class.java)
        val LOCAL_PLAYER_FAMILY: Family = Family.all(
            LocalPlayerComponent::class.java,
            TransformComponent::class.java,
        ).get()
    }
}
