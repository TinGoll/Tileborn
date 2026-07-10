package game.shared.protocol

data class InputCommand(
    val inputSequence: Long,
    val clientTick: Long,
    val moveX: Float,
    val moveY: Float,
    val attack: Boolean = false,
    val interact: Boolean = false,
    val aimX: Float = 0f,
    val aimY: Float = 0f,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.INPUT_COMMAND,
) : ClientMessage {
    init {
        require(type == MessageType.INPUT_COMMAND) { "InputCommand type must be INPUT_COMMAND." }
    }
}
