package game.shared.protocol

data class WorldSnapshot(
    val serverTick: Long,
    val entities: List<EntitySnapshot>,
    override val protocolVersion: Int = Protocol.PROTOCOL_VERSION,
    override val type: MessageType = MessageType.WORLD_SNAPSHOT,
) : ServerMessage {
    init {
        require(type == MessageType.WORLD_SNAPSHOT) { "WorldSnapshot type must be WORLD_SNAPSHOT." }
    }
}
