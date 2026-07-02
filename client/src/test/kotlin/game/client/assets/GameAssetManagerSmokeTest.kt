package game.client.assets

import com.badlogic.gdx.assets.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAssetManagerSmokeTest {
    @Test
    fun `manager can be created and disposed without application runtime`() {
        GameAssetManager().dispose()
    }

    @Test
    fun `missing optional map leaves loading flow valid`() {
        GameAssetManager().use { assets ->
            assets.queueMapAssets("missing_optional_map")
            assets.update()
        }
    }

    @Test
    fun `dispose is delegated to libgdx asset manager`() {
        val delegate = RecordingAssetManager()

        GameAssetManager(delegate).dispose()

        assertTrue(delegate.wasDisposed)
    }

    @Test
    fun `queueing the same scopes repeatedly does not duplicate assets`() {
        val delegate = AssetManager()

        GameAssetManager(delegate).use { assets ->
            repeat(2) {
                assets.queueCommonAssets()
                assets.queueMapAssets(AssetDescriptors.DEBUG_MAP_ID)
            }

            assertEquals(2, delegate.queuedAssets)
        }
    }

    private class RecordingAssetManager : AssetManager() {
        var wasDisposed = false

        override fun dispose() {
            wasDisposed = true
            super.dispose()
        }
    }
}
