package game.client.ecs

import com.badlogic.ashley.core.Entity
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.protocol.EntitySnapshot

/** Copies server-authored character attributes from a snapshot into client ECS data. */
object CharacterSnapshotApplier {
    fun apply(entity: Entity, snapshot: EntitySnapshot) {
        entity.getComponent(HealthComponent::class.java)?.let { health ->
            health.currentHealth = snapshot.currentHealth
            health.maxHealth = snapshot.maxHealth
        } ?: entity.add(HealthComponent(snapshot.currentHealth, snapshot.maxHealth))

        entity.getComponent(MovementSpeedComponent::class.java)?.let { movementSpeed ->
            movementSpeed.movementSpeed = snapshot.movementSpeed
        } ?: entity.add(MovementSpeedComponent(snapshot.movementSpeed))

        entity.getComponent(CharacterStateComponent::class.java)?.let { state ->
            state.state = snapshot.characterState
        } ?: entity.add(CharacterStateComponent(snapshot.characterState))
    }
}
