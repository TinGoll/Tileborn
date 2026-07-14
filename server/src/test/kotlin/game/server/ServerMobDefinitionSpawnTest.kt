package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.ecs.component.MobComponent
import game.server.ecs.component.NpcControllerComponent
import game.server.ecs.component.SpawnOriginComponent
import game.shared.definition.DefinitionRegistry
import game.shared.definition.MobDefinition
import game.shared.ecs.component.DefinitionIdComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.protocol.NetworkEntityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerMobDefinitionSpawnTest {
    @Test
    fun `mob is spawned from its definition`() {
        val application = ensureHeadlessApplication()
        val definition = mobDefinition()
        val world = ServerWorld(
            mapId = "debug_map",
            mapPath = "maps/debug_map.tmx",
            definitionRegistry = DefinitionRegistry(listOf(definition), emptyList()),
        )
        try {
            val mob = world.spawnMob(serverEntityId = 500, definitionId = definition.id, x = 3f, y = 4f)

            assertEquals(definition.id, mob.getComponent(DefinitionIdComponent::class.java).definitionId)
            assertEquals(definition.maxHealth, mob.getComponent(HealthComponent::class.java).currentHealth, 0f)
            assertEquals(definition.maxHealth, mob.getComponent(HealthComponent::class.java).maxHealth, 0f)
            assertEquals(
                definition.collisionRadius,
                mob.getComponent(PhysicsBodyComponent::class.java).body.fixtureList.single().shape.radius,
                0f,
            )
            assertNotNull(mob.getComponent(MobComponent::class.java))
            assertNotNull(mob.getComponent(NpcControllerComponent::class.java))
            assertEquals("manual:500", mob.getComponent(SpawnOriginComponent::class.java).spawnId)
            val snapshot = world.buildSnapshot(serverTick = 1L).entities.single()
            assertEquals(NetworkEntityKind.MOB, snapshot.entityKind)
            assertEquals(definition.id, snapshot.definitionId)
            assertEquals(definition.collisionRadius, snapshot.collisionRadius, 0f)
        } finally {
            world.dispose()
            application?.exit()
        }
    }

    @Test
    fun `unknown mob definition id fails with a clear error`() {
        val application = ensureHeadlessApplication()
        val world = ServerWorld(
            mapId = "debug_map",
            mapPath = "maps/debug_map.tmx",
            definitionRegistry = DefinitionRegistry.empty(),
        )
        try {
            val exception = assertThrows(IllegalStateException::class.java) {
                world.spawnMob(serverEntityId = 500, definitionId = "unknown", x = 0f, y = 0f)
            }
            assertEquals("Unknown mob definitionId 'unknown'", exception.message)
        } finally {
            world.dispose()
            application?.exit()
        }
    }

    private fun mobDefinition() = MobDefinition(
        id = "slime",
        displayName = "Test Mob",
        maxHealth = 75f,
        movementSpeed = 2.5f,
        collisionRadius = 0.6f,
        aggroRadius = 8f,
        attackRadius = 1f,
        attackDamage = 9f,
        attackCooldown = 1.5f,
    )

    private fun ensureHeadlessApplication(): HeadlessApplication? =
        if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
}
