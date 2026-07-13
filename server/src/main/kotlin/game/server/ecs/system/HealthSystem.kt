package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import game.shared.ecs.component.HealthComponent

/** Owns authoritative health mutation and keeps health values within valid bounds. */
class HealthSystem : IteratingSystem(FAMILY, PRIORITY) {
    fun applyDamage(entity: Entity, amount: Float): Float {
        require(amount.isFinite() && amount >= 0f) { "Damage must be finite and non-negative." }
        val health = requireHealth(entity)
        health.currentHealth -= amount
        normalize(health)
        return health.currentHealth
    }

    fun restoreHealth(entity: Entity, amount: Float): Float {
        require(amount.isFinite() && amount >= 0f) { "Restored health must be finite and non-negative." }
        val health = requireHealth(entity)
        health.currentHealth += amount
        normalize(health)
        return health.currentHealth
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        normalize(HEALTH_MAPPER.get(entity))
    }

    private fun requireHealth(entity: Entity): HealthComponent =
        HEALTH_MAPPER.get(entity) ?: error("Entity has no HealthComponent.")

    private fun normalize(health: HealthComponent) {
        if (!health.maxHealth.isFinite() || health.maxHealth < 0f) health.maxHealth = 0f
        health.currentHealth = health.currentHealth
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, health.maxHealth)
            ?: 0f
    }

    private companion object {
        const val PRIORITY = 50
        val HEALTH_MAPPER: ComponentMapper<HealthComponent> =
            ComponentMapper.getFor(HealthComponent::class.java)
        val FAMILY: Family = Family.all(HealthComponent::class.java).get()
    }
}
