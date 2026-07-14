package game.server.ecs.component

import com.badlogic.ashley.core.Component

/** Runtime-only NPC controller state. Behavior belongs to server systems. */
class NpcControllerComponent(
    var targetEntityId: Long = NO_TARGET_ENTITY_ID,
) : Component {
    companion object {
        const val NO_TARGET_ENTITY_ID: Long = 0L
    }
}
