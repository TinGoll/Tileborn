package game.client.debug

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.utils.Disposable
import game.client.ecs.ClientRenderEntityFactory
import game.shared.map.GameMapData

/** Owns client-only navigation grid entities and can hide them without changing navigation data. */
class NavigationGridDebugController(
    private val engine: Engine,
    private val mapData: GameMapData,
    initiallyVisible: Boolean = true,
) : Disposable {
    private val gridEntities = mutableListOf<Entity>()

    var visible: Boolean = false
        private set

    init {
        setVisible(initiallyVisible)
    }

    fun toggle() = setVisible(!visible)

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) {
            gridEntities += ClientRenderEntityFactory.createDebugNavigationGrid(engine, mapData)
        } else {
            gridEntities.forEach(engine::removeEntity)
            gridEntities.clear()
        }
    }

    override fun dispose() = setVisible(false)
}
