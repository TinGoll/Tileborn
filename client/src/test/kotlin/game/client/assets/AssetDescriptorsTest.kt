package game.client.assets

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.maps.tiled.TiledMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AssetDescriptorsTest {
    @Test
    fun `common asset descriptor is centralized`() {
        assertEquals("logo.png", AssetDescriptors.LOGO.fileName)
        assertEquals(Texture::class.java, AssetDescriptors.LOGO.type)
    }

    @Test
    fun `known map id resolves to tiled map descriptor`() {
        val descriptor = AssetDescriptors.map(AssetDescriptors.DEBUG_MAP_ID)

        assertSame(AssetDescriptors.DEBUG_MAP, descriptor)
        assertEquals("maps/debug_map.tmx", descriptor?.fileName)
        assertEquals(TiledMap::class.java, descriptor?.type)
    }

    @Test
    fun `unknown optional map does not produce a descriptor`() {
        assertNull(AssetDescriptors.map("missing_optional_map"))
    }
}
