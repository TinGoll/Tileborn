package game.shared.protocol

data class JoinRejected(
    val reason: String,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.JOIN_REJECTED,
) : ServerMessage {
    init {
        require(type == MessageType.JOIN_REJECTED) { "JoinRejected type must be JOIN_REJECTED." }
    }
}
