package game.shared.map

import com.badlogic.gdx.maps.MapProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class MapCustomPropertiesTest {
    @Test
    fun `required string custom property is trimmed`() {
        val properties = MapProperties().apply { put("spawnId", " default ") }

        val spawnId = MapCustomProperties(properties, "test spawn").requireString("spawnId")

        assertEquals("default", spawnId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing required custom property fails with validation error`() {
        MapCustomProperties(MapProperties(), "test portal").requireString("targetMap")
    }
}
