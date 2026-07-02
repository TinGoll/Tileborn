package game.desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.client.assets.AssetDescriptors
import game.client.assets.GameAssetManager
import game.shared.map.TiledGameplayMapParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                assertFalse(data.collisionObjects.isEmpty())
            }
        } finally {
            application.exit()
        }
    }
}
