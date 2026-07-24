package game.shared.navigation

import java.util.PriorityQueue
import kotlin.math.abs

/** Thread-safe A* implementation using four-way movement and a Manhattan heuristic. */
class Pathfinder(
    private val grid: NavigationGrid,
) {
    fun findPath(
        startWorldX: Float,
        startWorldY: Float,
        targetWorldX: Float,
        targetWorldY: Float,
        entityRadius: Float = 0f,
    ): List<NavigationCell> {
        val start = grid.cellAt(startWorldX, startWorldY) ?: return emptyList()
        val target = grid.cellAt(targetWorldX, targetWorldY) ?: return emptyList()
        return findPath(start, target, entityRadius)
    }

    fun findPath(
        start: NavigationCell,
        target: NavigationCell,
        entityRadius: Float = 0f,
    ): List<NavigationCell> {
        if (!grid.isWalkable(start, entityRadius) || !grid.isWalkable(target, entityRadius)) return emptyList()
        if (start == target) return listOf(start)

        val cellCount = grid.columns * grid.rows
        val costs = IntArray(cellCount) { UNREACHED }
        val parents = IntArray(cellCount) { NO_PARENT }
        val closed = BooleanArray(cellCount)
        val open = PriorityQueue(compareBy<OpenCell>({ it.estimatedTotalCost }, { it.sequence }))
        var sequence = 0L
        val startIndex = index(start)
        costs[startIndex] = 0
        open += OpenCell(start, heuristic(start, target), sequence++)

        while (open.isNotEmpty()) {
            val current = open.remove().cell
            val currentIndex = index(current)
            if (closed[currentIndex]) continue
            if (current == target) return reconstructPath(parents, currentIndex)
            closed[currentIndex] = true

            for (neighbor in neighbors(current)) {
                val neighborIndex = index(neighbor)
                if (closed[neighborIndex] || !grid.canTraverse(current, neighbor, entityRadius)) continue
                val nextCost = costs[currentIndex] + 1
                if (nextCost >= costs[neighborIndex]) continue
                costs[neighborIndex] = nextCost
                parents[neighborIndex] = currentIndex
                open += OpenCell(neighbor, nextCost + heuristic(neighbor, target), sequence++)
            }
        }
        return emptyList()
    }

    private fun reconstructPath(parents: IntArray, targetIndex: Int): List<NavigationCell> {
        val reversed = ArrayList<NavigationCell>()
        var currentIndex = targetIndex
        while (currentIndex != NO_PARENT) {
            reversed += cell(currentIndex)
            currentIndex = parents[currentIndex]
        }
        reversed.reverse()
        return reversed
    }

    private fun neighbors(cell: NavigationCell): Sequence<NavigationCell> = sequence {
        if (cell.column > 0) yield(NavigationCell(cell.column - 1, cell.row))
        if (cell.column + 1 < grid.columns) yield(NavigationCell(cell.column + 1, cell.row))
        if (cell.row > 0) yield(NavigationCell(cell.column, cell.row - 1))
        if (cell.row + 1 < grid.rows) yield(NavigationCell(cell.column, cell.row + 1))
    }

    private fun index(cell: NavigationCell): Int = cell.row * grid.columns + cell.column

    private fun cell(index: Int): NavigationCell = NavigationCell(index % grid.columns, index / grid.columns)

    private fun heuristic(first: NavigationCell, second: NavigationCell): Int =
        abs(first.column - second.column) + abs(first.row - second.row)

    private data class OpenCell(
        val cell: NavigationCell,
        val estimatedTotalCost: Int,
        val sequence: Long,
    )

    private companion object {
        const val UNREACHED = Int.MAX_VALUE
        const val NO_PARENT = -1
    }
}
