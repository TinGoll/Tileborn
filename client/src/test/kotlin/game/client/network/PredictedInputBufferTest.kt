package game.client.network

import game.shared.protocol.InputCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class PredictedInputBufferTest {
    @Test
    fun `acknowledging a sequence removes it and older commands`() {
        val buffer = PredictedInputBuffer()
        buffer.add(command(1), 0.05f)
        buffer.add(command(2), 0.05f)
        buffer.add(command(3), 0.05f)

        buffer.acknowledge(2)

        assertEquals(listOf(3L), buffer.entries().map { it.command.inputSequence })
        buffer.acknowledge(3)
        assertEquals(0, buffer.size)
    }

    private fun command(sequence: Long) = InputCommand(
        inputSequence = sequence,
        clientTick = sequence,
        moveX = 1f,
        moveY = 0f,
    )
}
