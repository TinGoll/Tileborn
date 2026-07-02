package game.client.screens

import com.badlogic.gdx.assets.AssetManager
import game.client.assets.GameAssetManager
import game.client.ecs.ClientEcsWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingScreenTest {
    @Test
    fun `completed loading transitions exactly once`() {
        val assets = GameAssetManager(CompletingAssetManager())
        var transitions = 0
        val screen = LoadingScreen(assets) { transitions++ }

        screen.queueAssetsOnce()

        assertTrue(screen.advanceLoading())
        assertFalse(screen.advanceLoading())
        assertEquals(1, transitions)
        assets.dispose()
    }

    @Test
    fun `game screen receives the ready asset manager`() {
        GameAssetManager().use { assets ->
            val screen = GameScreen(assets, ClientEcsWorld())

            assertSame(assets, screen.assets)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `game screen rejects an asset manager that is still loading`() {
        GameAssetManager().use { assets ->
            assets.queueCommonAssets()

            GameScreen(assets, ClientEcsWorld())
        }
    }

    @Test
    fun `loading failure prevents transition`() {
        val assets = GameAssetManager(FailingAssetManager())
        var transitioned = false
        val screen = LoadingScreen(assets) { transitioned = true }

        screen.queueAssetsOnce()

        assertFalse(screen.advanceLoading())
        assertFalse(screen.advanceLoading())
        assertFalse(transitioned)
        assets.dispose()
    }

    private class CompletingAssetManager : AssetManager() {
        override fun update(): Boolean = true
    }

    private class FailingAssetManager : AssetManager() {
        override fun update(): Boolean = throw IllegalStateException("broken asset")

        override fun dispose() = Unit
    }
}
