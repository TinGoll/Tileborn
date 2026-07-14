package game.client.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.physics.box2d.World
import game.client.ecs.component.CameraTargetComponent
import game.client.ecs.component.InterpolatedTransformComponent
import game.client.ecs.component.LocalPlayerComponent
import game.client.ecs.component.PhysicsInterpolatedTransformComponent
import game.client.ecs.component.PrimitiveShape
import game.client.ecs.component.RenderPrimitiveComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.DefinitionIdComponent
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.map.GameMapData
import game.shared.physics.PhysicsWorldFactory
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.NetworkEntityKind
import game.shared.constants.GameConstants

/** Creates the temporary primitive-rendered entities used by the client MVP. */
object ClientRenderEntityFactory {
    fun createTestPlayer(engine: Engine, physicsWorld: World, x: Float, y: Float): Entity =
        engine.createEntity().apply {
            add(TransformComponent(x = x, y = y))
            add(VelocityComponent())
            add(PhysicsBodyComponent(PhysicsWorldFactory.createDynamicPlayerBody(physicsWorld, x, y)))
            add(PhysicsInterpolatedTransformComponent(x = x, y = y))
            add(PlayerInputComponent())
            add(HealthComponent(GameConstants.PLAYER_MAX_HEALTH, GameConstants.PLAYER_MAX_HEALTH))
            add(MovementSpeedComponent(GameConstants.PLAYER_MOVE_SPEED))
            add(CharacterStateComponent())
            add(LocalPlayerComponent())
            add(
                RenderPrimitiveComponent(
                    shape = PrimitiveShape.CIRCLE,
                    red = 0.2f,
                    green = 0.75f,
                    blue = 1f,
                    radius = GameConstants.PLAYER_COLLISION_RADIUS,
                ),
            )
            add(CameraTargetComponent())
        }.also(engine::addEntity)

    fun createLocalPlayerFromSnapshot(engine: Engine, physicsWorld: World, snapshot: EntitySnapshot): Entity =
        engine.createEntity().apply {
            add(NetworkIdentityComponent(networkEntityId = snapshot.entityId.toLong()))
            add(TransformComponent(x = snapshot.x, y = snapshot.y))
            add(VelocityComponent(x = snapshot.velocityX, y = snapshot.velocityY))
            add(
                PhysicsBodyComponent(
                    PhysicsWorldFactory.createDynamicCircleBody(
                        physicsWorld,
                        snapshot.x,
                        snapshot.y,
                        snapshot.collisionRadius,
                    ),
                ),
            )
            add(PhysicsInterpolatedTransformComponent(x = snapshot.x, y = snapshot.y))
            add(PlayerInputComponent())
            add(HealthComponent(snapshot.currentHealth, snapshot.maxHealth))
            add(MovementSpeedComponent(snapshot.movementSpeed))
            add(CharacterStateComponent(snapshot.characterState))
            add(LocalPlayerComponent())
            add(
                RenderPrimitiveComponent(
                    shape = PrimitiveShape.CIRCLE,
                    red = 0.2f,
                    green = 0.75f,
                    blue = 1f,
                    radius = GameConstants.PLAYER_COLLISION_RADIUS,
                ),
            )
            add(CameraTargetComponent())
        }.also(engine::addEntity)

    fun createRemoteEntityFromSnapshot(engine: Engine, physicsWorld: World, snapshot: EntitySnapshot): Entity =
        engine.createEntity().apply {
            add(NetworkIdentityComponent(networkEntityId = snapshot.entityId.toLong()))
            add(TransformComponent(x = snapshot.x, y = snapshot.y))
            add(InterpolatedTransformComponent(x = snapshot.x, y = snapshot.y))
            add(VelocityComponent(x = snapshot.velocityX, y = snapshot.velocityY))
            add(
                PhysicsBodyComponent(
                    body = PhysicsWorldFactory.createKinematicCircleBody(
                        physicsWorld,
                        snapshot.x,
                        snapshot.y,
                        snapshot.collisionRadius,
                    ),
                    synchronizeVelocityWithBody = false,
                ),
            )
            add(HealthComponent(snapshot.currentHealth, snapshot.maxHealth))
            add(MovementSpeedComponent(snapshot.movementSpeed))
            add(CharacterStateComponent(snapshot.characterState))
            snapshot.definitionId?.let { add(DefinitionIdComponent(it)) }
            add(
                RenderPrimitiveComponent(
                    shape = PrimitiveShape.CIRCLE,
                    red = if (snapshot.entityKind == NetworkEntityKind.MOB) 0.25f else 1f,
                    green = if (snapshot.entityKind == NetworkEntityKind.MOB) 0.85f else 0.55f,
                    blue = if (snapshot.entityKind == NetworkEntityKind.MOB) 0.3f else 0.2f,
                    radius = snapshot.collisionRadius,
                ),
            )
        }.also(engine::addEntity)

    /** Makes otherwise invisible Tiled collision geometry visible during primitive-rendered MVP development. */
    fun createDebugCollisionGeometry(engine: Engine, mapData: GameMapData): List<Entity> =
        mapData.collisionObjects.map { collision ->
            engine.createEntity().apply {
                add(
                    TransformComponent(
                        x = collision.x + collision.width * 0.5f,
                        y = collision.y + collision.height * 0.5f,
                    ),
                )
                add(
                    RenderPrimitiveComponent(
                        shape = PrimitiveShape.RECTANGLE,
                        red = 0.18f,
                        green = 0.22f,
                        blue = 0.28f,
                        width = collision.width,
                        height = collision.height,
                    ),
                )
            }.also(engine::addEntity)
        }
}
