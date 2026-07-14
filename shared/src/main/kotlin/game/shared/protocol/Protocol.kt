package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // Version 7 adds authoritative collision radius to entity snapshots for client physics proxies.
    const val PROTOCOL_VERSION: Int = 7
}
