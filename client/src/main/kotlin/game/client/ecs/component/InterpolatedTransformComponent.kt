package game.client.ecs.component

import com.badlogic.ashley.core.Component

/** Position used to render a remote entity between authoritative snapshots. */
class InterpolatedTransformComponent(
    var x: Float = 0f,
    var y: Float = 0f,
    var rotationDegrees: Float = 0f,
) : Component
