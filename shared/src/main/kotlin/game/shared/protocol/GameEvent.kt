package game.shared.protocol

/** Authoritative gameplay result delivered by the server to affected observers. */
data class GameEvent(
    val eventType: GameEventType,
    val objectId: Int,
    val message: String,
    val sourceEntityId: Int? = null,
    val targetEntityId: Int? = null,
    /** Server-authored combat amount; no client command has a corresponding field. */
    val amount: Float? = null,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.GAME_EVENT,
) : ServerMessage {
    init {
        require(type == MessageType.GAME_EVENT) { "GameEvent type must be GAME_EVENT." }
    }
}

enum class GameEventType {
    TRIGGER_ENTERED,
    PORTAL_USED,
    ATTACK_HIT,
    ATTACK_MISSED,
}
