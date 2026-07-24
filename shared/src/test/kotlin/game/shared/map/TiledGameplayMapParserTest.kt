package game.shared.map

import com.badlogic.gdx.maps.MapLayer
import com.badlogic.gdx.maps.MapObject
import com.badlogic.gdx.maps.objects.PointMapObject
import com.badlogic.gdx.maps.objects.RectangleMapObject
import com.badlogic.gdx.maps.tiled.TiledMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiledGameplayMapParserTest {
    @Test
    fun `spawn point with default spawn id is parsed`() {
        val map = completeMap()
        try {
            map.layers[TiledGameplayMapParser.SPAWN_POINTS_LAYER].objects.add(
                PointMapObject(64f, 96f).withProperties("spawnId" to "default"),
            )

            val result = TiledGameplayMapParser().parse("test_map", map)

            assertEquals(MapSpawnPoint("default", 2f, 3f), result.requireSpawnPoint("default"))
        } finally {
            map.dispose()
        }
    }

    @Test
    fun `npc spawn point includes population settings in world units`() {
        val map = completeMap()
        try {
            map.layers[TiledGameplayMapParser.NPC_SPAWN_POINTS_LAYER].objects.add(
                PointMapObject(96f, 128f).withProperties(
                    "spawnId" to "slime_1",
                    "mobDefinitionId" to "slime",
                    "maxAlive" to 3,
                    "respawnSeconds" to 5f,
                    "spawnRadius" to 2f,
                ),
            )

            val spawn = TiledGameplayMapParser().parse("test_map", map).npcSpawnPoints.single()

            assertEquals(NpcSpawnPoint("slime_1", "slime", 3, 5f, 2f, 3f, 4f), spawn)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun `collision rectangle is converted from pixels to world units`() {
        val map = completeMap()
        try {
            map.layers[TiledGameplayMapParser.COLLISION_LAYER].objects.add(
                RectangleMapObject(32f, 64f, 96f, 128f).withProperties("id" to 42),
            )

            val collision = TiledGameplayMapParser()
                .parse("test_map", map)
                .collisionObjects.single()

            assertEquals(MapCollisionObject(42, 1f, 2f, 3f, 4f), collision)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun `map dimensions and navigation cell size are converted to world units`() {
        val map = completeMap().apply {
            properties.put("width", 12)
            properties.put("height", 8)
            properties.put("tilewidth", 32)
            properties.put("tileheight", 32)
        }
        try {
            val result = TiledGameplayMapParser().parse("test_map", map)

            assertEquals(12f, result.widthWorldUnits, 0f)
            assertEquals(8f, result.heightWorldUnits, 0f)
            assertEquals(1f, result.navigationCellSizeWorldUnits, 0f)
        } finally {
            map.dispose()
        }
    }

    @Test
    fun `missing required layer is logged and rejected`() {
        val messages = mutableListOf<String>()
        val map = TiledMap()
        try {
            try {
                TiledGameplayMapParser(messages::add).parse("broken_map", map)
            } catch (_: IllegalArgumentException) {
                // Expected: malformed maps must not enter gameplay.
            }
        } finally {
            map.dispose()
        }

        assertTrue(messages.single().contains("broken_map"))
        assertTrue(messages.single().contains("collision"))
    }

    @Test
    fun `trigger object is available as an interactable`() {
        val map = completeMap()
        try {
            map.layers[TiledGameplayMapParser.TRIGGERS_LAYER].objects.add(
                RectangleMapObject(32f, 64f, 32f, 32f).withProperties(
                    "id" to 6,
                    "triggerId" to "welcome",
                    "type" to "message",
                ),
            )

            val interactable = TiledGameplayMapParser().parse("test_map", map).interactableById(6)

            assertEquals(MapInteractable(6, MapInteractableType.TRIGGER, 1f, 2f, 1f, 1f), interactable)
        } finally {
            map.dispose()
        }
    }

    private fun completeMap() = TiledMap().apply {
        layers.add(MapLayer().named(TiledGameplayMapParser.COLLISION_LAYER))
        layers.add(MapLayer().named(TiledGameplayMapParser.SPAWN_POINTS_LAYER))
        layers.add(MapLayer().named(TiledGameplayMapParser.NPC_SPAWN_POINTS_LAYER))
        layers.add(MapLayer().named(TiledGameplayMapParser.TRIGGERS_LAYER))
        layers.add(MapLayer().named(TiledGameplayMapParser.PORTALS_LAYER))
    }

    private fun MapLayer.named(layerName: String) = apply { name = layerName }

    private fun <T : MapObject> T.withProperties(vararg values: Pair<String, Any>) = apply {
        values.forEach { (name, value) -> properties.put(name, value) }
    }
}
