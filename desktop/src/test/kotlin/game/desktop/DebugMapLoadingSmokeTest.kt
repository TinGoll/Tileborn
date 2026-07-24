package game.desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import game.shared.map.TiledGameplayMapParser
import game.shared.navigation.NavigationCell
import game.shared.navigation.NavigationGrid
import game.shared.navigation.Pathfinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugMapLoadingSmokeTest {
    @Test
    fun `desktop client loads and parses debug map through asset manager`() {
        val application = HeadlessApplication(object : ApplicationAdapter() {})
        try {
            GameAssetManager().use { assets ->
                assets.queueMapAssets(AssetDescriptors.DEBUG_MAP_ID)
                assets.finishLoading()

                val map = assets.get(AssetDescriptors.DEBUG_MAP)
                val data = TiledGameplayMapParser().parse(AssetDescriptors.DEBUG_MAP_ID, map)

                assertEquals("default", data.requireSpawnPoint("default").spawnId)
                assertEquals(30f, data.widthWorldUnits, 0f)
                assertEquals(20f, data.heightWorldUnits, 0f)
                assertTrue("Debug map should contain internal AI test obstacles", data.collisionObjects.size > 4)
                assertEquals(3, data.npcSpawnPoints.size)

                val grid = NavigationGrid.fromMap(data)
                val path = Pathfinder(grid).findPath(
                    start = NavigationCell(10, 5),
                    target = NavigationCell(5, 5),
                    entityRadius = 0.35f,
                )

                assertFalse("AI test route around west wall must exist", path.isEmpty())
                assertTrue("AI test route should detour from the direct row", path.any { it.row != 5 })
            }
        } finally {
            application.exit()
        }
    }
}
