package game.shared.ecs.component

import com.badlogic.ashley.core.Component

/** Stable network identity shared by authoritative and replicated entities. */
class NetworkIdentityComponent(
    var networkEntityId: Long = UNASSIGNED_NETWORK_ENTITY_ID,
) : Component {
    companion object {
        const val UNASSIGNED_NETWORK_ENTITY_ID: Long = 0L
    }
}
