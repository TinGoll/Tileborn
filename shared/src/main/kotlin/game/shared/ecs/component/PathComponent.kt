package game.shared.ecs.component

import com.badlogic.ashley.core.Component
import game.shared.navigation.NavigationCell

/** Server-authored path state. Contains data only and is safe to inspect from debug tooling. */
class PathComponent(
    val entityRadius: Float,
    var cells: List<NavigationCell> = emptyList(),
    var nextCellIndex: Int = 0,
    var lastTargetCell: NavigationCell? = null,
    var lastTargetX: Float = Float.NaN,
    var lastTargetY: Float = Float.NaN,
    var secondsUntilRepath: Float = 0f,
    var pathRequestCount: Long = 0L,
    var noPathAvailable: Boolean = false,
) : Component
