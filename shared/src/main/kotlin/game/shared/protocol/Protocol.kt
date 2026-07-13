package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // Version 6 identifies player and mob snapshots and carries the static definition id.
    const val PROTOCOL_VERSION: Int = 6
}
