package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // Version 5 adds authoritative health, movement speed, and character state to entity snapshots.
    const val PROTOCOL_VERSION: Int = 5
}
