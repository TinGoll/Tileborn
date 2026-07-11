package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.ecs.component.ServerAuthorityComponent
import game.server.network.TcpGameServer
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.TransformComponent
import game.shared.protocol.JoinAccepted
import game.shared.protocol.JoinRequest
import game.shared.protocol.ProtocolCodec
import game.shared.protocol.WorldSnapshot
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAuthoritativeSpawnTest {
    @Test
    fun `join creates player entity on authoritative server world`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            TcpGameServer(
                port = 0,
                mapIdProvider = { serverWorld.gameMapData.mapId },
                serverTickProvider = { 7L },
                initialSnapshotProvider = { playerEntityId ->
                    serverWorld.spawnPlayer(playerEntityId)
                    serverWorld.buildSnapshot(serverTick = 7L)
                },
                logger = {},
            ).use { server ->
                server.start()

                Socket("127.0.0.1", server.localPort).use { socket ->
                    socket.newWriter().use { writer ->
                        socket.newReader().use { reader ->
                            writer.writeLine(ProtocolCodec.encodeClient(JoinRequest(playerName = "spawn-test")))

                            val accepted = ProtocolCodec.decodeServer(reader.readLine()) as JoinAccepted
                            val snapshot = ProtocolCodec.decodeServer(reader.readLine()) as WorldSnapshot
                            val entity = serverWorld.engine.entities.single()
                            val transform = entity.getComponent(TransformComponent::class.java)
                            val identity = entity.getComponent(NetworkIdentityComponent::class.java)
                            val spawn = serverWorld.gameMapData.requireSpawnPoint("default")

                            assertEquals(accepted.playerEntityId.toLong(), identity.networkEntityId)
                            assertEquals(spawn.x, transform.x, 0f)
                            assertEquals(spawn.y, transform.y, 0f)
                            assertNotNull(entity.getComponent(ServerAuthorityComponent::class.java))
                            assertEquals(accepted.playerEntityId, snapshot.entities.single().entityId)
                        }
                    }
                }
            }
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    @Test
    fun `server world snapshot contains spawned player position`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            serverWorld.spawnPlayer(serverEntityId = 99)

            val snapshot = serverWorld.buildSnapshot(serverTick = 11L)
            val spawn = serverWorld.gameMapData.requireSpawnPoint("default")
            val player = snapshot.entities.single()

            assertEquals(99, player.entityId)
            assertEquals(spawn.x, player.x, 0f)
            assertEquals(spawn.y, player.y, 0f)
            assertEquals(11L, snapshot.serverTick)
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    private fun ensureHeadlessApplication(): HeadlessApplication? =
        if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null

    private fun Socket.newReader(): BufferedReader =
        BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))

    private fun Socket.newWriter(): BufferedWriter =
        BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
        flush()
    }
}
