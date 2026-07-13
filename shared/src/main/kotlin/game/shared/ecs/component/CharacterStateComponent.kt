package game.shared.ecs.component

import com.badlogic.ashley.core.Component

enum class CharacterState {
    ALIVE,
    DEAD,
    RESPAWNING,
}

/** Server-authored character lifecycle state. */
class CharacterStateComponent(
    var state: CharacterState = CharacterState.ALIVE,
) : Component
