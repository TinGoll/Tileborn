package game.client.debug

/** Client connection state shown by debug UI. */
enum class ConnectionState {
    LOCAL,
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    REJECTED,
    DISCONNECTED,
}
