package game.client.ecs.system

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Matrix4
import game.client.ecs.component.PrimitiveShape
import game.client.ecs.component.RenderPrimitiveComponent
import game.shared.ecs.component.TransformComponent
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimitiveRenderSystemTest {
    @Test
    fun `rendering uses component data without changing transform`() {
        val engine = Engine()
        val renderer = RecordingRenderer()
        val system = PrimitiveRenderSystem(OrthographicCamera(), renderer)
        val transform = TransformComponent(x = 3f, y = 4f, rotationDegrees = 30f)
        engine.addEntity(engine.createEntity().apply {
            add(transform)
            add(
                RenderPrimitiveComponent(
                    shape = PrimitiveShape.CIRCLE,
                    red = 0.1f,
                    green = 0.2f,
                    blue = 0.3f,
                    alpha = 0.4f,
                    radius = 0.75f,
                ),
            )
        })
        engine.addSystem(system)

        system.update(1f / 60f)

        assertEquals(listOf("circle:3.0,4.0,0.75"), renderer.draws)
        assertEquals(listOf("color:0.1,0.2,0.3,0.4"), renderer.colors)
        assertEquals(3f, transform.x, 0f)
        assertEquals(4f, transform.y, 0f)
        assertEquals(30f, transform.rotationDegrees, 0f)
    }

    @Test
    fun `entity without render primitive is ignored`() {
        val engine = Engine()
        val renderer = RecordingRenderer()
        val system = PrimitiveRenderSystem(OrthographicCamera(), renderer)
        engine.addEntity(engine.createEntity().apply { add(TransformComponent(1f, 2f)) })
        engine.addSystem(system)

        system.update(0f)

        assertEquals(emptyList<String>(), renderer.draws)
    }

    @Test
    fun `rectangle and line shapes are supported`() {
        val engine = Engine()
        val renderer = RecordingRenderer()
        val system = PrimitiveRenderSystem(OrthographicCamera(), renderer)
        engine.addEntity(engine.createEntity().apply {
            add(TransformComponent(1f, 2f))
            add(RenderPrimitiveComponent(shape = PrimitiveShape.RECTANGLE, width = 2f, height = 3f))
        })
        engine.addEntity(engine.createEntity().apply {
            add(TransformComponent(4f, 5f))
            add(RenderPrimitiveComponent(shape = PrimitiveShape.LINE, lineEndOffsetX = 2f, lineWidth = 0.1f))
        })
        engine.addSystem(system)

        system.update(0f)

        assertEquals(
            listOf("rectangle:1.0,2.0,2.0,3.0,0.0", "line:4.0,5.0,6.0,5.0,0.1"),
            renderer.draws,
        )
    }

    private class RecordingRenderer : PrimitiveRenderer {
        val draws = mutableListOf<String>()
        val colors = mutableListOf<String>()
        override fun begin(projection: Matrix4) = Unit
        override fun color(red: Float, green: Float, blue: Float, alpha: Float) {
            colors += "color:$red,$green,$blue,$alpha"
        }
        override fun circle(x: Float, y: Float, radius: Float) {
            draws += "circle:$x,$y,$radius"
        }
        override fun rectangle(centerX: Float, centerY: Float, width: Float, height: Float, rotationDegrees: Float) {
            draws += "rectangle:$centerX,$centerY,$width,$height,$rotationDegrees"
        }
        override fun line(startX: Float, startY: Float, endX: Float, endY: Float, width: Float) {
            draws += "line:$startX,$startY,$endX,$endY,$width"
        }
        override fun end() = Unit
        override fun dispose() = Unit
    }
}
