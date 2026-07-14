package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiState
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.HomePositionComponent
import game.server.ecs.component.MobComponent
import game.shared.ecs.component.AttackComponent
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.CooldownComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobMovementIntentSystemTest {
    @Test
    fun `returning mob moves toward home and becomes idle on arrival`() {
        val engine = Engine()
        engine.addSystem(MobAiSystem())
        engine.addSystem(MobMovementIntentSystem())
        val mob = engine.createEntity().apply {
            add(MobComponent())
            add(TransformComponent(3f, 4f))
            add(VelocityComponent())
            add(MovementSpeedComponent(2f))
            add(HealthComponent(20f, 20f))
            add(CharacterStateComponent())
            add(AiStateComponent(AiState.RETURN, aggroRadius = 5f, attackRadius = 1f))
            add(AggroTargetComponent())
            add(HomePositionComponent(0f, 0f))
            add(AttackComponent(range = 1f, damage = 3f, minimumDirectionDot = 0f))
            add(CooldownComponent(durationSeconds = 1f))
        }.also(engine::addEntity)

        engine.update(0.1f)
        val velocity = mob.getComponent(VelocityComponent::class.java)
        assertTrue(velocity.x < 0f)
        assertTrue(velocity.y < 0f)
        assertEquals(4f, velocity.x * velocity.x + velocity.y * velocity.y, 0.0001f)

        val transform = mob.getComponent(TransformComponent::class.java)
        repeat(30) {
            transform.x += velocity.x * 0.1f
            transform.y += velocity.y * 0.1f
            engine.update(0.1f)
        }

        assertEquals(AiState.IDLE, mob.getComponent(AiStateComponent::class.java).state)
        assertEquals(0f, transform.x, 0.0001f)
        assertEquals(0f, transform.y, 0.0001f)
        assertEquals(0f, velocity.x, 0f)
        assertEquals(0f, velocity.y, 0f)
    }
}
