package game.shared.navigation

import game.shared.map.GameMapData
import game.shared.map.MapCollisionObject
import kotlin.math.floor

/** Gameplay-only navigation data derived from map bounds and Tiled collision rectangles. */
class NavigationGrid(
    val columns: Int,
    val rows: Int,
    val cellSize: Float,
    collisionObjects: List<MapCollisionObject> = emptyList(),
) {
    private val obstacles = collisionObjects.toList()

    val worldWidth: Float = columns * cellSize
    val worldHeight: Float = rows * cellSize

    init {
        require(columns > 0) { "Navigation grid columns must be greater than zero" }
        require(rows > 0) { "Navigation grid rows must be greater than zero" }
        require(cellSize.isFinite() && cellSize > 0f) { "Navigation cell size must be finite and positive" }
    }

    fun cellAt(worldX: Float, worldY: Float): NavigationCell? {
        if (!worldX.isFinite() || !worldY.isFinite()) return null
        if (worldX < 0f || worldY < 0f || worldX >= worldWidth || worldY >= worldHeight) return null
        return NavigationCell(
            column = floor(worldX / cellSize).toInt(),
            row = floor(worldY / cellSize).toInt(),
        )
    }

    fun centerX(cell: NavigationCell): Float = (cell.column + 0.5f) * cellSize

    fun centerY(cell: NavigationCell): Float = (cell.row + 0.5f) * cellSize

    fun contains(cell: NavigationCell): Boolean =
        cell.column in 0 until columns && cell.row in 0 until rows

    /** Tests whether a circular entity can stand at the center of [cell]. */
    fun isWalkable(cell: NavigationCell, entityRadius: Float = 0f): Boolean {
        if (!contains(cell) || !validRadius(entityRadius)) return false
        val x = centerX(cell)
        val y = centerY(cell)
        if (x - entityRadius < 0f || y - entityRadius < 0f) return false
        if (x + entityRadius > worldWidth || y + entityRadius > worldHeight) return false
        return obstacles.none { obstacle -> circleIntersectsRectangle(x, y, entityRadius, obstacle) }
    }

    /** Prevents a grid edge from crossing a thin collision object between two clear cell centers. */
    fun canTraverse(from: NavigationCell, to: NavigationCell, entityRadius: Float = 0f): Boolean {
        if (!isWalkable(from, entityRadius) || !isWalkable(to, entityRadius)) return false
        val columnDistance = kotlin.math.abs(from.column - to.column)
        val rowDistance = kotlin.math.abs(from.row - to.row)
        if (columnDistance + rowDistance != 1) return false

        val fromX = centerX(from)
        val fromY = centerY(from)
        val toX = centerX(to)
        val toY = centerY(to)
        return obstacles.none { obstacle ->
            val minimumX = obstacle.x - entityRadius
            val maximumX = obstacle.x + obstacle.width + entityRadius
            val minimumY = obstacle.y - entityRadius
            val maximumY = obstacle.y + obstacle.height + entityRadius
            if (from.row == to.row) {
                fromY in minimumY..maximumY && rangesOverlap(fromX, toX, minimumX, maximumX)
            } else {
                fromX in minimumX..maximumX && rangesOverlap(fromY, toY, minimumY, maximumY)
            }
        }
    }

    fun cells(): Sequence<NavigationCell> = sequence {
        for (row in 0 until rows) {
            for (column in 0 until columns) yield(NavigationCell(column, row))
        }
    }

    private fun circleIntersectsRectangle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        rectangle: MapCollisionObject,
    ): Boolean {
        val nearestX = centerX.coerceIn(rectangle.x, rectangle.x + rectangle.width)
        val nearestY = centerY.coerceIn(rectangle.y, rectangle.y + rectangle.height)
        val deltaX = centerX - nearestX
        val deltaY = centerY - nearestY
        return deltaX * deltaX + deltaY * deltaY <= radius * radius
    }

    private fun rangesOverlap(firstStart: Float, firstEnd: Float, secondStart: Float, secondEnd: Float): Boolean =
        maxOf(firstStart, firstEnd) >= secondStart && minOf(firstStart, firstEnd) <= secondEnd

    private fun validRadius(radius: Float): Boolean = radius.isFinite() && radius >= 0f

    companion object {
        fun fromMap(mapData: GameMapData): NavigationGrid {
            require(
                mapData.widthWorldUnits.isFinite() && mapData.widthWorldUnits > 0f &&
                    mapData.heightWorldUnits.isFinite() && mapData.heightWorldUnits > 0f,
            ) {
                "Map '${mapData.mapId}' must declare positive finite bounds for navigation"
            }
            val cellSize = mapData.navigationCellSizeWorldUnits
            require(cellSize.isFinite() && cellSize > 0f) {
                "Map '${mapData.mapId}' must declare a positive navigation cell size"
            }
            val columns = (mapData.widthWorldUnits / cellSize).toInt()
            val rows = (mapData.heightWorldUnits / cellSize).toInt()
            require(columns * cellSize == mapData.widthWorldUnits && rows * cellSize == mapData.heightWorldUnits) {
                "Map '${mapData.mapId}' bounds must align to its navigation cell size"
            }
            return NavigationGrid(columns, rows, cellSize, mapData.collisionObjects)
        }
    }
}
