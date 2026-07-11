package game.client.ecs

import com.badlogic.ashley.core.Entity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClientEntityRegistryTest {
    @Test
    fun `maps client entity by authoritative server entity id`() {
        val registry = ClientEntityRegistry()
        val first = Entity()
        val replacement = Entity()

        registry.put(101, first)
        assertSame(first, registry.get(101))

        registry.put(101, replacement)
        assertSame(replacement, registry.get(101))
        assertEquals(setOf(101), registry.serverEntityIds())
        assertSame(replacement, registry.remove(101))
        assertNull(registry.get(101))
    }
}
