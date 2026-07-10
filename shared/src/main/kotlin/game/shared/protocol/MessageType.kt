package game.shared.protocol

enum class MessageType {
    JOIN_REQUEST,
    JOIN_ACCEPTED,
    JOIN_REJECTED,
    INPUT_COMMAND,
    WORLD_SNAPSHOT,
    PING_REQUEST,
    PONG_RESPONSE,
}
