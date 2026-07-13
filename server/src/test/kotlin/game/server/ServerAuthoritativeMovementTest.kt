package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.network.TcpGameServer
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.PlayerInputComponent
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
import org.junit.Assert.assertFalse
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
    fun `input is acknowledged only after authoritative simulation tick`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            serverWorld.spawnPlayer(serverEntityId = 1)
            serverWorld.applyInput(
                serverEntityId = 1,
                command = InputCommand(inputSequence = 7L, clientTick = 7L, moveX = 1f, moveY = 0f),
            )

            assertEquals(WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE, serverWorld.acknowledgedInputSequence(1))

            serverWorld.update(0.05f)

            assertEquals(7L, serverWorld.acknowledgedInputSequence(1))
            assertEquals(7L, serverWorld.buildSnapshotForRecipient(1, serverTick = 1L).acknowledgedInputSequence)
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    @Test
    fun `server physics prevents a player from crossing a tiled wall`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            val player = serverWorld.spawnPlayer(serverEntityId = 1)
            val transform = player.getComponent(TransformComponent::class.java)

            serverWorld.applyInput(
                serverEntityId = 1,
                command = InputCommand(inputSequence = 1L, clientTick = 1L, moveX = 1f, moveY = 0f),
            )
            repeat(180) { serverWorld.update(1f / 60f) }

            assertTrue("Player crossed east wall: x=${transform.x}", transform.x <= 8.61f)
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    @Test
    fun `input for one entity does not directly control another entity`() {
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

            // Both players spawn at the same point. Box2D may push the first player away,
            // but it must never move in the direction of the second player's input.
            assertTrue(firstTransform.x < firstStartX)
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

    @Test
    fun `dead player movement and attack input are ignored`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            val player = serverWorld.spawnPlayer(serverEntityId = 1)
            val transform = player.getComponent(TransformComponent::class.java)
            val startX = transform.x
            val startY = transform.y
            assertTrue(serverWorld.applyDamage(serverEntityId = 1, amount = 100f))

            val accepted = serverWorld.applyInput(
                serverEntityId = 1,
                command = InputCommand(
                    inputSequence = 1L,
                    clientTick = 1L,
                    moveX = 1f,
                    moveY = 0f,
                    attack = true,
                ),
            )
            serverWorld.update(0.05f)

            assertFalse(accepted)
            assertEquals(CharacterState.DEAD, player.getComponent(CharacterStateComponent::class.java).state)
            assertEquals(0f, player.getComponent(PlayerInputComponent::class.java).state.moveX, 0f)
            assertFalse(player.getComponent(PlayerInputComponent::class.java).state.attack)
            assertEquals(startX, transform.x, 0.0001f)
            assertEquals(startY, transform.y, 0.0001f)
        } finally {
            serverWorld.dispose()
            application?.exit()
        }
    }

    @Test
    fun `client receives authoritative health and state through snapshot`() {
        val application = ensureHeadlessApplication()
        val serverWorld = ServerWorld(mapId = "debug_map", mapPath = "maps/debug_map.tmx")
        try {
            TcpGameServer(
                port = 0,
                mapIdProvider = { serverWorld.gameMapData.mapId },
                serverTickProvider = { 1L },
                initialSnapshotProvider = { playerEntityId ->
                    serverWorld.spawnPlayer(playerEntityId)
                    serverWorld.applyDamage(playerEntityId, 25f)
                    serverWorld.buildSnapshot(serverTick = 1L)
                },
                logger = {},
            ).use { server ->
                server.start()
                Socket("127.0.0.1", server.localPort).use { socket ->
                    socket.newWriter().use { writer ->
                        socket.newReader().use { reader ->
                            writer.writeLine(ProtocolCodec.encodeClient(JoinRequest(playerName = "health-sync-test")))
                            assertTrue(ProtocolCodec.decodeServer(reader.readLine()) is JoinAccepted)

                            val snapshot = ProtocolCodec.decodeServer(reader.readLine()) as WorldSnapshot
                            val player = snapshot.entities.single()
                            assertEquals(75f, player.currentHealth, 0f)
                            assertEquals(100f, player.maxHealth, 0f)
                            assertEquals(CharacterState.ALIVE, player.characterState)
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
