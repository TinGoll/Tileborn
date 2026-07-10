package game.shared.protocol

sealed interface ClientMessage {
    val type: MessageType
    val protocolVersion: Int
}
