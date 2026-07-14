package game.server.ecs.component

import com.badlogic.ashley.core.Component

/** Immutable spawn position to which a mob returns after losing aggro. */
class HomePositionComponent(
    val x: Float,
    val y: Float,
) : Component
