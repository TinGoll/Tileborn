package game.client.debug

/** Immutable debug data rendered over the game view. */
data class DebugOverlaySnapshot(
    val fps: Int,
    val serverTick: Long?,
    val localPlayerX: Float?,
    val localPlayerY: Float?,
    val entityCount: Int,
    val visibleEntityCount: Int,
    val mapId: String,
    val connectionState: ConnectionState,
    val pingMillis: Long?,
    val lastGameEvent: String?,
)
