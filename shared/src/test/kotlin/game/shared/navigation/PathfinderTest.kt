package game.shared.navigation

import game.shared.map.MapCollisionObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathfinderTest {
    @Test
    fun `straight route stays on the direct row`() {
        val grid = NavigationGrid(columns = 6, rows = 3, cellSize = 1f)

        val path = Pathfinder(grid).findPath(NavigationCell(0, 1), NavigationCell(5, 1), entityRadius = 0.2f)

        assertEquals(NavigationCell(0, 1), path.first())
        assertEquals(NavigationCell(5, 1), path.last())
        assertEquals(6, path.size)
        assertTrue(path.all { it.row == 1 })
    }

    @Test
    fun `route goes around rectangular obstacle`() {
        val obstacle = MapCollisionObject(id = 1, x = 2f, y = 0f, width = 1f, height = 2f)
        val grid = NavigationGrid(columns = 6, rows = 4, cellSize = 1f, collisionObjects = listOf(obstacle))

        val path = Pathfinder(grid).findPath(NavigationCell(0, 1), NavigationCell(5, 1), entityRadius = 0.2f)

        assertEquals(NavigationCell(0, 1), path.first())
        assertEquals(NavigationCell(5, 1), path.last())
        assertTrue(path.any { it.row == 2 })
        assertTrue(path.all { grid.isWalkable(it, 0.2f) })
    }

    @Test
    fun `missing route returns empty path`() {
        val wall = MapCollisionObject(id = 1, x = 2f, y = 0f, width = 1f, height = 4f)
        val grid = NavigationGrid(columns = 6, rows = 4, cellSize = 1f, collisionObjects = listOf(wall))

        val path = Pathfinder(grid).findPath(NavigationCell(0, 1), NavigationCell(5, 1), entityRadius = 0.2f)

        assertTrue(path.isEmpty())
    }

    @Test
    fun `start and target in same cell returns that cell`() {
        val grid = NavigationGrid(columns = 3, rows = 3, cellSize = 1f)

        val path = Pathfinder(grid).findPath(1.1f, 1.2f, 1.8f, 1.7f, entityRadius = 0.2f)

        assertEquals(listOf(NavigationCell(1, 1)), path)
    }

    @Test(timeout = 5000L)
    fun `multiple concurrent path requests complete independently`() {
        val obstacles = (2 until 18 step 2).mapIndexed { index, column ->
            val gapAtTop = index % 2 == 0
            MapCollisionObject(
                id = index,
                x = column.toFloat(),
                y = if (gapAtTop) 0f else 1f,
                width = 0.5f,
                height = 19f,
            )
        }
        val grid = NavigationGrid(columns = 20, rows = 20, cellSize = 1f, collisionObjects = obstacles)
        val pathfinder = Pathfinder(grid)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val requests = (0 until 64).map {
                Callable {
                    pathfinder.findPath(NavigationCell(0, 0), NavigationCell(19, 19), entityRadius = 0.1f)
                }
            }
            val results = executor.invokeAll(requests)

            assertTrue(results.all { future -> future.get().lastOrNull() == NavigationCell(19, 19) })
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }
}
