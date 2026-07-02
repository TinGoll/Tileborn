package game.client.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import game.client.ecs.component.CameraTargetComponent
import game.client.ecs.component.PrimitiveShape
import game.client.ecs.component.RenderPrimitiveComponent
import game.shared.ecs.component.TransformComponent

/** Creates the temporary primitive-rendered entities used by the client MVP. */
object ClientRenderEntityFactory {
    fun createTestPlayer(engine: Engine, x: Float, y: Float): Entity =
        engine.createEntity().apply {
            add(TransformComponent(x = x, y = y))
            add(
                RenderPrimitiveComponent(
                    shape = PrimitiveShape.CIRCLE,
                    red = 0.2f,
                    green = 0.75f,
                    blue = 1f,
                    radius = 0.4f,
                ),
            )
            add(CameraTargetComponent())
        }.also(engine::addEntity)
}
