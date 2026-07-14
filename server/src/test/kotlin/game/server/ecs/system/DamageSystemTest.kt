package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import game.server.ecs.component.ServerAuthorityComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.protocol.DamageEvent
import game.shared.protocol.EntityDiedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageSystemTest {
    @Test
    fun `damage event decreases authoritative health`() {
        val fixture = fixture(currentHealth = 100f)

        fixture.combatEvents.queueDamage(damageEvent(eventId = 10L, amount = 25f))
        fixture.engine.update(0f)

        assertEquals(75f, fixture.health.currentHealth, 0f)
        val applied = fixture.combatEvents.drainOutboundEvents().single() as DamageEvent
        assertEquals(75f, applied.currentHealth!!, 0f)
        assertEquals(100f, applied.maxHealth!!, 0f)
    }

    @Test
    fun `lethal damage emits entity died event`() {
        val fixture = fixture(currentHealth = 20f)

        fixture.combatEvents.queueDamage(damageEvent(eventId = 20L, amount = 25f))
        fixture.engine.update(0f)

        assertEquals(0f, fixture.health.currentHealth, 0f)
        assertEquals(CharacterState.DEAD, fixture.state.state)
        val events = fixture.combatEvents.drainOutboundEvents()
        assertTrue(events[0] is DamageEvent)
        val died = events[1] as EntityDiedEvent
        assertEquals(20L, died.damageEventId)
        assertEquals(SOURCE_ENTITY_ID, died.sourceEntityId)
        assertEquals(TARGET_ENTITY_ID, died.targetEntityId)
    }

    @Test
    fun `same damage event is applied only once`() {
        val fixture = fixture(currentHealth = 100f)
        val event = damageEvent(eventId = 30L, amount = 25f)

        fixture.combatEvents.queueDamage(event)
        fixture.combatEvents.queueDamage(event)
        fixture.engine.update(0f)

        assertEquals(75f, fixture.health.currentHealth, 0f)
        assertEquals(1, fixture.combatEvents.drainOutboundEvents().filterIsInstance<DamageEvent>().size)
        assertFalse(fixture.damageSystem.applyDamage(event))
        assertEquals(75f, fixture.health.currentHealth, 0f)
    }

    private fun fixture(currentHealth: Float): Fixture {
        val engine = Engine()
        val healthSystem = HealthSystem()
        val characterStateSystem = CharacterStateSystem()
        val combatEvents = CombatEventSystem()
        val damageSystem = DamageSystem(healthSystem, characterStateSystem, combatEvents)
        val health = HealthComponent(currentHealth = currentHealth, maxHealth = 100f)
        val state = CharacterStateComponent()
        val target = engine.createEntity().apply {
            add(NetworkIdentityComponent(TARGET_ENTITY_ID.toLong()))
            add(health)
            add(state)
            add(ServerAuthorityComponent())
        }
        engine.addEntity(target)
        engine.addSystem(healthSystem)
        engine.addSystem(characterStateSystem)
        engine.addSystem(damageSystem)
        engine.addSystem(combatEvents)
        return Fixture(engine, damageSystem, combatEvents, health, state)
    }

    private fun damageEvent(eventId: Long, amount: Float) = DamageEvent(
        eventId = eventId,
        hitEventId = eventId - 1L,
        sourceEntityId = SOURCE_ENTITY_ID,
        targetEntityId = TARGET_ENTITY_ID,
        amount = amount,
    )

    private data class Fixture(
        val engine: Engine,
        val damageSystem: DamageSystem,
        val combatEvents: CombatEventSystem,
        val health: HealthComponent,
        val state: CharacterStateComponent,
    )

    private companion object {
        const val SOURCE_ENTITY_ID = 1
        const val TARGET_ENTITY_ID = 2
    }
}
