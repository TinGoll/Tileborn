package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Authoritative or replicated position and rotation in world units. */
class TransformComponent(
    var x: Float = 0f,
    var y: Float = 0f,
    var rotationDegrees: Float = 0f,
) : Component
