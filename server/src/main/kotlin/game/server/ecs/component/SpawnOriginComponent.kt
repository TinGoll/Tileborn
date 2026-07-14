package game.server.ecs.component

import com.badlogic.ashley.core.Component

/** Identifies the Tiled population rule that owns this server-side NPC. */
class SpawnOriginComponent(
    val spawnId: String,
) : Component
