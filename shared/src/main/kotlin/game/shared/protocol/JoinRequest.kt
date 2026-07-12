package game.shared.protocol

data class JoinRequest(
    val playerName: String,
    /** Opaque token returned by a previous JoinAccepted; absent for a new player session. */
    val sessionToken: String? = null,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.JOIN_REQUEST,
) : ClientMessage {
    init {
        require(type == MessageType.JOIN_REQUEST) { "JoinRequest type must be JOIN_REQUEST." }
    }
}
