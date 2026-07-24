package game.shared.navigation

import game.shared.map.MapCollisionObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGridTest {
    @Test
    fun `collision objects and entity radius affect walkability`() {
        val obstacle = MapCollisionObject(id = 1, x = 2f, y = 0f, width = 1f, height = 3f)
        val grid = NavigationGrid(5, 3, 1f, listOf(obstacle))

        assertFalse(grid.isWalkable(NavigationCell(2, 1), entityRadius = 0f))
        assertTrue(grid.isWalkable(NavigationCell(1, 1), entityRadius = 0.4f))
        assertFalse(grid.isWalkable(NavigationCell(1, 1), entityRadius = 0.6f))
    }

    @Test
    fun `map bounds exclude cells that cannot contain entity`() {
        val grid = NavigationGrid(3, 3, 1f)

        assertNull(grid.cellAt(-0.01f, 1f))
        assertNull(grid.cellAt(3f, 1f))
        assertFalse(grid.isWalkable(NavigationCell(0, 1), entityRadius = 0.6f))
        assertTrue(grid.isWalkable(NavigationCell(1, 1), entityRadius = 0.6f))
    }
}
