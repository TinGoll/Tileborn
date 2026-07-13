package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import game.client.ecs.component.InterpolatedTransformComponent
import game.client.network.SnapshotBuffer
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import game.shared.protocol.EntitySnapshot

/** Advances delayed server time and writes smooth render positions for remote entities only. */
class SnapshotInterpolationSystem(
    private val interpolationDelayTicks: Float = DEFAULT_INTERPOLATION_DELAY_TICKS,
    private val serverTicksPerSecond: Float = DEFAULT_SERVER_TICKS_PER_SECOND,
    private val maxInterpolationDistance: Float = SnapshotBuffer.DEFAULT_MAX_INTERPOLATION_DISTANCE,
) : EntitySystem(PRIORITY) {
    private val buffersByEntityId = mutableMapOf<Long, SnapshotBuffer>()
    private var remoteEntities: ImmutableArray<Entity>? = null
    private var latestServerTick: Long? = null
    private var renderServerTick: Float? = null

    init {
        require(interpolationDelayTicks >= 0f) { "Interpolation delay cannot be negative." }
        require(serverTicksPerSecond > 0f) { "Server tick rate must be positive." }
    }

    fun recordSnapshot(serverTick: Long, snapshot: EntitySnapshot) {
        buffersByEntityId.getOrPut(snapshot.entityId.toLong()) { SnapshotBuffer() }.add(serverTick, snapshot)
        latestServerTick = maxOf(latestServerTick ?: serverTick, serverTick)
        if (renderServerTick == null) renderServerTick = serverTick - interpolationDelayTicks
    }

    fun removeEntity(serverEntityId: Int) {
        buffersByEntityId.remove(serverEntityId.toLong())
    }

    fun clearSnapshots() {
        buffersByEntityId.clear()
        latestServerTick = null
        renderServerTick = null
    }

    override fun addedToEngine(engine: Engine) {
        remoteEntities = engine.getEntitiesFor(FAMILY)
    }

    override fun removedFromEngine(engine: Engine) {
        remoteEntities = null
        clearSnapshots()
    }

    override fun update(deltaTime: Float) {
        val latest = latestServerTick ?: return
        val latestAllowedRenderTick = latest - interpolationDelayTicks
        val currentRenderTick = renderServerTick ?: latestAllowedRenderTick
        renderServerTick = (currentRenderTick + deltaTime.coerceAtLeast(0f) * serverTicksPerSecond)
            .coerceAtMost(latestAllowedRenderTick)

        val targetTick = renderServerTick ?: return
        for (entity in remoteEntities ?: return) {
            val entityId = IDENTITY_MAPPER.get(entity).networkEntityId
            val snapshot = buffersByEntityId[entityId]?.sample(targetTick, maxInterpolationDistance) ?: continue
            val transform = INTERPOLATED_TRANSFORM_MAPPER.get(entity)
            transform.x = snapshot.x
            transform.y = snapshot.y
            PHYSICS_MAPPER.get(entity)?.let { physics ->
                val physicsTransform = TRANSFORM_MAPPER.get(entity)
                physicsTransform.x = snapshot.x
                physicsTransform.y = snapshot.y
                physics.synchronizeTransformToBody = true
            }
        }
    }

    private companion object {
        const val PRIORITY = 150
        // Two server ticks absorb ordinary packet jitter while playback still advances at the
        // authoritative tick rate. The extra buffered snapshot prevents freeze-and-catch-up motion.
        const val DEFAULT_INTERPOLATION_DELAY_TICKS = 2f
        const val DEFAULT_SERVER_TICKS_PER_SECOND = 20f
        val IDENTITY_MAPPER: ComponentMapper<NetworkIdentityComponent> =
            ComponentMapper.getFor(NetworkIdentityComponent::class.java)
        val INTERPOLATED_TRANSFORM_MAPPER: ComponentMapper<InterpolatedTransformComponent> =
            ComponentMapper.getFor(InterpolatedTransformComponent::class.java)
        val PHYSICS_MAPPER: ComponentMapper<PhysicsBodyComponent> =
            ComponentMapper.getFor(PhysicsBodyComponent::class.java)
        val TRANSFORM_MAPPER: ComponentMapper<TransformComponent> =
            ComponentMapper.getFor(TransformComponent::class.java)
        val FAMILY: Family = Family.all(
            NetworkIdentityComponent::class.java,
            InterpolatedTransformComponent::class.java,
        ).exclude(game.client.ecs.component.LocalPlayerComponent::class.java).get()
    }
}
