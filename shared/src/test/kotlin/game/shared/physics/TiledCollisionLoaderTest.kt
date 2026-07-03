package game.shared.physics

import com.badlogic.gdx.physics.box2d.BodyDef
import game.shared.map.GameMapData
import game.shared.map.MapCollisionObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TiledCollisionLoaderTest {
    @Test
    fun `collision layer data creates centered static bodies`() {
        val world = PhysicsWorldFactory.create()
        try {
            val bodies = TiledCollisionLoader(world).load(
                GameMapData(
                    mapId = "test",
                    spawnPoints = emptyList(),
                    collisionObjects = listOf(
                        MapCollisionObject(id = 7, x = 1f, y = 2f, width = 4f, height = 2f),
                    ),
                    triggers = emptyList(),
                    portals = emptyList(),
                ),
            )

            assertEquals(1, world.bodyCount)
            assertEquals(BodyDef.BodyType.StaticBody, bodies.single().type)
            assertEquals(3f, bodies.single().position.x, EPSILON)
            assertEquals(3f, bodies.single().position.y, EPSILON)
            assertEquals(7, bodies.single().userData)
        } finally {
            world.dispose()
        }
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
