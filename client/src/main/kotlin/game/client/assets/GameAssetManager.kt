package game.client.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.maps.tiled.TiledMap
import com.badlogic.gdx.maps.tiled.TmxMapLoader

class GameAssetManager(
    private val manager: AssetManager = AssetManager(),
) : AutoCloseable {
    private val assetsByScope = AssetScope.entries.associateWith { linkedSetOf<String>() }

    init {
        manager.setLoader(TiledMap::class.java, TmxMapLoader(manager.fileHandleResolver))
    }

    fun queueCommonAssets() {
        queue(AssetScope.COMMON, AssetDescriptors.LOGO)
    }

    /** Unknown map ids are optional and intentionally leave the queue unchanged. */
    fun queueMapAssets(mapId: String) {
        AssetDescriptors.map(mapId)?.let { queue(AssetScope.MAP, it) }
    }

    fun update(): Boolean = manager.update()

    fun getProgress(): Float = manager.progress

    fun isFinished(): Boolean = manager.isFinished

    fun <T> get(descriptor: AssetDescriptor<T>): T = manager.get(descriptor)

    fun unloadScope(scope: AssetScope) {
        val paths = assetsByScope.getValue(scope)
        paths.forEach { path ->
            if (manager.contains(path)) {
                manager.unload(path)
            }
        }
        paths.clear()
    }

    override fun close() = dispose()

    fun dispose() {
        manager.dispose()
        assetsByScope.values.forEach(MutableSet<String>::clear)
    }

    private fun <T> queue(scope: AssetScope, descriptor: AssetDescriptor<T>) {
        assetsByScope.getValue(scope).add(descriptor.fileName)
        if (!manager.contains(descriptor.fileName)) {
            manager.load(descriptor)
        }
    }
}
