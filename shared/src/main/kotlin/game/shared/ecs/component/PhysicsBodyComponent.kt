package game.shared.ecs.component

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.physics.box2d.Body

/** Box2D body owned by the physics world. Components remain data-only. */
class PhysicsBodyComponent(
    val body: Body,
    /** Set after an authoritative teleport/correction to push Transform into Box2D once. */
    var synchronizeTransformToBody: Boolean = true,
    /** Body transform immediately before the most recent fixed physics step. */
    var previousX: Float = body.position.x,
    var previousY: Float = body.position.y,
    var previousRotationRadians: Float = body.angle,
    /** False for externally positioned bodies such as client-side remote-player proxies. */
    var synchronizeVelocityWithBody: Boolean = true,
) : Component
