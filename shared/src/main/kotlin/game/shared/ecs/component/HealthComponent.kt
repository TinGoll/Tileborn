package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Mutable health values for one entity instance. Behavior belongs to server systems. */
class HealthComponent(
    var currentHealth: Float,
    var maxHealth: Float,
) : Component
