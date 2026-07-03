package game.shared.physics

import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.World
import game.shared.map.GameMapData
import game.shared.map.MapCollisionObject

/** Creates server-compatible Box2D geometry from the gameplay-only Tiled map model. */
class TiledCollisionLoader(
    private val world: World,
) {
    fun load(mapData: GameMapData): List<Body> = mapData.collisionObjects.map(::createStaticBody)

    fun createStaticBody(collision: MapCollisionObject): Body {
        require(collision.width > 0f && collision.height > 0f) {
            "Collision object ${collision.id} must have positive dimensions"
        }
        val centerX = collision.x + collision.width * 0.5f
        val centerY = collision.y + collision.height * 0.5f
        val body = world.createBody(
            BodyDef().apply {
                type = BodyDef.BodyType.StaticBody
                position.set(centerX, centerY)
            },
        )
        body.userData = collision.id
        val shape = PolygonShape()
        try {
            shape.setAsBox(collision.width * 0.5f, collision.height * 0.5f)
            body.createFixture(shape, 0f)
        } finally {
            shape.dispose()
        }
        return body
    }
}
