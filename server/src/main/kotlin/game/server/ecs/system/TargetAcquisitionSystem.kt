package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.ashley.utils.ImmutableArray
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiState
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.MobComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent

/** Selects the nearest living player for idle mobs, using authoritative positions only. */
class TargetAcquisitionSystem : IteratingSystem(MOB_FAMILY, PRIORITY) {
    private var players: ImmutableArray<Entity>? = null

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        players = engine.getEntitiesFor(PLAYER_FAMILY)
    }

    override fun removedFromEngine(engine: Engine) {
        players = null
        super.removedFromEngine(engine)
    }

    override fun processEntity(mob: Entity, deltaTime: Float) {
        val ai = AI_MAPPER.get(mob)
        if (ai.state != AiState.IDLE) return

        val target = TARGET_MAPPER.get(mob)
        target.targetEntityId = null
        val mobTransform = TRANSFORM_MAPPER.get(mob)
        val aggroRadiusSquared = ai.aggroRadius * ai.aggroRadius
        if (!aggroRadiusSquared.isFinite()) return

        var nearestId: Long? = null
        var nearestDistanceSquared = Float.POSITIVE_INFINITY
        val playerEntities = players ?: return
        for (player in playerEntities) {
            if (!isAlive(player)) continue
            val playerTransform = TRANSFORM_MAPPER.get(player)
            val deltaX = playerTransform.x - mobTransform.x
            val deltaY = playerTransform.y - mobTransform.y
            val distanceSquared = deltaX * deltaX + deltaY * deltaY
            if (!distanceSquared.isFinite() || distanceSquared > aggroRadiusSquared) continue

            val playerId = IDENTITY_MAPPER.get(player).networkEntityId
            if (distanceSquared < nearestDistanceSquared ||
                (distanceSquared == nearestDistanceSquared && (nearestId == null || playerId < nearestId))
            ) {
                nearestId = playerId
                nearestDistanceSquared = distanceSquared
            }
        }
        target.targetEntityId = nearestId
    }

    private fun isAlive(entity: Entity): Boolean =
        STATE_MAPPER.get(entity).state == CharacterState.ALIVE &&
            HEALTH_MAPPER.get(entity).currentHealth > 0f

    companion object {
        const val PRIORITY = 82
        private val AI_MAPPER = ComponentMapper.getFor(AiStateComponent::class.java)
        private val TARGET_MAPPER = ComponentMapper.getFor(AggroTargetComponent::class.java)
        private val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        private val IDENTITY_MAPPER = ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        private val STATE_MAPPER = ComponentMapper.getFor(CharacterStateComponent::class.java)
        private val HEALTH_MAPPER = ComponentMapper.getFor(HealthComponent::class.java)
        private val MOB_FAMILY = Family.all(
            MobComponent::class.java,
            AiStateComponent::class.java,
            AggroTargetComponent::class.java,
            TransformComponent::class.java,
        ).get()
        private val PLAYER_FAMILY = Family.all(
            PlayerInputComponent::class.java,
            NetworkIdentityComponent::class.java,
            TransformComponent::class.java,
            CharacterStateComponent::class.java,
            HealthComponent::class.java,
        ).get()
    }
}
