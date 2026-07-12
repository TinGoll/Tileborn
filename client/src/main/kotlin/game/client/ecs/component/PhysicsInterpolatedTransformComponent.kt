package game.client.ecs.component

import com.badlogic.ashley.core.Component

/** Smooth render-space transform sampled between fixed Box2D simulation steps. */
class PhysicsInterpolatedTransformComponent(
    var x: Float = 0f,
    var y: Float = 0f,
    var rotationDegrees: Float = 0f,
) : Component
