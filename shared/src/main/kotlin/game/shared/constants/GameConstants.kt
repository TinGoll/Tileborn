package game.shared.constants

/** Shared world-scale constants used by clients and the authoritative server. */
object GameConstants {
    const val TILE_SIZE_PIXELS: Float = 32f
    const val PIXELS_PER_METER: Float = 32f
    const val WORLD_UNITS_PER_METER: Float = 1f
    const val PLAYER_MOVE_SPEED: Float = 4f
    const val PHYSICS_FIXED_TIME_STEP: Float = 1f / 60f
    const val PHYSICS_VELOCITY_ITERATIONS: Int = 6
    const val PHYSICS_POSITION_ITERATIONS: Int = 2
    const val PLAYER_COLLISION_RADIUS: Float = 0.4f
}
