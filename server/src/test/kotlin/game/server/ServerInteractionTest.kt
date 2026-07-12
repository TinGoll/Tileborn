package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.network.TcpGameServer
import game.shared.ecs.component.TransformComponent
import game.shared.protocol.GameEvent
import game.shared.protocol.GameEventType
import game.shared.protocol.InteractCommand
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

class ServerInteractionTest {
    @Test
    fun `interaction inside radius succeeds with authoritative game event`() = withWorld { world ->
        world.spawnPlayer(1)

        val result = world.interact(1, InteractCommand(1L, targetObjectId = 6))

        assertEquals(
            InteractionResult.Accepted(
                GameEvent(GameEventType.TRIGGER_ENTERED, 6, "player entered trigger welcome"),
            ),
            result,
        )
    }

    @Test
    fun `interaction outside radius is rejected`() = withWorld { world ->
        val player = world.spawnPlayer(1)
        player.getComponent(TransformComponent::class.java).apply { x = 8f; y = 8f }

        assertTrue(world.interact(1, InteractCommand(1L, 6)) is InteractionResult.Rejected)
    }

    @Test
    fun `unknown interaction object id is rejected`() = withWorld { world ->
        world.spawnPlayer(1)

        assertTrue(world.interact(1, InteractCommand(1L, 9999)) is InteractionResult.Rejected)
    }

    @Test
    fun `client receives game event for accepted interaction`() = withWorld { world ->
        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 1L },
            initialSnapshotProvider = { entityId -> world.spawnPlayer(entityId); world.buildSnapshot(1L) },
            interactCommandHandler = { entityId, command ->
                (world.interact(entityId, command) as? InteractionResult.Accepted)?.event
            },
            logger = {},
        ).use { server ->
            server.start()
            Socket("127.0.0.1", server.localPort).use { socket ->
                socket.newWriter().use { writer -> socket.newReader().use { reader ->
                    writer.writeLine(ProtocolCodec.encodeClient(JoinRequest("interaction-test")))
                    assertTrue(ProtocolCodec.decodeServer(reader.readLine()) is JoinAccepted)
                    assertTrue(ProtocolCodec.decodeServer(reader.readLine()) is WorldSnapshot)
                    writer.writeLine(ProtocolCodec.encodeClient(InteractCommand(1L, 6)))
                    assertEquals(
                        GameEvent(GameEventType.TRIGGER_ENTERED, 6, "player entered trigger welcome"),
                        ProtocolCodec.decodeServer(reader.readLine()),
                    )
                } }
            }
        }
    }

    private fun withWorld(block: (ServerWorld) -> Unit) {
        val application = if (Gdx.app == null) HeadlessApplication(object : ApplicationAdapter() {}) else null
        val world = ServerWorld("debug_map", "maps/debug_map.tmx")
        try { block(world) } finally { world.dispose(); application?.exit() }
    }

    private fun Socket.newReader() = BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))
    private fun Socket.newWriter() = BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))
    private fun BufferedWriter.writeLine(line: String) { write(line); newLine(); flush() }
}
