package game.shared.input

import kotlin.math.sqrt

/** Platform-independent player intent, suitable for inclusion in a client input command. */
data class GameInputState(
    var moveX: Float = 0f,
    var moveY: Float = 0f,
    var attack: Boolean = false,
    var interact: Boolean = false,
    var aimX: Float = 0f,
    var aimY: Float = 0f,
) {
    /** Sets movement intent and caps its magnitude so diagonals are not faster. */
    fun setMovement(x: Float, y: Float) {
        val safeX = x.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        val safeY = y.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        val lengthSquared = safeX * safeX + safeY * safeY
        if (lengthSquared > 1f) {
            val inverseLength = 1f / sqrt(lengthSquared)
            moveX = safeX * inverseLength
            moveY = safeY * inverseLength
        } else {
            moveX = safeX
            moveY = safeY
        }
    }
}
