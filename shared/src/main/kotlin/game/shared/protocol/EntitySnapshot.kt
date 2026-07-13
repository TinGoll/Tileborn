package game.shared.protocol

import game.shared.ecs.component.CharacterState

data class EntitySnapshot(
    val entityId: Int,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val currentHealth: Float,
    val maxHealth: Float,
    val movementSpeed: Float,
    val characterState: CharacterState,
)
