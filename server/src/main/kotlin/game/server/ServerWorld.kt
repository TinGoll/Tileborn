package game.server

import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.component.ServerAuthorityComponent
import game.server.ecs.ServerEcsWorld
import game.shared.constants.InterestManagementConstants
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.input.InputCommandValidator
import game.shared.map.GameMapData
import game.shared.map.TiledGameplayMapParser
import game.shared.map.MapInteractableType
import game.shared.map.interactableById
import game.shared.physics.PhysicsWorldFactory
import game.shared.physics.TiledCollisionLoader
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.GameEvent
import game.shared.protocol.GameEventType
import game.shared.protocol.InteractCommand
import game.shared.protocol.InputCommand
import game.shared.protocol.WorldSnapshot

/** Authoritative server world state: ECS engine plus gameplay-only map metadata. */
class ServerWorld(
    mapId: String,
    mapPath: String,
    private val ecsWorld: ServerEcsWorld = ServerEcsWorld(),
) : Disposable {
    val gameMapData: GameMapData
    val engine = ecsWorld.engine
    private val lastAcknowledgedInputByEntityId = mutableMapOf<Int, Long>()

    init {
        val tiledMap = TmxMapLoader().load(mapPath)
        try {
            gameMapData = TiledGameplayMapParser(::println).parse(mapId, tiledMap)
        } finally {
            tiledMap.dispose()
        }
        TiledCollisionLoader(ecsWorld.physicsWorld).load(gameMapData)
    }

    @Synchronized
    fun update(deltaTime: Float) {
        engine.update(deltaTime)
    }

    @Synchronized
    fun spawnPlayer(serverEntityId: Int, spawnId: String = DEFAULT_SPAWN_ID) =
        gameMapData.requireSpawnPoint(spawnId).let { spawn ->
            engine.createEntity().apply {
                add(TransformComponent(x = spawn.x, y = spawn.y))
                add(NetworkIdentityComponent(networkEntityId = serverEntityId.toLong()))
                add(PlayerInputComponent())
                add(VelocityComponent())
                add(PhysicsBodyComponent(PhysicsWorldFactory.createDynamicPlayerBody(ecsWorld.physicsWorld, spawn.x, spawn.y)))
                add(ServerAuthorityComponent())
            }.also(engine::addEntity)
        }

    @Synchronized
    fun despawnPlayer(serverEntityId: Int): Boolean {
        val entity = engine.entities.firstOrNull { candidate ->
            candidate.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return false
        engine.removeEntity(entity)
        lastAcknowledgedInputByEntityId.remove(serverEntityId)
        return true
    }

    @Synchronized
    fun applyInput(serverEntityId: Int, command: InputCommand): Boolean {
        val lastAcknowledged = lastAcknowledgedInputByEntityId[serverEntityId]
        if (lastAcknowledged != null && command.inputSequence <= lastAcknowledged) return false
        val entity = engine.entities.firstOrNull { entity ->
            entity.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return false
        val input = entity.getComponent(PlayerInputComponent::class.java) ?: return false
        val validated = InputCommandValidator.toInputState(command)
        input.state.moveX = validated.moveX
        input.state.moveY = validated.moveY
        input.state.attack = validated.attack
        input.state.interact = validated.interact
        input.state.aimX = validated.aimX
        input.state.aimY = validated.aimY
        lastAcknowledgedInputByEntityId[serverEntityId] = command.inputSequence
        return true
    }

    /** Validates and executes a player interaction using only authoritative ECS/map state. */
    @Synchronized
    fun interact(serverEntityId: Int, command: InteractCommand): InteractionResult {
        val entity = engine.entities.firstOrNull { candidate ->
            candidate.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return InteractionResult.Rejected("unknown player")
        val transform = entity.getComponent(TransformComponent::class.java)
            ?: return InteractionResult.Rejected("player has no transform")
        val target = gameMapData.interactableById(command.targetObjectId)
            ?: return InteractionResult.Rejected("unknown object id ${command.targetObjectId}")
        if (squaredDistanceToRectangle(transform.x, transform.y, target.x, target.y, target.width, target.height) >
            INTERACTION_RADIUS_WORLD_UNITS * INTERACTION_RADIUS_WORLD_UNITS
        ) return InteractionResult.Rejected("object id ${target.id} is out of range")

        return when (target.type) {
            MapInteractableType.TRIGGER -> {
                val trigger = gameMapData.triggers.first { it.id == target.id }
                if (trigger.type != MESSAGE_TRIGGER_TYPE) {
                    return InteractionResult.Rejected("unsupported trigger type ${trigger.type}")
                }
                InteractionResult.Accepted(
                    GameEvent(GameEventType.TRIGGER_ENTERED, target.id, "player entered trigger ${trigger.triggerId}"),
                )
            }
            MapInteractableType.PORTAL -> {
                val portal = gameMapData.portals.first { it.id == target.id }
                if (portal.targetMap != gameMapData.mapId) {
                    return InteractionResult.Rejected("portal ${portal.portalId} targets unsupported map ${portal.targetMap}")
                }
                val spawn = gameMapData.requireSpawnPoint(portal.targetSpawn)
                transform.x = spawn.x
                transform.y = spawn.y
                entity.getComponent(PhysicsBodyComponent::class.java)?.synchronizeTransformToBody = true
                InteractionResult.Accepted(
                    GameEvent(GameEventType.PORTAL_USED, target.id, "player used portal ${portal.portalId}"),
                )
            }
        }
    }

    @Synchronized
    fun buildSnapshot(
        serverTick: Long,
        acknowledgedInputSequence: Long = WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE,
    ): WorldSnapshot =
        WorldSnapshot(
            serverTick = serverTick,
            entities = engine.entities
                .mapNotNull { entity ->
                    val identity = entity.getComponent(NetworkIdentityComponent::class.java) ?: return@mapNotNull null
                    val transform = entity.getComponent(TransformComponent::class.java) ?: return@mapNotNull null
                    val velocity = entity.getComponent(VelocityComponent::class.java)
                    EntitySnapshot(
                        entityId = identity.networkEntityId.toInt(),
                        x = transform.x,
                        y = transform.y,
                        velocityX = velocity?.x ?: 0f,
                        velocityY = velocity?.y ?: 0f,
                    )
                },
            acknowledgedInputSequence = acknowledgedInputSequence,
        )

    /**
     * Builds the authoritative snapshot visible to one recipient.
     *
     * The recipient is always included so input acknowledgement and local prediction remain valid.
     */
    @Synchronized
    fun buildSnapshotForRecipient(
        recipientEntityId: Int,
        serverTick: Long,
        acknowledgedInputSequence: Long = WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE,
    ): WorldSnapshot =
        filterSnapshotForRecipient(
            recipientEntityId = recipientEntityId,
            snapshot = buildSnapshot(serverTick, acknowledgedInputSequence),
        )

    @Synchronized
    fun filterSnapshotForRecipient(recipientEntityId: Int, snapshot: WorldSnapshot): WorldSnapshot {
        val recipient = snapshot.entities.firstOrNull { it.entityId == recipientEntityId }
            ?: return snapshot.copy(entities = emptyList())
        val radiusSquared = InterestManagementConstants.VISIBILITY_RADIUS_WORLD_UNITS *
            InterestManagementConstants.VISIBILITY_RADIUS_WORLD_UNITS

        return snapshot.copy(
            entities = snapshot.entities.filter { candidate ->
                candidate.entityId == recipientEntityId ||
                    squaredDistance(recipient, candidate) <= radiusSquared
            },
        )
    }

    @Synchronized
    fun acknowledgedInputSequence(serverEntityId: Int): Long =
        lastAcknowledgedInputByEntityId[serverEntityId] ?: WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE

    private fun squaredDistance(first: EntitySnapshot, second: EntitySnapshot): Float {
        val x = first.x - second.x
        val y = first.y - second.y
        return x * x + y * y
    }

    private fun squaredDistanceToRectangle(
        pointX: Float, pointY: Float, x: Float, y: Float, width: Float, height: Float,
    ): Float {
        val nearestX = pointX.coerceIn(x, x + width)
        val nearestY = pointY.coerceIn(y, y + height)
        val deltaX = pointX - nearestX
        val deltaY = pointY - nearestY
        return deltaX * deltaX + deltaY * deltaY
    }

    override fun dispose() {
        ecsWorld.dispose()
    }

    private companion object {
        const val DEFAULT_SPAWN_ID = "default"
        const val INTERACTION_RADIUS_WORLD_UNITS = 1f
        const val MESSAGE_TRIGGER_TYPE = "message"
    }
}

sealed interface InteractionResult {
    data class Accepted(val event: GameEvent) : InteractionResult
    data class Rejected(val reason: String) : InteractionResult
}
