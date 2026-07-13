package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Stable reference from runtime ECS state to immutable static configuration. */
class DefinitionIdComponent(
    val definitionId: String,
) : Component
