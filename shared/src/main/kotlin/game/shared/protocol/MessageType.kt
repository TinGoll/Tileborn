package game.shared.protocol

enum class MessageType {
    JOIN_REQUEST,
    JOIN_ACCEPTED,
    JOIN_REJECTED,
    INPUT_COMMAND,
    INTERACT_COMMAND,
    WORLD_SNAPSHOT,
    GAME_EVENT,
    PING_REQUEST,
    PONG_RESPONSE,
}
