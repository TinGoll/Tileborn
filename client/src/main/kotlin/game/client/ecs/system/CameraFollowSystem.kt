package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.graphics.OrthographicCamera
import game.client.ecs.component.CameraTargetComponent
import game.client.ecs.component.PhysicsInterpolatedTransformComponent
import game.shared.ecs.component.TransformComponent

/** Centers the client camera on the first explicitly marked camera target. */
class CameraFollowSystem(
    private val camera: OrthographicCamera,
) : EntitySystem(PRIORITY) {
    private var targets: ImmutableArray<com.badlogic.ashley.core.Entity>? = null

    override fun addedToEngine(engine: Engine) {
        targets = engine.getEntitiesFor(FAMILY)
    }

    override fun removedFromEngine(engine: Engine) {
        targets = null
    }

    override fun update(deltaTime: Float) {
        val target = targets?.firstOrNull() ?: return
        val transform = TRANSFORM_MAPPER.get(target)
        val renderTransform = PHYSICS_INTERPOLATED_TRANSFORM_MAPPER.get(target)
        camera.position.set(renderTransform?.x ?: transform.x, renderTransform?.y ?: transform.y, 0f)
    }

    private companion object {
        const val PRIORITY = 700
        val TRANSFORM_MAPPER: ComponentMapper<TransformComponent> =
            ComponentMapper.getFor(TransformComponent::class.java)
        val PHYSICS_INTERPOLATED_TRANSFORM_MAPPER: ComponentMapper<PhysicsInterpolatedTransformComponent> =
            ComponentMapper.getFor(PhysicsInterpolatedTransformComponent::class.java)
        val FAMILY: Family = Family.all(
            TransformComponent::class.java,
            CameraTargetComponent::class.java,
        ).get()
    }
}
