package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // Version 8 adds the dedicated AttackCommand and authoritative combat result events.
    const val PROTOCOL_VERSION: Int = 8
}
