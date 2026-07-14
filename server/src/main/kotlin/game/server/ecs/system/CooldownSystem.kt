package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.shared.ecs.component.CooldownComponent

/** Advances authoritative cooldowns using the fixed server timestep. */
class CooldownSystem : IteratingSystem(FAMILY, PRIORITY) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val cooldown = COOLDOWN_MAPPER.get(entity)
        val elapsed = deltaTime.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        cooldown.remainingSeconds = (cooldown.remainingSeconds - elapsed).coerceAtLeast(0f)
    }

    companion object {
        const val PRIORITY = 110
        private val COOLDOWN_MAPPER = ComponentMapper.getFor(CooldownComponent::class.java)
        private val FAMILY: Family = Family.all(CooldownComponent::class.java).get()
    }
}
