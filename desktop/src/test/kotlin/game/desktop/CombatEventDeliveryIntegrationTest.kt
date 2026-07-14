package game.desktop

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.client.debug.ConnectionState
import game.client.network.TcpGameClient
import game.server.ServerWorld
import game.server.network.TcpGameServer
import game.shared.constants.GameConstants
import game.shared.protocol.AttackCommand
import game.shared.protocol.CombatEvent
import game.shared.protocol.DamageEvent
import game.shared.protocol.HitEvent
import game.shared.protocol.WorldSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatEventDeliveryIntegrationTest {
    @Test
    fun `client receives hit and new health from authoritative combat pipeline`() {
        val application = if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
        val world = ServerWorld("debug_map", "maps/debug_map.tmx")
        try {
            val attackReceived = CountDownLatch(1)
            TcpGameServer(
                port = 0,
                mapIdProvider = { world.gameMapData.mapId },
                serverTickProvider = { 1L },
                initialSnapshotProvider = { playerEntityId ->
                    world.spawnPlayer(playerEntityId)
                    world.spawnPlayer(TARGET_ENTITY_ID)
                    world.buildSnapshot(1L)
                },
                attackCommandHandler = { playerEntityId, command ->
                    world.queueAttack(playerEntityId, command)
                    attackReceived.countDown()
                },
                logger = {},
            ).use { server ->
                server.start()
                val client = TcpGameClient(port = server.localPort, pingIntervalMillis = 60_000L, logger = {})
                try {
                    client.connect()
                    assertTrue(waitUntil { client.connectionState == ConnectionState.CONNECTED })
                    assertTrue(waitUntil { client.lastServerMessage is WorldSnapshot })

                    client.sendAttack(
                        AttackCommand(
                            inputSequence = 1L,
                            clientTick = 1L,
                            aimX = 1f,
                            aimY = 0f,
                            optionalTargetEntityId = TARGET_ENTITY_ID,
                        ),
                    )
                    assertTrue(attackReceived.await(2L, TimeUnit.SECONDS))

                    world.update(0f)
                    world.drainCombatEvents().forEach(server::broadcastCombatEvent)
                    server.broadcastSnapshot(world.buildSnapshot(2L))

                    val received = mutableListOf<CombatEvent>()
                    assertTrue(waitUntil {
                        received += client.drainCombatEvents()
                        received.any { it is DamageEvent }
                    })
                    val hit = received.filterIsInstance<HitEvent>().single()
                    val damage = received.filterIsInstance<DamageEvent>().single()
                    assertEquals(client.localPlayerEntityId, hit.sourceEntityId)
                    assertEquals(TARGET_ENTITY_ID, hit.targetEntityId)
                    assertEquals(TARGET_ENTITY_ID, damage.targetEntityId)
                    assertEquals(
                        GameConstants.PLAYER_MAX_HEALTH - GameConstants.PLAYER_ATTACK_DAMAGE,
                        damage.currentHealth!!,
                        0f,
                    )
                    assertNotNull(damage.maxHealth)
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
        val deadline = System.nanoTime() + WAIT_TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(WAIT_SLEEP_MILLIS)
        }
        return condition()
    }

    private companion object {
        const val TARGET_ENTITY_ID = 2
        const val WAIT_TIMEOUT_NANOS = 2_000_000_000L
        const val WAIT_SLEEP_MILLIS = 10L
    }
}
