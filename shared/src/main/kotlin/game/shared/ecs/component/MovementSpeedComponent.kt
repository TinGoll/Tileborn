package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Maximum movement speed in world units per second. */
class MovementSpeedComponent(
    var movementSpeed: Float,
) : Component
