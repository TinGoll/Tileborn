package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import game.server.ecs.component.ServerAuthorityComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.protocol.DamageEvent
import game.shared.protocol.EntityDiedEvent

/** Applies each server-created DamageEvent at most once and emits authoritative results. */
class DamageSystem(
    private val healthSystem: HealthSystem,
    private val characterStateSystem: CharacterStateSystem,
    private val combatEventSystem: CombatEventSystem,
) : EntitySystem(PRIORITY) {
    private val processedEventIds = mutableSetOf<Long>()

    override fun update(deltaTime: Float) {
        combatEventSystem.drainPendingDamageEvents().forEach(::applyDamage)
    }

    /** Returns false when an event was already processed or its authoritative target does not exist. */
    fun applyDamage(event: DamageEvent): Boolean {
        if (!processedEventIds.add(event.eventId)) return false
        if (event.currentHealth != null || event.maxHealth != null) return false
        val target = findAuthoritativeTarget(event.targetEntityId) ?: return false
        val health = HEALTH_MAPPER.get(target) ?: return false
        val state = STATE_MAPPER.get(target) ?: return false
        if (state.state != CharacterState.ALIVE || health.currentHealth <= 0f) return false

        val healthBefore = health.currentHealth
        val currentHealth = healthSystem.applyDamage(target, event.amount)
        characterStateSystem.synchronizeState(target)
        combatEventSystem.publish(
            event.copy(
                currentHealth = currentHealth,
                maxHealth = health.maxHealth,
            ),
        )
        if (healthBefore > 0f && currentHealth <= 0f) {
            combatEventSystem.publish(
                EntityDiedEvent(
                    eventId = combatEventSystem.nextEventId(),
                    damageEventId = event.eventId,
                    sourceEntityId = event.sourceEntityId,
                    targetEntityId = event.targetEntityId,
                ),
            )
        }
        return true
    }

    private fun findAuthoritativeTarget(networkEntityId: Int): Entity? =
        engine.entities.firstOrNull { entity ->
            AUTHORITY_MAPPER.get(entity) != null &&
                IDENTITY_MAPPER.get(entity)?.networkEntityId == networkEntityId.toLong()
        }

    companion object {
        const val PRIORITY = 130
        private val AUTHORITY_MAPPER = ComponentMapper.getFor(ServerAuthorityComponent::class.java)
        private val IDENTITY_MAPPER = ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        private val HEALTH_MAPPER = ComponentMapper.getFor(HealthComponent::class.java)
        private val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
    }
}
