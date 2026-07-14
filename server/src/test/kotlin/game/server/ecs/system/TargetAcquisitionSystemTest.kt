package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.MobComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetAcquisitionSystemTest {
    @Test
    fun `idle mob selects nearest living player`() {
        val engine = Engine()
        engine.addSystem(TargetAcquisitionSystem())
        val mob = mob(engine, x = 0f, y = 0f, aggroRadius = 5f)
        player(engine, entityId = 10L, x = 4f, y = 0f)
        player(engine, entityId = 20L, x = 2f, y = 0f)
        player(engine, entityId = 30L, x = 1f, y = 0f, state = CharacterState.DEAD)

        engine.update(0.05f)

        assertEquals(20L, mob.getComponent(AggroTargetComponent::class.java).targetEntityId)
    }

    @Test
    fun `idle mob has no target when all players are outside aggro radius`() {
        val engine = Engine()
        engine.addSystem(TargetAcquisitionSystem())
        val mob = mob(engine, x = 0f, y = 0f, aggroRadius = 5f)
        player(engine, entityId = 10L, x = 5.01f, y = 0f)

        engine.update(0.05f)

        assertNull(mob.getComponent(AggroTargetComponent::class.java).targetEntityId)
    }

    private fun mob(engine: Engine, x: Float, y: Float, aggroRadius: Float): Entity =
        engine.createEntity().apply {
            add(MobComponent())
            add(TransformComponent(x, y))
            add(AiStateComponent(aggroRadius = aggroRadius, attackRadius = 1f))
            add(AggroTargetComponent())
        }.also(engine::addEntity)

    private fun player(
        engine: Engine,
        entityId: Long,
        x: Float,
        y: Float,
        state: CharacterState = CharacterState.ALIVE,
    ) {
        engine.createEntity().apply {
            add(NetworkIdentityComponent(entityId))
            add(TransformComponent(x, y))
            add(HealthComponent(if (state == CharacterState.ALIVE) 100f else 0f, 100f))
            add(CharacterStateComponent(state))
            add(PlayerInputComponent())
        }.also(engine::addEntity)
    }
}
