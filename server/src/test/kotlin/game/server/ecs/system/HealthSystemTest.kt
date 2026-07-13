package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import game.shared.ecs.component.HealthComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthSystemTest {
    @Test
    fun `damage decreases current health`() {
        val fixture = fixture(currentHealth = 100f, maxHealth = 100f)

        fixture.system.applyDamage(fixture.entity, 25f)

        assertEquals(75f, fixture.health.currentHealth, 0f)
    }

    @Test
    fun `health is clamped at lower and upper bounds`() {
        val fixture = fixture(currentHealth = 10f, maxHealth = 100f)

        fixture.system.applyDamage(fixture.entity, 50f)
        assertEquals(0f, fixture.health.currentHealth, 0f)

        fixture.system.restoreHealth(fixture.entity, 150f)
        assertEquals(100f, fixture.health.currentHealth, 0f)
    }

    @Test
    fun `system normalizes externally assigned health`() {
        val fixture = fixture(currentHealth = 150f, maxHealth = 100f)

        fixture.engine.update(0f)

        assertEquals(100f, fixture.health.currentHealth, 0f)
        fixture.health.currentHealth = -20f
        fixture.engine.update(0f)
        assertEquals(0f, fixture.health.currentHealth, 0f)
    }

    private fun fixture(currentHealth: Float, maxHealth: Float): Fixture {
        val engine = Engine()
        val system = HealthSystem()
        val health = HealthComponent(currentHealth, maxHealth)
        val entity = engine.createEntity().apply { add(health) }
        engine.addEntity(entity)
        engine.addSystem(system)
        return Fixture(engine, system, entity, health)
    }

    private data class Fixture(
        val engine: Engine,
        val system: HealthSystem,
        val entity: com.badlogic.ashley.core.Entity,
        val health: HealthComponent,
    )
}
