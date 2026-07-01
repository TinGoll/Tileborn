package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Linear velocity in world units per second. */
class VelocityComponent(
    var x: Float = 0f,
    var y: Float = 0f,
) : Component
