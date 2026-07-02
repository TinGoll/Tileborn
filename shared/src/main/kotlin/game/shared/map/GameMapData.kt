package game.shared.map

/** Gameplay-only representation of a Tiled map. All coordinates are world units (meters). */
data class GameMapData(
    val mapId: String,
    val spawnPoints: List<MapSpawnPoint>,
    val collisionObjects: List<MapCollisionObject>,
    val triggers: List<MapTrigger>,
    val portals: List<MapPortal>,
) {
    fun requireSpawnPoint(spawnId: String): MapSpawnPoint =
        spawnPoints.firstOrNull { it.spawnId == spawnId }
            ?: error("Map '$mapId' does not contain spawn point '$spawnId'")
}

data class MapSpawnPoint(
    val spawnId: String,
    val x: Float,
    val y: Float,
)

data class MapCollisionObject(
    val id: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

data class MapTrigger(
    val id: Int,
    val triggerId: String,
    val type: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

data class MapPortal(
    val id: Int,
    val portalId: String,
    val targetMap: String,
    val targetSpawn: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
