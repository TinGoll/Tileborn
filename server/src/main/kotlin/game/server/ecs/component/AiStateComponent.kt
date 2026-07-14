package game.server.ecs.component

import com.badlogic.ashley.core.Component

/** Server-owned runtime state and tuning for one mob AI controller. */
class AiStateComponent(
    var state: AiState = AiState.IDLE,
    val aggroRadius: Float,
    val attackRadius: Float,
) : Component

enum class AiState {
    IDLE,
    CHASE,
    ATTACK,
    RETURN,
    DEAD,
}
