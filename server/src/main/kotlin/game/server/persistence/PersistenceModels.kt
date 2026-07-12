package game.server.persistence

/** Stable account key. Authentication is intentionally outside this MVP persistence model. */
@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "Account id must not be blank." }
    }
}

/** Stable character key, independent of the temporary network entity id. */
@JvmInline
value class CharacterId(val value: String) {
    init {
        require(value.isNotBlank()) { "Character id must not be blank." }
    }
}

/** Durable character data. Velocity, input and Box2D bodies intentionally remain runtime-only. */
data class SavedCharacterState(
    val accountId: AccountId,
    val characterId: CharacterId,
    val mapId: String,
    val positionX: Float,
    val positionY: Float,
    val nickname: String,
) {
    init {
        require(mapId.isNotBlank()) { "Map id must not be blank." }
        require(nickname.isNotBlank()) { "Nickname must not be blank." }
    }
}
