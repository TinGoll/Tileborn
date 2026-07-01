package game.client.ecs.component

import com.badlogic.ashley.core.Component

/** Client-only primitive render data. */
class RenderPrimitiveComponent(
    var shape: PrimitiveShape = PrimitiveShape.CIRCLE,
    var red: Float = 1f,
    var green: Float = 1f,
    var blue: Float = 1f,
    var alpha: Float = 1f,
    var radius: Float = 0.5f,
    var width: Float = 1f,
    var height: Float = 1f,
) : Component

enum class PrimitiveShape {
    CIRCLE,
    RECTANGLE,
    LINE,
}
