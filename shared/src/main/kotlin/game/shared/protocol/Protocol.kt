package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // Join messages now carry an opaque reconnect session token.
    const val PROTOCOL_VERSION: Int = 3
}
