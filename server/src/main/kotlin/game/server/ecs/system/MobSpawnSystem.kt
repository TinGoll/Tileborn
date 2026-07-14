package game.server.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import game.server.ecs.component.MobComponent
import game.server.ecs.component.SpawnOriginComponent
import game.shared.definition.DefinitionRegistry
import game.shared.map.NpcSpawnPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Maintains server-owned mob populations declared by Tiled spawn points. */
class MobSpawnSystem(
    private val spawnPoints: List<NpcSpawnPoint>,
    definitionRegistry: DefinitionRegistry,
    private val spawnMob: (entityId: Int, definitionId: String, x: Float, y: Float, spawnId: String) -> Entity,
    private val random: Random = Random.Default,
    firstEntityId: Int = FIRST_MOB_ENTITY_ID,
) : EntitySystem(PRIORITY) {
    private val respawnRemainingBySpawnId = mutableMapOf<String, Float>()
    private val initializedSpawnIds = mutableSetOf<String>()
    private var nextEntityId = firstEntityId
    private var mobEntities: ImmutableArray<Entity>? = null

    init {
        require(spawnPoints.map(NpcSpawnPoint::spawnId).distinct().size == spawnPoints.size) {
            "NPC spawnId values must be unique"
        }
        spawnPoints.forEach { definitionRegistry.requireMob(it.mobDefinitionId) }
    }

    override fun addedToEngine(engine: Engine) {
        mobEntities = engine.getEntitiesFor(MOB_FAMILY)
    }

    override fun removedFromEngine(engine: Engine) {
        mobEntities = null
    }

    override fun update(deltaTime: Float) {
        val safeDelta = deltaTime.coerceAtLeast(0f)
        respawnRemainingBySpawnId.replaceAll { _, remaining -> (remaining - safeDelta).coerceAtLeast(0f) }

        for (spawnPoint in spawnPoints) {
            val alive = aliveCount(spawnPoint.spawnId)
            if (initializedSpawnIds.add(spawnPoint.spawnId)) {
                repeat((spawnPoint.maxAlive - alive).coerceAtLeast(0)) { createMob(spawnPoint) }
                continue
            }
            if (alive >= spawnPoint.maxAlive) continue
            if (respawnRemainingBySpawnId.getOrDefault(spawnPoint.spawnId, 0f) > 0f) continue

            createMob(spawnPoint)
            if (alive + 1 < spawnPoint.maxAlive) {
                respawnRemainingBySpawnId[spawnPoint.spawnId] = spawnPoint.respawnSeconds
            } else {
                respawnRemainingBySpawnId.remove(spawnPoint.spawnId)
            }
        }
    }

    fun scheduleRespawn(spawnId: String) {
        val spawnPoint = spawnPoints.firstOrNull { it.spawnId == spawnId } ?: return
        respawnRemainingBySpawnId[spawnId] = maxOf(
            respawnRemainingBySpawnId.getOrDefault(spawnId, 0f),
            spawnPoint.respawnSeconds,
        )
    }

    fun aliveCount(spawnId: String): Int {
        val entities = mobEntities ?: return 0
        var count = 0
        for (entity in entities) {
            if (ORIGIN_MAPPER.get(entity).spawnId == spawnId) count += 1
        }
        return count
    }

    private fun createMob(spawnPoint: NpcSpawnPoint) {
        val (x, y) = randomPosition(spawnPoint)
        spawnMob(nextEntityId--, spawnPoint.mobDefinitionId, x, y, spawnPoint.spawnId)
    }

    private fun randomPosition(spawnPoint: NpcSpawnPoint): Pair<Float, Float> {
        if (spawnPoint.spawnRadius == 0f) return spawnPoint.x to spawnPoint.y
        val angle = random.nextDouble() * PI * 2.0
        val distance = sqrt(random.nextDouble()) * spawnPoint.spawnRadius
        return (spawnPoint.x + cos(angle).toFloat() * distance.toFloat()) to
            (spawnPoint.y + sin(angle).toFloat() * distance.toFloat())
    }

    companion object {
        const val PRIORITY = 90
        const val FIRST_MOB_ENTITY_ID = -1
        private val ORIGIN_MAPPER = ComponentMapper.getFor(SpawnOriginComponent::class.java)
        private val MOB_FAMILY = Family.all(MobComponent::class.java, SpawnOriginComponent::class.java).get()
    }
}
