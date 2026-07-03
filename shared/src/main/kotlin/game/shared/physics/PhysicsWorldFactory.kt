package game.shared.physics

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.GdxNativesLoader
import game.shared.constants.GameConstants

object PhysicsWorldFactory {
    fun create(): World {
        GdxNativesLoader.load()
        Box2D.init()
        return World(Vector2(), true)
    }

    fun createDynamicPlayerBody(world: World, x: Float, y: Float): Body {
        val body = world.createBody(
            BodyDef().apply {
                type = BodyDef.BodyType.DynamicBody
                position.set(x, y)
                fixedRotation = true
            },
        )
        val shape = CircleShape()
        try {
            shape.radius = GameConstants.PLAYER_COLLISION_RADIUS
            body.createFixture(
                FixtureDef().apply {
                    this.shape = shape
                    density = 1f
                    friction = 0f
                },
            )
        } finally {
            shape.dispose()
        }
        return body
    }
}
