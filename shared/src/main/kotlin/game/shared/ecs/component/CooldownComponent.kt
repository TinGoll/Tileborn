package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Mutable authoritative cooldown state expressed in simulation seconds. */
class CooldownComponent(
    val durationSeconds: Float,
    var remainingSeconds: Float = 0f,
) : Component
