package game.shared.protocol

/** Requests one attack attempt. Hit selection and damage remain authoritative server decisions. */
data class AttackCommand(
    val inputSequence: Long,
    val clientTick: Long,
    val aimX: Float,
    val aimY: Float,
    val optionalTargetEntityId: Int? = null,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.ATTACK_COMMAND,
) : ClientMessage {
    init {
        require(type == MessageType.ATTACK_COMMAND) { "AttackCommand type must be ATTACK_COMMAND." }
    }
}
