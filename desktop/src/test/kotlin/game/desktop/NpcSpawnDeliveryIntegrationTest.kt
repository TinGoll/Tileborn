package game.desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.client.debug.ConnectionState
import game.client.network.TcpGameClient
import game.server.ServerWorld
import game.server.network.TcpGameServer
import game.shared.definition.DefinitionRegistry
import game.shared.definition.MobDefinition
import game.shared.protocol.NetworkEntityKind
import game.shared.protocol.WorldSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcSpawnDeliveryIntegrationTest {
    @Test
    fun `client receives server spawned mob in world snapshot`() {
        val application = if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
        val world = ServerWorld(
            mapId = "debug_map",
            mapPath = "maps/debug_map.tmx",
            definitionRegistry = DefinitionRegistry(listOf(slimeDefinition()), emptyList()),
        )
        try {
            world.update(1f / 20f)
            TcpGameServer(
                port = 0,
                mapIdProvider = { world.gameMapData.mapId },
                serverTickProvider = { 1L },
                initialSnapshotProvider = { playerEntityId ->
                    world.spawnPlayer(playerEntityId)
                    world.buildSnapshot(1L)
                },
                logger = {},
            ).use { server ->
                server.start()
                val client = TcpGameClient(port = server.localPort, pingIntervalMillis = 60_000L, logger = {})
                try {
                    client.connect()
                    assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
                    assertTrue(waitUntil {
                        (client.lastServerMessage as? WorldSnapshot)?.entities?.any {
                            it.entityKind == NetworkEntityKind.MOB
                        } == true
                    })

                    val snapshot = client.lastServerMessage as WorldSnapshot
                    val mobs = snapshot.entities.filter { it.entityKind == NetworkEntityKind.MOB }
                    assertEquals(3, mobs.size)
                    assertTrue(mobs.all { it.entityId < 0 && it.definitionId == "slime" })
                    assertEquals(3, mobs.map { it.entityId }.distinct().size)
                } finally {
                    client.close()
                }
            }
        } finally {
            world.dispose()
            application?.exit()
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10L)
        }
        return condition()
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
