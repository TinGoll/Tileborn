package game.server.persistence

import java.util.concurrent.ConcurrentHashMap

/** Development implementation; process restart intentionally clears all saved state. */
class InMemoryCharacterRepository : CharacterRepository {
    private val statesByCharacterId = ConcurrentHashMap<CharacterId, SavedCharacterState>()
    private val characterIdByAccountId = ConcurrentHashMap<AccountId, CharacterId>()

    override fun save(state: SavedCharacterState) {
        statesByCharacterId[state.characterId] = state
        characterIdByAccountId[state.accountId] = state.characterId
    }

    override fun load(characterId: CharacterId): SavedCharacterState? = statesByCharacterId[characterId]

    override fun findByAccountId(accountId: AccountId): SavedCharacterState? =
        characterIdByAccountId[accountId]?.let(statesByCharacterId::get)
}

class InMemorySessionRepository : SessionRepository {
    private val characterIdBySessionToken = ConcurrentHashMap<String, CharacterId>()

    override fun bind(sessionToken: String, characterId: CharacterId) {
        require(sessionToken.isNotBlank()) { "Session token must not be blank." }
        characterIdBySessionToken[sessionToken] = characterId
    }

    override fun findCharacterId(sessionToken: String): CharacterId? = characterIdBySessionToken[sessionToken]
}
