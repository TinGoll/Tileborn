package game.server.persistence

/** Maps an opaque reconnect token to a durable character identity. */
interface SessionRepository {
    fun bind(sessionToken: String, characterId: CharacterId)

    fun findCharacterId(sessionToken: String): CharacterId?
}
