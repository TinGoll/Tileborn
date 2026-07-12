package game.client.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import game.client.ecs.component.PhysicsInterpolatedTransformComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.system.PhysicsSimulationSystem

/** Produces smooth visual positions without changing authoritative Box2D state. */
class PhysicsInterpolationSystem(
    private val physicsSimulationSystem: PhysicsSimulationSystem,
) : IteratingSystem(FAMILY, PRIORITY) {
    override fun processEntity(entity: Entity, deltaTime: Float) {
        val physics = PHYSICS_MAPPER.get(entity)
        val renderTransform = RENDER_TRANSFORM_MAPPER.get(entity)
        val alpha = physicsSimulationSystem.interpolationAlpha
        val body = physics.body
        renderTransform.x = MathUtils.lerp(physics.previousX, body.position.x, alpha)
        renderTransform.y = MathUtils.lerp(physics.previousY, body.position.y, alpha)
        renderTransform.rotationDegrees = MathUtils.lerp(
            physics.previousRotationRadians,
            body.angle,
            alpha,
        ) * MathUtils.radiansToDegrees
    }

    private companion object {
        const val PRIORITY = 350
        val PHYSICS_MAPPER = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
        val RENDER_TRANSFORM_MAPPER = ComponentMapper.getFor(PhysicsInterpolatedTransformComponent::class.java)
        val FAMILY: Family = Family.all(
            PhysicsBodyComponent::class.java,
            PhysicsInterpolatedTransformComponent::class.java,
        ).get()
    }
}
