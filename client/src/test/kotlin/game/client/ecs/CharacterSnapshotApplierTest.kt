package game.client.ecs

import com.badlogic.ashley.core.Entity
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.protocol.EntitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterSnapshotApplierTest {
    @Test
    fun `authoritative snapshot updates client character components`() {
        val entity = Entity().apply {
            add(HealthComponent(currentHealth = 100f, maxHealth = 100f))
            add(MovementSpeedComponent(4f))
            add(CharacterStateComponent())
        }
        val snapshot = EntitySnapshot(
            entityId = 1,
            x = 0f,
            y = 0f,
            velocityX = 0f,
            velocityY = 0f,
            currentHealth = 0f,
            maxHealth = 120f,
            movementSpeed = 3f,
            characterState = CharacterState.DEAD,
        )

        CharacterSnapshotApplier.apply(entity, snapshot)

        assertEquals(0f, entity.getComponent(HealthComponent::class.java).currentHealth, 0f)
        assertEquals(120f, entity.getComponent(HealthComponent::class.java).maxHealth, 0f)
        assertEquals(3f, entity.getComponent(MovementSpeedComponent::class.java).movementSpeed, 0f)
        assertEquals(CharacterState.DEAD, entity.getComponent(CharacterStateComponent::class.java).state)
    }
}
