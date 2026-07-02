package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import game.client.ecs.component.PrimitiveShape
import game.client.ecs.component.RenderPrimitiveComponent
import game.shared.ecs.component.TransformComponent

/** Draws client primitives from ECS data without modifying gameplay components. */
class PrimitiveRenderSystem(
    private val camera: OrthographicCamera,
    private val renderer: PrimitiveRenderer = GdxPrimitiveRenderer(),
) : EntitySystem(PRIORITY), Disposable {
    private var renderEntities: ImmutableArray<Entity>? = null

    override fun addedToEngine(engine: Engine) {
        renderEntities = engine.getEntitiesFor(FAMILY)
    }

    override fun removedFromEngine(engine: Engine) {
        renderEntities = null
    }

    override fun update(deltaTime: Float) {
        val entities = renderEntities ?: return
        renderer.begin(camera.combined)
        for (entity in entities) {
            render(entity)
        }
        renderer.end()
    }

    private fun render(entity: Entity) {
        val transform = TRANSFORM_MAPPER.get(entity)
        val primitive = RENDER_MAPPER.get(entity)
        renderer.color(primitive.red, primitive.green, primitive.blue, primitive.alpha)

        when (primitive.shape) {
            PrimitiveShape.CIRCLE -> renderer.circle(transform.x, transform.y, primitive.radius)
            PrimitiveShape.RECTANGLE -> renderer.rectangle(
                transform.x,
                transform.y,
                primitive.width,
                primitive.height,
                transform.rotationDegrees,
            )
            PrimitiveShape.LINE -> {
                val cos = MathUtils.cosDeg(transform.rotationDegrees)
                val sin = MathUtils.sinDeg(transform.rotationDegrees)
                val endX = transform.x + primitive.lineEndOffsetX * cos - primitive.lineEndOffsetY * sin
                val endY = transform.y + primitive.lineEndOffsetX * sin + primitive.lineEndOffsetY * cos
                renderer.line(transform.x, transform.y, endX, endY, primitive.lineWidth)
            }
        }
    }

    override fun dispose() = renderer.dispose()

    private companion object {
        const val PRIORITY = 900
        val TRANSFORM_MAPPER: ComponentMapper<TransformComponent> =
            ComponentMapper.getFor(TransformComponent::class.java)
        val RENDER_MAPPER: ComponentMapper<RenderPrimitiveComponent> =
            ComponentMapper.getFor(RenderPrimitiveComponent::class.java)
        val FAMILY: Family = Family.all(
            TransformComponent::class.java,
            RenderPrimitiveComponent::class.java,
        ).get()
    }
}

interface PrimitiveRenderer : Disposable {
    fun begin(projection: Matrix4)
    fun color(red: Float, green: Float, blue: Float, alpha: Float)
    fun circle(x: Float, y: Float, radius: Float)
    fun rectangle(centerX: Float, centerY: Float, width: Float, height: Float, rotationDegrees: Float)
    fun line(startX: Float, startY: Float, endX: Float, endY: Float, width: Float)
    fun end()
}

private class GdxPrimitiveRenderer : PrimitiveRenderer {
    private val shapeRenderer = ShapeRenderer()

    override fun begin(projection: Matrix4) {
        shapeRenderer.projectionMatrix = projection
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
    }

    override fun color(red: Float, green: Float, blue: Float, alpha: Float) {
        shapeRenderer.setColor(red, green, blue, alpha)
    }

    override fun circle(x: Float, y: Float, radius: Float) = shapeRenderer.circle(x, y, radius, 24)

    override fun rectangle(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotationDegrees: Float,
    ) = shapeRenderer.rect(
        centerX - width * 0.5f,
        centerY - height * 0.5f,
        width * 0.5f,
        height * 0.5f,
        width,
        height,
        1f,
        1f,
        rotationDegrees,
    )

    override fun line(startX: Float, startY: Float, endX: Float, endY: Float, width: Float) =
        shapeRenderer.rectLine(startX, startY, endX, endY, width)

    override fun end() = shapeRenderer.end()

    override fun dispose() = shapeRenderer.dispose()
}
