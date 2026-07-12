package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.shared.constants.InterestManagementConstants
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestManagementTest {
    @Test
    fun `entity inside visibility radius is included in recipient snapshot`() = withWorld { world ->
        world.spawnPlayer(serverEntityId = RECIPIENT_ID)
        val nearby = world.spawnPlayer(serverEntityId = NEARBY_ID)
        nearby.transform().x += InterestManagementConstants.VISIBILITY_RADIUS_WORLD_UNITS - 0.1f

        val snapshot = world.buildSnapshotForRecipient(RECIPIENT_ID, serverTick = 1L)

        assertEquals(setOf(RECIPIENT_ID, NEARBY_ID), snapshot.entities.map { it.entityId }.toSet())
    }

    @Test
    fun `entity outside visibility radius is excluded from recipient snapshot`() = withWorld { world ->
        world.spawnPlayer(serverEntityId = RECIPIENT_ID)
        val distant = world.spawnPlayer(serverEntityId = DISTANT_ID)
        distant.transform().x += InterestManagementConstants.VISIBILITY_RADIUS_WORLD_UNITS + 0.1f

        val snapshot = world.buildSnapshotForRecipient(RECIPIENT_ID, serverTick = 1L)

        assertEquals(setOf(RECIPIENT_ID), snapshot.entities.map { it.entityId }.toSet())
    }

    @Test
    fun `recipient snapshot adds and removes entity as it crosses visibility boundary`() = withWorld { world ->
        world.spawnPlayer(serverEntityId = RECIPIENT_ID)
        val moving = world.spawnPlayer(serverEntityId = NEARBY_ID)
        val transform = moving.transform()

        transform.x += InterestManagementConstants.VISIBILITY_RADIUS_WORLD_UNITS + 1f
        val outside = world.buildSnapshotForRecipient(RECIPIENT_ID, serverTick = 1L)
        assertFalse(outside.entities.any { it.entityId == NEARBY_ID })

        transform.x -= 2f
        val entered = world.buildSnapshotForRecipient(RECIPIENT_ID, serverTick = 2L)
        assertTrue(entered.entities.any { it.entityId == NEARBY_ID })

        transform.x += 2f
        val left = world.buildSnapshotForRecipient(RECIPIENT_ID, serverTick = 3L)
        assertFalse(left.entities.any { it.entityId == NEARBY_ID })
    }

    @Test
    fun `snapshot filters multiple entities independently`() = withWorld { world ->
        world.spawnPlayer(serverEntityId = RECIPIENT_ID)
        val nearby = world.spawnPlayer(serverEntityId = NEARBY_ID)
        val distant = world.spawnPlayer(serverEntityId = DISTANT_ID)
        nearby.transform().x += 1f
        distant.transform().x += InterestManagementConstants.VISIBILITY_RADIUS_WORLD_UNITS + 1f

        val snapshot = world.buildSnapshotForRecipient(RECIPIENT_ID, serverTick = 1L)

        assertEquals(setOf(RECIPIENT_ID, NEARBY_ID), snapshot.entities.map { it.entityId }.toSet())
    }

    private fun com.badlogic.ashley.core.Entity.transform(): TransformComponent =
        requireNotNull(getComponent(TransformComponent::class.java))

    private fun withWorld(block: (ServerWorld) -> Unit) {
        val application = if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
        val world = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            block(world)
        } finally {
            world.dispose()
            application?.exit()
        }
    }

    private companion object {
        const val RECIPIENT_ID = 1
        const val NEARBY_ID = 2
        const val DISTANT_ID = 3
    }
}
