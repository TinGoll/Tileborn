package game.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.gson.JsonParseException

class ProtocolMessagesTest {
    @Test
    fun `join request is created with protocol version`() {
        val request = JoinRequest(playerName = "player")

        assertEquals(Protocol.PROTOCOL_VERSION, request.protocolVersion)
        assertEquals(MessageType.JOIN_REQUEST, request.type)
    }

    @Test
    fun `world snapshot contains server tick and entity snapshots`() {
        val entity = EntitySnapshot(
            entityId = 7,
            x = 12f,
            y = 4f,
            velocityX = 1f,
            velocityY = 0f,
        )
        val snapshot = WorldSnapshot(
            serverTick = 42L,
            entities = listOf(entity),
        )

        assertEquals(42L, snapshot.serverTick)
        assertEquals(listOf(entity), snapshot.entities)
        assertEquals(MessageType.WORLD_SNAPSHOT, snapshot.type)
    }

    @Test
    fun `input command contains input sequence and client tick`() {
        val command = InputCommand(
            inputSequence = 10L,
            clientTick = 25L,
            moveX = 1f,
            moveY = 0f,
        )

        assertEquals(10L, command.inputSequence)
        assertEquals(25L, command.clientTick)
        assertTrue(command is ClientMessage)
    }

    @Test
    fun `client message serializes and deserializes`() {
        val request = JoinRequest(playerName = "player", sessionToken = "resume-token")

        val decoded = ProtocolCodec.decodeClient(ProtocolCodec.encodeClient(request))

        assertEquals(request, decoded)
    }

    @Test
    fun `join accepted serializes opaque session token`() {
        val accepted = JoinAccepted(
            playerEntityId = 7,
            mapId = "debug_map",
            serverTick = 42L,
            sessionToken = "session-token",
        )

        assertEquals(accepted, ProtocolCodec.decodeServer(ProtocolCodec.encodeServer(accepted)))
    }

    @Test
    fun `server message serializes and deserializes`() {
        val snapshot = WorldSnapshot(
            serverTick = 99L,
            acknowledgedInputSequence = 12L,
            entities = listOf(
                EntitySnapshot(
                    entityId = 3,
                    x = 1f,
                    y = 2f,
                    velocityX = 0.5f,
                    velocityY = 0f,
                ),
            ),
        )

        val decoded = ProtocolCodec.decodeServer(ProtocolCodec.encodeServer(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `ping and pong messages serialize and deserialize`() {
        val ping = PingRequest(
            pingSequence = 4L,
            clientTimeMillis = 1_000L,
        )
        val pong = PongResponse(
            pingSequence = 4L,
            clientTimeMillis = 1_000L,
            serverTimeMillis = 1_025L,
        )

        assertEquals(ping, ProtocolCodec.decodeClient(ProtocolCodec.encodeClient(ping)))
        assertEquals(pong, ProtocolCodec.decodeServer(ProtocolCodec.encodeServer(pong)))
    }

    @Test
    fun `interaction command and game event serialize and deserialize`() {
        val command = InteractCommand(interactionSequence = 3L, targetObjectId = 6)
        val event = GameEvent(GameEventType.TRIGGER_ENTERED, objectId = 6, message = "player entered trigger welcome")

        assertEquals(command, ProtocolCodec.decodeClient(ProtocolCodec.encodeClient(command)))
        assertEquals(event, ProtocolCodec.decodeServer(ProtocolCodec.encodeServer(event)))
    }

    @Test(expected = JsonParseException::class)
    fun `direct client position message is not supported`() {
        ProtocolCodec.decodeClient(
            """{"type":"POSITION_UPDATE","protocolVersion":1,"x":120.0,"y":50.0}""",
        )
    }
}
