package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.server.ecs.component.MobComponent
import game.server.ecs.component.SpawnOriginComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent

/** Removes dead mobs from the authoritative world and starts their server-side respawn delay. */
class MobDespawnSystem(
    private val scheduleRespawn: (spawnId: String) -> Unit,
) : IteratingSystem(FAMILY, PRIORITY) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (STATE_MAPPER.get(entity).state != CharacterState.DEAD) return
        scheduleRespawn(ORIGIN_MAPPER.get(entity).spawnId)
        engine.removeEntity(entity)
    }

    companion object {
        const val PRIORITY = 87
        private val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        private val ORIGIN_MAPPER = ComponentMapper.getFor(SpawnOriginComponent::class.java)
        private val FAMILY = Family.all(
            MobComponent::class.java,
            SpawnOriginComponent::class.java,
            CharacterStateComponent::class.java,
        ).get()
    }
}
