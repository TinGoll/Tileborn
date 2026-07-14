package game.shared.map

import com.badlogic.gdx.maps.MapLayer
import com.badlogic.gdx.maps.MapObject
import com.badlogic.gdx.maps.objects.PointMapObject
import com.badlogic.gdx.maps.objects.RectangleMapObject
import com.badlogic.gdx.maps.tiled.TiledMap
import game.shared.math.WorldUnits

class TiledGameplayMapParser(
    private val errorLogger: (String) -> Unit = {},
) {
    fun parse(mapId: String, tiledMap: TiledMap): GameMapData {
        val collisionLayer = requireLayer(mapId, tiledMap, COLLISION_LAYER)
        val spawnLayer = requireLayer(mapId, tiledMap, SPAWN_POINTS_LAYER)
        val npcSpawnLayer = requireLayer(mapId, tiledMap, NPC_SPAWN_POINTS_LAYER)
        val triggerLayer = requireLayer(mapId, tiledMap, TRIGGERS_LAYER)
        val portalLayer = requireLayer(mapId, tiledMap, PORTALS_LAYER)

        return GameMapData(
            mapId = mapId,
            spawnPoints = spawnLayer.objects.map { parseSpawnPoint(mapId, it) },
            npcSpawnPoints = npcSpawnLayer.objects.map { parseNpcSpawnPoint(mapId, it) },
            collisionObjects = collisionLayer.objects.map { parseCollision(mapId, it) },
            triggers = triggerLayer.objects.map { parseTrigger(mapId, it) },
            portals = portalLayer.objects.map { parsePortal(mapId, it) },
        )
    }

    private fun parseNpcSpawnPoint(mapId: String, mapObject: MapObject): NpcSpawnPoint {
        val context = objectContext(mapId, NPC_SPAWN_POINTS_LAYER, mapObject)
        val point = requirePoint(context, mapObject)
        val properties = MapCustomProperties(mapObject.properties, context)
        return NpcSpawnPoint(
            spawnId = properties.requireString("spawnId"),
            mobDefinitionId = properties.requireString("mobDefinitionId"),
            maxAlive = properties.requireInt("maxAlive", minimum = 1),
            respawnSeconds = properties.requireFloat("respawnSeconds", minimum = 0f),
            spawnRadius = properties.requireFloat("spawnRadius", minimum = 0f),
            x = WorldUnits.pixelsToMeters(point.x),
            y = WorldUnits.pixelsToMeters(point.y),
        )
    }

    private fun requireLayer(mapId: String, map: TiledMap, layerName: String): MapLayer {
        val layer = map.layers.get(layerName)
        if (layer != null) return layer

        val message = "Map '$mapId' is missing required gameplay layer '$layerName'"
        errorLogger(message)
        throw IllegalArgumentException(message)
    }

    private fun parseSpawnPoint(mapId: String, mapObject: MapObject): MapSpawnPoint {
        val context = objectContext(mapId, SPAWN_POINTS_LAYER, mapObject)
        val point = requirePoint(context, mapObject)
        val properties = MapCustomProperties(mapObject.properties, context)
        return MapSpawnPoint(
            spawnId = properties.requireString("spawnId"),
            x = WorldUnits.pixelsToMeters(point.x),
            y = WorldUnits.pixelsToMeters(point.y),
        )
    }

    private fun parseCollision(mapId: String, mapObject: MapObject): MapCollisionObject {
        val context = objectContext(mapId, COLLISION_LAYER, mapObject)
        val rectangle = requireRectangle(context, mapObject)
        return MapCollisionObject(
            id = objectId(mapObject),
            x = WorldUnits.pixelsToMeters(rectangle.x),
            y = WorldUnits.pixelsToMeters(rectangle.y),
            width = WorldUnits.pixelsToMeters(rectangle.width),
            height = WorldUnits.pixelsToMeters(rectangle.height),
        )
    }

    private fun parseTrigger(mapId: String, mapObject: MapObject): MapTrigger {
        val context = objectContext(mapId, TRIGGERS_LAYER, mapObject)
        val rectangle = requireRectangle(context, mapObject)
        val properties = MapCustomProperties(mapObject.properties, context)
        return MapTrigger(
            id = objectId(mapObject),
            triggerId = properties.requireString("triggerId"),
            type = properties.requireString("type"),
            x = WorldUnits.pixelsToMeters(rectangle.x),
            y = WorldUnits.pixelsToMeters(rectangle.y),
            width = WorldUnits.pixelsToMeters(rectangle.width),
            height = WorldUnits.pixelsToMeters(rectangle.height),
        )
    }

    private fun parsePortal(mapId: String, mapObject: MapObject): MapPortal {
        val context = objectContext(mapId, PORTALS_LAYER, mapObject)
        val rectangle = requireRectangle(context, mapObject)
        val properties = MapCustomProperties(mapObject.properties, context)
        return MapPortal(
            id = objectId(mapObject),
            portalId = properties.string("portalId", mapObject.name.orEmpty()).ifEmpty {
                throw IllegalArgumentException("$context requires 'portalId' or an object name")
            },
            targetMap = properties.requireString("targetMap"),
            targetSpawn = properties.requireString("targetSpawn"),
            x = WorldUnits.pixelsToMeters(rectangle.x),
            y = WorldUnits.pixelsToMeters(rectangle.y),
            width = WorldUnits.pixelsToMeters(rectangle.width),
            height = WorldUnits.pixelsToMeters(rectangle.height),
        )
    }

    private fun requirePoint(context: String, mapObject: MapObject) =
        (mapObject as? PointMapObject)?.point
            ?: throw IllegalArgumentException("$context must be a Tiled point object")

    private fun requireRectangle(context: String, mapObject: MapObject) =
        (mapObject as? RectangleMapObject)?.rectangle
            ?: throw IllegalArgumentException("$context must be a Tiled rectangle object")

    private fun objectId(mapObject: MapObject): Int =
        mapObject.properties.get("id")?.toString()?.toIntOrNull() ?: 0

    private fun objectContext(mapId: String, layerName: String, mapObject: MapObject): String =
        "Object '${mapObject.name.orEmpty()}' in layer '$layerName' of map '$mapId'"

    companion object {
        const val COLLISION_LAYER = "collision"
        const val SPAWN_POINTS_LAYER = "spawn_points"
        const val NPC_SPAWN_POINTS_LAYER = "npc_spawn_points"
        const val TRIGGERS_LAYER = "triggers"
        const val PORTALS_LAYER = "portals"
    }
}
