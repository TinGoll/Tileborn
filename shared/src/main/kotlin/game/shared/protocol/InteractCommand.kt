package game.shared.protocol

/** Requests interaction with a Tiled gameplay object. The server validates its id, type and distance. */
data class InteractCommand(
    val interactionSequence: Long,
    val targetObjectId: Int,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.INTERACT_COMMAND,
) : ClientMessage {
    init {
        require(type == MessageType.INTERACT_COMMAND) { "InteractCommand type must be INTERACT_COMMAND." }
    }
}
