package game.client.debug

/** Immutable debug data rendered over the game view. */
data class DebugOverlaySnapshot(
    val fps: Int,
    val localPlayerX: Float?,
    val localPlayerY: Float?,
    val entityCount: Int,
    val mapId: String,
    val connectionState: ConnectionState,
    val pingMillis: Long?,
)
