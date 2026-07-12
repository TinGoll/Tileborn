package game.shared.protocol

data class WorldSnapshot(
    val serverTick: Long,
    val entities: List<EntitySnapshot>,
    /** Last input sequence accepted for the receiving player, or [NO_ACKNOWLEDGED_INPUT_SEQUENCE]. */
    val acknowledgedInputSequence: Long = NO_ACKNOWLEDGED_INPUT_SEQUENCE,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.WORLD_SNAPSHOT,
) : ServerMessage {
    init {
        require(type == MessageType.WORLD_SNAPSHOT) { "WorldSnapshot type must be WORLD_SNAPSHOT." }
    }

    companion object {
        const val NO_ACKNOWLEDGED_INPUT_SEQUENCE: Long = -1L
    }
}
