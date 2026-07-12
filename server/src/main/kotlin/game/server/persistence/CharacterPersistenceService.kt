package game.server.persistence

import java.util.UUID

/**
 * Coordinates repository access at join/disconnect boundaries.
 *
 * ECS remains runtime-only: callers provide an authoritative position only when a disconnect occurs.
 */
class CharacterPersistenceService(
    private val characterRepository: CharacterRepository,
    private val sessionRepository: SessionRepository,
    private val characterIdFactory: () -> CharacterId = { CharacterId(UUID.randomUUID().toString()) },
) {
    private val activeStateByEntityId = mutableMapOf<Int, SavedCharacterState>()

    @Synchronized
    fun restoreForJoin(
        entityId: Int,
        sessionToken: String,
        nickname: String,
        defaultMapId: String,
        defaultPositionX: Float,
        defaultPositionY: Float,
    ): SavedCharacterState {
        val accountId = AccountId(nickname)
        val characterId = sessionRepository.findCharacterId(sessionToken)
            ?: characterRepository.findByAccountId(accountId)?.characterId
            ?: characterIdFactory()
        sessionRepository.bind(sessionToken, characterId)

        return (characterRepository.load(characterId) ?: SavedCharacterState(
            accountId = accountId,
            characterId = characterId,
            mapId = defaultMapId,
            positionX = defaultPositionX,
            positionY = defaultPositionY,
            nickname = nickname,
        )).also { activeStateByEntityId[entityId] = it }
    }

    @Synchronized
    fun stateForEntity(entityId: Int): SavedCharacterState? = activeStateByEntityId[entityId]

    @Synchronized
    fun saveOnDisconnect(entityId: Int, mapId: String, positionX: Float, positionY: Float) {
        val activeState = activeStateByEntityId[entityId] ?: return
        characterRepository.save(activeState.copy(mapId = mapId, positionX = positionX, positionY = positionY))
    }

    @Synchronized
    fun forgetEntity(entityId: Int) {
        activeStateByEntityId.remove(entityId)
    }
}
