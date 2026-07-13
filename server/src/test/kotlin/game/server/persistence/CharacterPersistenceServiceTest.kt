package game.server.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterPersistenceServiceTest {
    @Test
    fun `save and load character state`() {
        val repository = InMemoryCharacterRepository()
        val state = savedState(positionX = 12f, positionY = 7f)

        repository.save(state)

        assertEquals(state, repository.load(state.characterId))
        assertEquals(state, repository.findByAccountId(state.accountId))
    }

    @Test
    fun `disconnect saves active character state`() {
        val characters = InMemoryCharacterRepository()
        val service = CharacterPersistenceService(characters, InMemorySessionRepository()) { CharacterId("character-1") }
        val restored = service.restoreForJoin(41, "session-1", "Ada", "debug_map", 1f, 2f)

        service.saveOnDisconnect(entityId = 41, mapId = "debug_map", positionX = 9f, positionY = 11f)

        assertEquals(restored.copy(positionX = 9f, positionY = 11f), characters.load(restored.characterId))
    }

    @Test
    fun `saved position is restored when the same session reconnects`() {
        val characters = InMemoryCharacterRepository()
        val sessions = InMemorySessionRepository()
        val service = CharacterPersistenceService(characters, sessions) { CharacterId("character-1") }
        val initial = service.restoreForJoin(1, "same-token", "Ada", "debug_map", 1f, 2f)
        service.saveOnDisconnect(1, "debug_map", 15f, 20f)

        val restored = service.restoreForJoin(2, "same-token", "Ada", "debug_map", 1f, 2f)

        assertEquals(initial.characterId, restored.characterId)
        assertEquals(15f, restored.positionX, 0f)
        assertEquals(20f, restored.positionY, 0f)
    }

    @Test
    fun `same nickname with a new session creates a different guest character`() {
        val characters = InMemoryCharacterRepository()
        val sessions = InMemorySessionRepository()
        val characterIds = ArrayDeque(listOf(CharacterId("character-1"), CharacterId("character-2")))
        val service = CharacterPersistenceService(characters, sessions) { characterIds.removeFirst() }
        val first = service.restoreForJoin(1, "first-token", "Ada", "debug_map", 1f, 2f)
        service.saveOnDisconnect(1, "debug_map", 15f, 20f)

        val second = service.restoreForJoin(2, "second-token", "Ada", "debug_map", 1f, 2f)

        assertNotEquals(first.characterId, second.characterId)
        assertNotEquals(first.accountId, second.accountId)
        assertEquals(1f, second.positionX, 0f)
        assertEquals(2f, second.positionY, 0f)
    }

    @Test
    fun `persistence is not invoked by server ticks`() {
        val characters = CountingCharacterRepository()
        val service = CharacterPersistenceService(characters, InMemorySessionRepository()) { CharacterId("character-1") }
        service.restoreForJoin(1, "token", "Ada", "debug_map", 1f, 2f)
        val savesAfterJoin = characters.saveCount

        repeat(100) { /* Server ticks update ECS; persistence has no tick API. */ }

        assertEquals(savesAfterJoin, characters.saveCount)
        assertNull(characters.load(CharacterId("unknown")))
    }

    private fun savedState(positionX: Float, positionY: Float) = SavedCharacterState(
        accountId = AccountId("account-1"),
        characterId = CharacterId("character-1"),
        mapId = "debug_map",
        positionX = positionX,
        positionY = positionY,
        nickname = "Ada",
    )

    private class CountingCharacterRepository : CharacterRepository {
        private val delegate = InMemoryCharacterRepository()
        var saveCount = 0
            private set

        override fun save(state: SavedCharacterState) {
            saveCount += 1
            delegate.save(state)
        }

        override fun load(characterId: CharacterId): SavedCharacterState? = delegate.load(characterId)

        override fun findByAccountId(accountId: AccountId): SavedCharacterState? = delegate.findByAccountId(accountId)
    }
}
