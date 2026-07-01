package game.shared.math

import game.shared.constants.GameConstants

/** Converts Tiled tile coordinates without exposing scale calculations to game code. */
object TileUnits {
    fun tilesToPixels(tiles: Float): Float = tiles * GameConstants.TILE_SIZE_PIXELS

    fun tilesToMeters(tiles: Float): Float = WorldUnits.pixelsToMeters(tilesToPixels(tiles))

    fun pixelsToTiles(pixels: Float): Float = pixels / GameConstants.TILE_SIZE_PIXELS
}
