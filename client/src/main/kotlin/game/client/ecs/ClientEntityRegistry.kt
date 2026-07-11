package game.client.ecs

import com.badlogic.ashley.core.Entity

/** Maps authoritative server entity ids to their client-side ECS representations. */
class ClientEntityRegistry {
    private val entitiesByServerId = mutableMapOf<Int, Entity>()

    fun get(serverEntityId: Int): Entity? = entitiesByServerId[serverEntityId]

    fun put(serverEntityId: Int, entity: Entity) {
        entitiesByServerId[serverEntityId] = entity
    }

    fun remove(serverEntityId: Int): Entity? = entitiesByServerId.remove(serverEntityId)

    fun serverEntityIds(): Set<Int> = entitiesByServerId.keys.toSet()

    fun clear(): Collection<Entity> = entitiesByServerId.values.toList().also { entitiesByServerId.clear() }
}
