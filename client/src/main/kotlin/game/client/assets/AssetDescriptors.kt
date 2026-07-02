package game.client.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.loaders.TextureLoader
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.maps.tiled.TiledMap

object AssetDescriptors {
    const val DEBUG_MAP_ID = "debug_map"

    private val logoParameters = TextureLoader.TextureParameter().apply {
        genMipMaps = true
        minFilter = Texture.TextureFilter.Linear
        magFilter = Texture.TextureFilter.Linear
    }

    val LOGO = AssetDescriptor("logo.png", Texture::class.java, logoParameters)
    val DEBUG_MAP = AssetDescriptor("maps/debug_map.tmx", TiledMap::class.java)

    private val mapsById = mapOf(DEBUG_MAP_ID to DEBUG_MAP)

    fun map(mapId: String): AssetDescriptor<TiledMap>? = mapsById[mapId]
}
