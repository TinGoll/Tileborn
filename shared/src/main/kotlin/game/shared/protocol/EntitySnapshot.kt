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
    val entityKind: NetworkEntityKind = NetworkEntityKind.PLAYER,
    val definitionId: String? = null,
)

/** Gameplay identity needed by clients without exposing server-only ECS components. */
enum class NetworkEntityKind {
    PLAYER,
    MOB,
}
