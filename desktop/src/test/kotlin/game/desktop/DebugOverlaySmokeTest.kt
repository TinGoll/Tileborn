package game.desktop

import com.badlogic.ashley.core.Engine
import game.client.debug.ConnectionState
import game.client.debug.DebugOverlay
import game.client.debug.DebugOverlayRenderer
import game.client.debug.DebugOverlaySnapshotBuilder
import game.client.ecs.component.LocalPlayerComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugOverlaySmokeTest {
    @Test
    fun `desktop debug overlay exposes visible game state`() {
        val engine = Engine()
        engine.addEntity(engine.createEntity().apply {
            add(LocalPlayerComponent())
            add(TransformComponent(x = 2f, y = 3f))
        })
        val renderer = RecordingDebugOverlayRenderer()
        val overlay = DebugOverlay(
            snapshotBuilder = DebugOverlaySnapshotBuilder(
                engine = engine,
                mapIdProvider = { "debug_map" },
                connectionStateProvider = { ConnectionState.LOCAL },
                pingMillisProvider = { 18L },
            ),
            toggleKeyJustPressed = { false },
            fpsProvider = { 75 },
            renderer = renderer,
        )

        overlay.render()

        assertEquals("FPS: 75", renderer.lastLines[0])
        assertEquals("Server tick: n/a", renderer.lastLines[1])
        assertEquals("Player: 2.00, 3.00", renderer.lastLines[2])
        assertEquals("Entities: 1", renderer.lastLines[3])
        assertEquals("Visible entities: 1", renderer.lastLines[4])
        assertEquals("Map: debug_map", renderer.lastLines[5])
        assertEquals("Connection: local", renderer.lastLines[6])
        assertEquals("Ping: 18 ms", renderer.lastLines[7])
    }

    private class RecordingDebugOverlayRenderer : DebugOverlayRenderer {
        var lastLines: List<String> = emptyList()
            private set

        override fun render(lines: List<String>) {
            lastLines = lines
        }

        override fun dispose() = Unit
    }
}
