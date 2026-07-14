package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiState
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.HomePositionComponent
import game.server.ecs.component.MobComponent
import game.shared.ecs.component.AttackComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.CooldownComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobAiSystemTest {
    @Test
    fun `mob transitions through chase attack return and dead states`() {
        val engine = Engine()
        engine.addSystem(TargetAcquisitionSystem())
        engine.addSystem(MobAiSystem())
        val mob = mob(engine, x = 0f, y = 0f)
        val player = player(engine, entityId = 7L, x = 3f, y = 0f)
        val ai = mob.getComponent(AiStateComponent::class.java)
        val target = mob.getComponent(AggroTargetComponent::class.java)
        val attacks = mob.getComponent(AttackComponent::class.java).pendingAttacks

        engine.update(0.05f)
        assertEquals(AiState.CHASE, ai.state)
        assertEquals(7L, target.targetEntityId)
        assertTrue(attacks.isEmpty())

        player.getComponent(TransformComponent::class.java).x = 0.75f
        engine.update(0.05f)
        assertEquals(AiState.ATTACK, ai.state)
        assertEquals(1, attacks.size)

        attacks.clear()
        player.getComponent(TransformComponent::class.java).x = 2f
        engine.update(0.05f)
        assertEquals(AiState.CHASE, ai.state)
        assertTrue(attacks.isEmpty())

        mob.getComponent(TransformComponent::class.java).x = 1f
        player.getComponent(TransformComponent::class.java).x = 8f
        engine.update(0.05f)
        assertEquals(AiState.RETURN, ai.state)
        assertEquals(null, target.targetEntityId)

        mob.getComponent(HealthComponent::class.java).currentHealth = 0f
        engine.update(0.05f)
        assertEquals(AiState.DEAD, ai.state)
    }

    private fun mob(engine: Engine, x: Float, y: Float): Entity =
        engine.createEntity().apply {
            add(MobComponent())
            add(TransformComponent(x, y))
            add(HealthComponent(20f, 20f))
            add(CharacterStateComponent())
            add(AiStateComponent(aggroRadius = 5f, attackRadius = 1f))
            add(AggroTargetComponent())
            add(HomePositionComponent(x, y))
            add(AttackComponent(range = 1f, damage = 3f, minimumDirectionDot = 0f))
            add(CooldownComponent(durationSeconds = 1f))
        }.also(engine::addEntity)

    private fun player(engine: Engine, entityId: Long, x: Float, y: Float): Entity =
        engine.createEntity().apply {
            add(NetworkIdentityComponent(entityId))
            add(TransformComponent(x, y))
            add(HealthComponent(100f, 100f))
            add(CharacterStateComponent())
            add(PlayerInputComponent())
        }.also(engine::addEntity)
}
