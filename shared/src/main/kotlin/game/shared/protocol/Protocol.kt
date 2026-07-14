package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // Version 9 adds explicit attack, hit, damage, and entity-death events.
    const val PROTOCOL_VERSION: Int = 9
}
