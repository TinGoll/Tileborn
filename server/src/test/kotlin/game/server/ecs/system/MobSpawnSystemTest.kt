package game.server.ecs.system

import com.badlogic.ashley.core.Engine
import game.server.ecs.component.MobComponent
import game.server.ecs.component.SpawnOriginComponent
import game.shared.definition.DefinitionRegistry
import game.shared.definition.MobDefinition
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.map.NpcSpawnPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MobSpawnSystemTest {
    @Test
    fun `spawn population never exceeds max alive`() {
        val engine = Engine()
        val spawnedIds = mutableListOf<Int>()
        val spawnPoint = NpcSpawnPoint(
            spawnId = "slime_camp",
            mobDefinitionId = "slime",
            maxAlive = 2,
            respawnSeconds = 5f,
            spawnRadius = 1f,
            x = 10f,
            y = 20f,
        )
        val system = MobSpawnSystem(
            spawnPoints = listOf(spawnPoint),
            definitionRegistry = DefinitionRegistry(listOf(slimeDefinition()), emptyList()),
            random = Random(31),
            spawnMob = { entityId, _, x, y, spawnId ->
                spawnedIds += entityId
                engine.createEntity().apply {
                    add(NetworkIdentityComponent(entityId.toLong()))
                    add(MobComponent())
                    add(SpawnOriginComponent(spawnId))
                    assertTrue((x - spawnPoint.x) * (x - spawnPoint.x) + (y - spawnPoint.y) * (y - spawnPoint.y) <= 1f)
                }.also(engine::addEntity)
            },
        )
        engine.addSystem(system)

        repeat(100) { engine.update(0.05f) }

        assertEquals(2, system.aliveCount("slime_camp"))
        assertEquals(2, spawnedIds.size)
        assertEquals(2, spawnedIds.distinct().size)
        assertTrue(spawnedIds.all { it < 0 })
    }

    @Test
    fun `despawned mob is replaced only after respawn delay`() {
        val engine = Engine()
        val spawnedIds = mutableListOf<Int>()
        val spawnPoint = NpcSpawnPoint("slime_camp", "slime", 1, 2f, 0f, 10f, 20f)
        val spawnSystem = MobSpawnSystem(
            spawnPoints = listOf(spawnPoint),
            definitionRegistry = DefinitionRegistry(listOf(slimeDefinition()), emptyList()),
            spawnMob = { entityId, _, _, _, spawnId ->
                spawnedIds += entityId
                engine.createEntity().apply {
                    add(NetworkIdentityComponent(entityId.toLong()))
                    add(MobComponent())
                    add(SpawnOriginComponent(spawnId))
                    add(CharacterStateComponent())
                }.also(engine::addEntity)
            },
        )
        engine.addSystem(MobDespawnSystem(spawnSystem::scheduleRespawn))
        engine.addSystem(spawnSystem)
        engine.update(0f)
        engine.entities.single().getComponent(CharacterStateComponent::class.java).state = CharacterState.DEAD

        engine.update(0f)
        engine.update(1f)

        assertEquals(1, spawnedIds.size)
        assertEquals(0, spawnSystem.aliveCount("slime_camp"))

        engine.update(1f)

        assertEquals(2, spawnedIds.size)
        assertEquals(1, spawnSystem.aliveCount("slime_camp"))
        assertTrue(spawnedIds[0] != spawnedIds[1])
    }

    private fun slimeDefinition() = MobDefinition(
        id = "slime",
        displayName = "Slime",
        maxHealth = 30f,
        movementSpeed = 2f,
        collisionRadius = 0.35f,
        aggroRadius = 6f,
        attackRadius = 0.8f,
        attackDamage = 4f,
        attackCooldown = 1f,
    )
}
