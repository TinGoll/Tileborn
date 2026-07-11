package game.shared.map

import org.junit.Assert.assertEquals
import org.junit.Test

class GameMapDataTest {
    @Test
    fun `require spawn point selects spawn from map data`() {
        val mapData = GameMapData(
            mapId = "test_map",
            spawnPoints = listOf(
                MapSpawnPoint(spawnId = "default", x = 5f, y = 7f),
                MapSpawnPoint(spawnId = "east_gate", x = 12f, y = 3f),
            ),
            collisionObjects = emptyList(),
            triggers = emptyList(),
            portals = emptyList(),
        )

        val spawn = mapData.requireSpawnPoint("east_gate")

        assertEquals(MapSpawnPoint(spawnId = "east_gate", x = 12f, y = 3f), spawn)
    }
}
