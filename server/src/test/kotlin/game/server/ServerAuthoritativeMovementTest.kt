package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.network.TcpGameServer
import game.shared.ecs.component.TransformComponent
import game.shared.protocol.InputCommand
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
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAuthoritativeMovementTest {
    @Test
    fun `input right changes x on authoritative server`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            val player = serverWorld.spawnPlayer(serverEntityId = 1)
            val transform = player.getComponent(TransformComponent::class.java)
            val startX = transform.x

            assertTrue(
                serverWorld.applyInput(
                    serverEntityId = 1,
                    command = InputCommand(inputSequence = 1L, clientTick = 1L, moveX = 1f, moveY = 0f),
                ),
            )
            serverWorld.update(0.05f)

            assertTrue(transform.x > startX)
            assertEquals(serverWorld.gameMapData.requireSpawnPoint("default").y, transform.y, 0.0001f)
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    @Test
    fun `input for one entity does not control another entity`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            val first = serverWorld.spawnPlayer(serverEntityId = 1)
            val second = serverWorld.spawnPlayer(serverEntityId = 2)
            val firstTransform = first.getComponent(TransformComponent::class.java)
            val secondTransform = second.getComponent(TransformComponent::class.java)
            val firstStartX = firstTransform.x
            val secondStartX = secondTransform.x

            serverWorld.applyInput(
                serverEntityId = 2,
                command = InputCommand(inputSequence = 1L, clientTick = 1L, moveX = 1f, moveY = 0f),
            )
            serverWorld.update(0.05f)

            assertEquals(firstStartX, firstTransform.x, 0f)
            assertTrue(secondTransform.x > secondStartX)
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    @Test
    fun `client receives updated authoritative position after input`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            TcpGameServer(
                port = 0,
                mapIdProvider = { serverWorld.gameMapData.mapId },
                serverTickProvider = { 1L },
                initialSnapshotProvider = { playerEntityId ->
                    serverWorld.spawnPlayer(playerEntityId)
                    serverWorld.buildSnapshot(serverTick = 1L)
                },
                inputCommandHandler = { playerEntityId, command ->
                    serverWorld.applyInput(playerEntityId, command)
                    serverWorld.update(0.05f)
                    serverWorld.buildSnapshot(serverTick = 2L)
                },
                logger = {},
            ).use { server ->
                server.start()
                Socket("127.0.0.1", server.localPort).use { socket ->
                    socket.newWriter().use { writer ->
                        socket.newReader().use { reader ->
                            writer.writeLine(ProtocolCodec.encodeClient(JoinRequest(playerName = "movement-test")))
                            assertTrue(ProtocolCodec.decodeServer(reader.readLine()) is JoinAccepted)
                            val initialSnapshot = ProtocolCodec.decodeServer(reader.readLine()) as WorldSnapshot
                            val initialX = initialSnapshot.entities.single().x

                            writer.writeLine(
                                ProtocolCodec.encodeClient(
                                    InputCommand(inputSequence = 1L, clientTick = 1L, moveX = 1f, moveY = 0f),
                                ),
                            )

                            val updatedSnapshot = ProtocolCodec.decodeServer(reader.readLine()) as WorldSnapshot
                            assertEquals(2L, updatedSnapshot.serverTick)
                            assertTrue(updatedSnapshot.entities.single().x > initialX)
                        }
                    }
                }
            }
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    private fun Socket.newReader(): BufferedReader =
        BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))

    private fun Socket.newWriter(): BufferedWriter =
        BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
        flush()
    }

    private fun ensureHeadlessApplication(): HeadlessApplication? =
        if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
}
