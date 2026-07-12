package game.shared.protocol

/** Authoritative gameplay result delivered by the server to the affected client. */
data class GameEvent(
    val eventType: GameEventType,
    val objectId: Int,
    val message: String,
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
}
