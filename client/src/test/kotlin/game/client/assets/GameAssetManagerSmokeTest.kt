package game.client.assets

import com.badlogic.gdx.assets.AssetManager
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

    private class RecordingAssetManager : AssetManager() {
        var wasDisposed = false

        override fun dispose() {
            wasDisposed = true
            super.dispose()
        }
    }
}
