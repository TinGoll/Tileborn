package game.server.ecs.component

import com.badlogic.ashley.core.Component

/** Network identity of the player currently selected by server AI. */
class AggroTargetComponent(
    var targetEntityId: Long? = null,
) : Component
