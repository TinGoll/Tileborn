package game.shared.protocol

sealed interface ServerMessage {
    val type: MessageType
    val protocolVersion: Int
}
