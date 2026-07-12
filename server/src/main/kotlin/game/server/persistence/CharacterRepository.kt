package game.server.persistence

/** Server-only durable character store. It is never accessed by client code or ECS systems. */
interface CharacterRepository {
    fun save(state: SavedCharacterState)

    fun load(characterId: CharacterId): SavedCharacterState?

    fun findByAccountId(accountId: AccountId): SavedCharacterState?
}
