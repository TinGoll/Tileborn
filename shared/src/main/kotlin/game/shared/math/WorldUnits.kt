package game.shared.math

import game.shared.constants.GameConstants

/** Converts between rendering pixels and authoritative world/Box2D meters. */
object WorldUnits {
    fun pixelsToMeters(pixels: Float): Float = pixels / GameConstants.PIXELS_PER_METER

    fun metersToPixels(meters: Float): Float = meters * GameConstants.PIXELS_PER_METER
}
