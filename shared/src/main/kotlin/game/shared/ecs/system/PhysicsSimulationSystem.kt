package game.shared.ecs.system

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Disposable
import game.shared.constants.GameConstants
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import java.util.IdentityHashMap

/** Advances Box2D at a fixed timestep and synchronizes ECS physics data explicitly. */
class PhysicsSimulationSystem(
    private val world: World,
) : EntitySystem(PRIORITY), EntityListener, Disposable {
    private var entities: ImmutableArray<Entity>? = null
    private val ownedBodies = IdentityHashMap<Entity, Body>()
    private var accumulator = 0f

    /** Fraction remaining until the next fixed step, used only for client-side visual interpolation. */
    val interpolationAlpha: Float
        get() = accumulator / GameConstants.PHYSICS_FIXED_TIME_STEP

    override fun addedToEngine(engine: Engine) {
        entities = engine.getEntitiesFor(FAMILY)
        engine.addEntityListener(FAMILY, this)
        for (entity in entities!!) entityAdded(entity)
    }

    override fun removedFromEngine(engine: Engine) {
        engine.removeEntityListener(this)
        entities = null
    }

    override fun entityAdded(entity: Entity) {
        ownedBodies[entity] = BODY_MAPPER.get(entity).body
    }

    override fun entityRemoved(entity: Entity) {
        ownedBodies.remove(entity)?.let { body ->
            if (body.world === world) world.destroyBody(body)
        }
    }

    override fun update(deltaTime: Float) {
        val physicsEntities = entities ?: return
        for (entity in physicsEntities) synchronizeEcsToBody(entity)

        accumulator += deltaTime.coerceIn(0f, MAX_FRAME_TIME)
        while (accumulator >= GameConstants.PHYSICS_FIXED_TIME_STEP) {
            for (entity in physicsEntities) {
                val physics = BODY_MAPPER.get(entity)
                physics.previousX = physics.body.position.x
                physics.previousY = physics.body.position.y
                physics.previousRotationRadians = physics.body.angle
            }
            world.step(
                GameConstants.PHYSICS_FIXED_TIME_STEP,
                GameConstants.PHYSICS_VELOCITY_ITERATIONS,
                GameConstants.PHYSICS_POSITION_ITERATIONS,
            )
            accumulator -= GameConstants.PHYSICS_FIXED_TIME_STEP
        }

        for (entity in physicsEntities) synchronizeBodyToEcs(entity)
    }

    private fun synchronizeEcsToBody(entity: Entity) {
        val physics = BODY_MAPPER.get(entity)
        val transform = TRANSFORM_MAPPER.get(entity)
        if (physics.synchronizeTransformToBody) {
            physics.body.setTransform(transform.x, transform.y, transform.rotationDegrees * MathUtils.degreesToRadians)
            physics.previousX = transform.x
            physics.previousY = transform.y
            physics.previousRotationRadians = transform.rotationDegrees * MathUtils.degreesToRadians
            physics.synchronizeTransformToBody = false
        }
        if (physics.synchronizeVelocityWithBody) {
            val velocity = VELOCITY_MAPPER.get(entity)
            physics.body.setLinearVelocity(velocity.x, velocity.y)
        }
    }

    private fun synchronizeBodyToEcs(entity: Entity) {
        val body = BODY_MAPPER.get(entity).body
        val transform = TRANSFORM_MAPPER.get(entity)
        transform.x = body.position.x
        transform.y = body.position.y
        transform.rotationDegrees = body.angle * MathUtils.radiansToDegrees
        if (BODY_MAPPER.get(entity).synchronizeVelocityWithBody) {
            val velocity = VELOCITY_MAPPER.get(entity)
            velocity.x = body.linearVelocity.x
            velocity.y = body.linearVelocity.y
        }
    }

    override fun dispose() {
        val bodies = ownedBodies.values.toList()
        ownedBodies.clear()
        for (body in bodies) {
            if (body.world === world) world.destroyBody(body)
        }
    }

    private companion object {
        const val PRIORITY = 300
        const val MAX_FRAME_TIME = 0.25f
        val BODY_MAPPER = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
        val TRANSFORM_MAPPER = ComponentMapper.getFor(TransformComponent::class.java)
        val VELOCITY_MAPPER = ComponentMapper.getFor(VelocityComponent::class.java)
        val FAMILY: Family = Family.all(
            PhysicsBodyComponent::class.java,
            TransformComponent::class.java,
            VelocityComponent::class.java,
        ).get()
    }
}
