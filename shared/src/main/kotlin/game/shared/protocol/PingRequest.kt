package game.shared.protocol

data class PingRequest(
    val pingSequence: Long,
    val clientTimeMillis: Long,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.PING_REQUEST,
) : ClientMessage {
    init {
        require(type == MessageType.PING_REQUEST) { "PingRequest type must be PING_REQUEST." }
    }
}
