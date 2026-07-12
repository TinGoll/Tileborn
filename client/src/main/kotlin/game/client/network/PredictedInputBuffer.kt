package game.client.network

import game.shared.protocol.InputCommand

/** Ordered local commands retained until the authoritative server acknowledges them. */
class PredictedInputBuffer {
    data class Entry(val command: InputCommand, val deltaTime: Float)

    private val entries = ArrayDeque<Entry>()

    fun add(command: InputCommand, deltaTime: Float) {
        require(deltaTime >= 0f) { "Predicted input delta time cannot be negative." }
        require(entries.lastOrNull()?.command?.inputSequence?.let { command.inputSequence > it } ?: true) {
            "Predicted input sequences must be strictly increasing."
        }
        entries.addLast(Entry(command, deltaTime))
    }

    fun acknowledge(sequence: Long) {
        while (entries.firstOrNull()?.command?.inputSequence?.let { it <= sequence } == true) {
            entries.removeFirst()
        }
    }

    fun entries(): List<Entry> = entries.toList()

    fun clear() = entries.clear()

    val size: Int get() = entries.size
}
