package game.client.ecs.system

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer

/** Renders the Tiled map between camera update and primitive entity rendering. */
class MapRenderSystem(
    private val camera: OrthographicCamera,
    private val mapRenderer: OrthogonalTiledMapRenderer,
) : EntitySystem(PRIORITY) {
    override fun update(deltaTime: Float) {
        camera.update()
        mapRenderer.setView(camera)
        mapRenderer.render()
    }

    private companion object {
        const val PRIORITY = 800
    }
}
