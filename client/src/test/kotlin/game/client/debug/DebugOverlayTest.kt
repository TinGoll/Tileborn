package game.client.debug

import com.badlogic.ashley.core.Engine
import game.client.ecs.component.LocalPlayerComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugOverlayTest {
    @Test
    fun `overlay renders current debug state`() {
        val engine = Engine()
        engine.addEntity(engine.createEntity().apply {
            add(LocalPlayerComponent())
            add(TransformComponent(x = 12.5f, y = 7.25f))
        })
        engine.addEntity(engine.createEntity())
        val renderer = RecordingDebugOverlayRenderer()
        val overlay = DebugOverlay(
            snapshotBuilder = DebugOverlaySnapshotBuilder(
                engine = engine,
                mapIdProvider = { "debug_map" },
                connectionStateProvider = { ConnectionState.LOCAL },
                pingMillisProvider = { 42L },
            ),
            toggleKeyJustPressed = { false },
            fpsProvider = { 60 },
            renderer = renderer,
        )

        overlay.render()

        assertEquals(
            listOf(
                "FPS: 60",
                "Player: 12.50, 7.25",
                "Entities: 2",
                "Map: debug_map",
                "Connection: local",
                "Ping: 42 ms",
            ),
            renderer.lastLines,
        )
        assertTrue(overlay.visible)
    }

    @Test
    fun `toggle hides and shows overlay`() {
        val renderer = RecordingDebugOverlayRenderer()
        var toggle = false
        val overlay = DebugOverlay(
            snapshotBuilder = DebugOverlaySnapshotBuilder(
                engine = Engine(),
                mapIdProvider = { null },
                connectionStateProvider = { ConnectionState.DISCONNECTED },
                pingMillisProvider = { null },
            ),
            toggleKeyJustPressed = { toggle.also { toggle = false } },
            fpsProvider = { 30 },
            renderer = renderer,
        )

        overlay.render()
        assertEquals(1, renderer.renderCount)

        toggle = true
        overlay.render()
        assertFalse(overlay.visible)
        assertEquals(1, renderer.renderCount)

        toggle = true
        overlay.render()
        assertTrue(overlay.visible)
        assertEquals(2, renderer.renderCount)
    }

    @Test
    fun `missing local player is shown without failing`() {
        val engine = Engine()
        engine.addEntity(engine.createEntity().apply { add(TransformComponent(x = 1f, y = 2f)) })
        val renderer = RecordingDebugOverlayRenderer()
        val overlay = DebugOverlay(
            snapshotBuilder = DebugOverlaySnapshotBuilder(
                engine = engine,
                mapIdProvider = { "debug_map" },
                connectionStateProvider = { ConnectionState.LOCAL },
                pingMillisProvider = { null },
            ),
            toggleKeyJustPressed = { false },
            fpsProvider = { 45 },
            renderer = renderer,
        )

        overlay.render()

        assertEquals("Player: none", renderer.lastLines[1])
        assertEquals("Entities: 1", renderer.lastLines[2])
        assertEquals("Ping: n/a", renderer.lastLines[5])
    }

    private class RecordingDebugOverlayRenderer : DebugOverlayRenderer {
        var lastLines: List<String> = emptyList()
            private set
        var renderCount: Int = 0
            private set

        override fun render(lines: List<String>) {
            lastLines = lines
            renderCount++
        }

        override fun dispose() = Unit
    }
}
