package game.shared.protocol

data class PongResponse(
    val pingSequence: Long,
    val clientTimeMillis: Long,
    val serverTimeMillis: Long,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.PONG_RESPONSE,
) : ServerMessage {
    init {
        require(type == MessageType.PONG_RESPONSE) { "PongResponse type must be PONG_RESPONSE." }
    }
}
