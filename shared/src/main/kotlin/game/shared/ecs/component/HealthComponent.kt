package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Mutable health of one entity instance; maximum health remains in its definition. */
class HealthComponent(
    var currentHealth: Float,
) : Component
