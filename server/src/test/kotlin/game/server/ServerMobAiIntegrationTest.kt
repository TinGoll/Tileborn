package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.shared.definition.DefinitionRegistry
import game.shared.definition.MobDefinition
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMobAiIntegrationTest {
    @Test
    fun `authoritative mob movement is included in snapshot`() {
        val application = ensureHeadlessApplication()
        val definition = mobDefinition()
        val world = ServerWorld(
            mapId = "debug_map",
            mapPath = "maps/debug_map.tmx",
            definitionRegistry = DefinitionRegistry(listOf(definition), emptyList()),
        )
        try {
            world.spawnPlayer(serverEntityId = PLAYER_ID)
            world.spawnMob(serverEntityId = MOB_ID, definitionId = definition.id, x = 3f, y = 5f)
            val initial = world.buildSnapshot(serverTick = 0L).entities.single { it.entityId == MOB_ID }

            world.update(0.05f)

            val updated = world.buildSnapshot(serverTick = 1L).entities.single { it.entityId == MOB_ID }
            assertTrue("Mob did not approach the player: ${initial.x} -> ${updated.x}", updated.x > initial.x)
            assertTrue("Snapshot does not contain mob movement intent", updated.velocityX > 0f)
        } finally {
            world.dispose()
            application?.exit()
        }
    }

    @Test
    fun `mob damages player only after entering attack radius`() {
        val application = ensureHeadlessApplication()
        val definition = mobDefinition()
        val world = ServerWorld(
            mapId = "debug_map",
            mapPath = "maps/debug_map.tmx",
            definitionRegistry = DefinitionRegistry(listOf(definition), emptyList()),
        )
        try {
            val player = world.spawnPlayer(serverEntityId = PLAYER_ID)
            val mob = world.spawnMob(serverEntityId = MOB_ID, definitionId = definition.id, x = 3f, y = 5f)
            val playerHealth = player.getComponent(HealthComponent::class.java)

            world.update(0.05f)
            assertEquals(100f, playerHealth.currentHealth, 0f)

            val mobTransform = mob.getComponent(TransformComponent::class.java)
            mobTransform.x = 4.3f
            mobTransform.y = 5f
            mob.getComponent(PhysicsBodyComponent::class.java).synchronizeTransformToBody = true
            world.update(0.05f)

            assertEquals(95f, playerHealth.currentHealth, 0f)
        } finally {
            world.dispose()
            application?.exit()
        }
    }

    private fun mobDefinition() = MobDefinition(
        id = "slime",
        displayName = "Test Slime",
        maxHealth = 30f,
        movementSpeed = 2f,
        collisionRadius = 0.35f,
        aggroRadius = 6f,
        attackRadius = 0.8f,
        attackDamage = 5f,
        attackCooldown = 1.25f,
    )

    private fun ensureHeadlessApplication(): HeadlessApplication? =
        if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null

    private companion object {
        const val PLAYER_ID = 1
        const val MOB_ID = 500
    }
}
