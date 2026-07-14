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
        return createCircleBody(
            world = world,
            x = x,
            y = y,
            collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
            bodyType = BodyDef.BodyType.DynamicBody,
        )
    }

    /** Client-side collision proxy for a remote server-authoritative player. */
    fun createKinematicPlayerBody(world: World, x: Float, y: Float): Body {
        return createKinematicCircleBody(
            world = world,
            x = x,
            y = y,
            collisionRadius = GameConstants.PLAYER_COLLISION_RADIUS,
        )
    }

    /** Client-side collision proxy positioned from authoritative snapshots. */
    fun createKinematicCircleBody(world: World, x: Float, y: Float, collisionRadius: Float): Body {
        return createCircleBody(world, x, y, collisionRadius, BodyDef.BodyType.KinematicBody)
    }

    fun createDynamicCircleBody(world: World, x: Float, y: Float, collisionRadius: Float): Body {
        return createCircleBody(world, x, y, collisionRadius, BodyDef.BodyType.DynamicBody)
    }

    private fun createCircleBody(
        world: World,
        x: Float,
        y: Float,
        collisionRadius: Float,
        bodyType: BodyDef.BodyType,
    ): Body {
        require(collisionRadius > 0f && collisionRadius.isFinite()) {
            "collisionRadius must be finite and greater than zero, was $collisionRadius"
        }
        val body = world.createBody(
            BodyDef().apply {
                type = bodyType
                position.set(x, y)
                fixedRotation = true
            },
        )
        val shape = CircleShape()
        try {
            shape.radius = collisionRadius
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
