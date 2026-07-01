package game.shared.math

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateConversionTest {
    @Test
    fun `32 pixels equal one meter`() {
        assertEquals(1f, WorldUnits.pixelsToMeters(32f), EPSILON)
    }

    @Test
    fun `one meter equals 32 pixels`() {
        assertEquals(32f, WorldUnits.metersToPixels(1f), EPSILON)
    }

    @Test
    fun `one tile equals 32 pixels`() {
        assertEquals(32f, TileUnits.tilesToPixels(1f), EPSILON)
    }

    @Test
    fun `two tiles equal two meters`() {
        assertEquals(2f, TileUnits.tilesToMeters(2f), EPSILON)
    }

    @Test
    fun `pixel and meter conversion is reversible`() {
        val pixels = 96f

        assertEquals(pixels, WorldUnits.metersToPixels(WorldUnits.pixelsToMeters(pixels)), EPSILON)
    }

    @Test
    fun `tile and pixel conversion is reversible`() {
        val tiles = 2.5f

        assertEquals(tiles, TileUnits.pixelsToTiles(TileUnits.tilesToPixels(tiles)), EPSILON)
    }

    private companion object {
        const val EPSILON: Float = 0.0001f
    }
}
