package game.shared.protocol

/** Constants shared by every protocol message exchanged by clients and the authoritative server. */
object Protocol {
    // WorldSnapshot now carries a per-recipient acknowledged input sequence.
    const val PROTOCOL_VERSION: Int = 2
}
