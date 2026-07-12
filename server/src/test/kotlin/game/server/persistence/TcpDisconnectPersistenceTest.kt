package game.server.persistence

import game.server.network.TcpGameServer
import game.shared.protocol.JoinRequest
import game.shared.protocol.ProtocolCodec
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class TcpDisconnectPersistenceTest {
    @Test
    fun `disconnect lifecycle invokes character save once`() {
        val characters = CountingCharacterRepository()
        val persistence = CharacterPersistenceService(characters, InMemorySessionRepository()) { CharacterId("character-1") }
        val establishedEntityId = AtomicInteger()
        TcpGameServer(
            port = 0,
            mapIdProvider = { "debug_map" },
            serverTickProvider = { 0L },
            sessionEstablishedHandler = { session ->
                establishedEntityId.set(session.entityId)
                persistence.restoreForJoin(session.entityId, session.sessionToken, session.playerName, "debug_map", 1f, 2f)
            },
            sessionDisconnectedHandler = { entityId -> persistence.saveOnDisconnect(entityId, "debug_map", 3f, 4f) },
            logger = {},
        ).use { server ->
            server.start()
            Socket("127.0.0.1", server.localPort).use { socket ->
                BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)).use { writer ->
                    writer.write(ProtocolCodec.encodeClient(JoinRequest("Ada")))
                    writer.newLine()
                    writer.flush()
                }
            }

            waitUntil { characters.saveCount == 1 }
        }

        assertEquals(1, establishedEntityId.get())
        assertEquals(1, characters.saveCount)
        assertEquals(3f, characters.lastSaved!!.positionX, 0f)
        assertEquals(4f, characters.lastSaved!!.positionY, 0f)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10L)
        check(condition()) { "Timed out waiting for disconnect persistence." }
    }

    private class CountingCharacterRepository : CharacterRepository {
        var saveCount = 0
            private set
        var lastSaved: SavedCharacterState? = null
            private set

        override fun save(state: SavedCharacterState) {
            saveCount += 1
            lastSaved = state
        }

        override fun load(characterId: CharacterId): SavedCharacterState? = null

        override fun findByAccountId(accountId: AccountId): SavedCharacterState? = null
    }
}
