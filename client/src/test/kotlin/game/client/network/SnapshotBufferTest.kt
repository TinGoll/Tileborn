package game.client.network

import game.shared.ecs.component.CharacterState
import game.shared.protocol.EntitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotBufferTest {
    @Test
    fun `interpolates between two authoritative positions`() {
        val buffer = SnapshotBuffer()
        buffer.add(0, snapshot(x = 2f, y = 4f))
        buffer.add(10, snapshot(x = 10f, y = 12f))

        val result = buffer.sample(renderServerTick = 5f)!!

        assertEquals(6f, result.x, 0.001f)
        assertEquals(8f, result.y, 0.001f)
    }

    @Test
    fun `one snapshot falls back to its authoritative position`() {
        val buffer = SnapshotBuffer()
        buffer.add(7, snapshot(x = 3f, y = 9f))

        val result = buffer.sample(renderServerTick = 3f)!!

        assertEquals(3f, result.x, 0f)
        assertEquals(9f, result.y, 0f)
    }

    private fun snapshot(x: Float, y: Float) = EntitySnapshot(
        entityId = 12,
        x = x,
        y = y,
        velocityX = 0f,
        velocityY = 0f,
        currentHealth = 100f,
        maxHealth = 100f,
        movementSpeed = 4f,
        characterState = CharacterState.ALIVE,
    )
}
