package game.client.debug

import com.badlogic.ashley.core.Engine
import game.shared.map.GameMapData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGridDebugControllerTest {
    @Test
    fun `grid can be hidden and shown without changing map data`() {
        val engine = Engine()
        val mapData = GameMapData(
            mapId = "debug",
            spawnPoints = emptyList(),
            collisionObjects = emptyList(),
            triggers = emptyList(),
            portals = emptyList(),
            widthWorldUnits = 3f,
            heightWorldUnits = 2f,
            navigationCellSizeWorldUnits = 1f,
        )
        val controller = NavigationGridDebugController(engine, mapData)

        assertTrue(controller.visible)
        assertEquals(7, engine.entities.size())

        controller.toggle()
        assertFalse(controller.visible)
        assertEquals(0, engine.entities.size())

        controller.setVisible(true)
        assertTrue(controller.visible)
        assertEquals(7, engine.entities.size())

        controller.dispose()
        assertEquals(0, engine.entities.size())
        assertEquals(3f, mapData.widthWorldUnits, 0f)
    }
}
