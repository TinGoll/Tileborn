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

/** Tiled object types which are eligible for explicit player interaction. */
enum class MapInteractableType {
    TRIGGER,
    PORTAL,
}

data class MapInteractable(
    val id: Int,
    val type: MapInteractableType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

fun GameMapData.interactableById(objectId: Int): MapInteractable? =
    triggers.firstOrNull { it.id == objectId }?.let {
        MapInteractable(it.id, MapInteractableType.TRIGGER, it.x, it.y, it.width, it.height)
    } ?: portals.firstOrNull { it.id == objectId }?.let {
        MapInteractable(it.id, MapInteractableType.PORTAL, it.x, it.y, it.width, it.height)
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
