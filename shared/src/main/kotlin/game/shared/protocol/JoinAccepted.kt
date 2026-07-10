package game.shared.protocol

data class JoinAccepted(
    val playerEntityId: Int,
    val mapId: String,
    val serverTick: Long,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.JOIN_ACCEPTED,
) : ServerMessage {
    init {
        require(type == MessageType.JOIN_ACCEPTED) { "JoinAccepted type must be JOIN_ACCEPTED." }
    }
}
