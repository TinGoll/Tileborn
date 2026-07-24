package game.server

import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.ServerEcsWorld
import game.server.ecs.component.AggroTargetComponent
import game.server.ecs.component.AiStateComponent
import game.server.ecs.component.HomePositionComponent
import game.server.ecs.component.MobComponent
import game.server.ecs.component.NpcControllerComponent
import game.server.ecs.component.ServerAuthorityComponent
import game.server.ecs.component.SpawnOriginComponent
import game.server.persistence.SavedCharacterState
import game.shared.constants.GameConstants
import game.shared.constants.InterestManagementConstants
import game.shared.definition.DefinitionRegistry
import game.shared.ecs.component.DefinitionIdComponent
import game.shared.ecs.component.AttackComponent
import game.shared.ecs.component.CharacterState
import game.shared.ecs.component.CharacterStateComponent
import game.shared.ecs.component.CooldownComponent
import game.shared.ecs.component.HealthComponent
import game.shared.ecs.component.MovementSpeedComponent
import game.shared.ecs.component.NetworkIdentityComponent
import game.shared.ecs.component.PhysicsBodyComponent
import game.shared.ecs.component.PlayerInputComponent
import game.shared.ecs.component.PathComponent
import game.shared.ecs.component.TransformComponent
import game.shared.ecs.component.VelocityComponent
import game.shared.input.InputCommandValidator
import game.shared.map.GameMapData
import game.shared.map.MapInteractableType
import game.shared.map.TiledGameplayMapParser
import game.shared.map.interactableById
import game.shared.physics.PhysicsWorldFactory
import game.shared.physics.TiledCollisionLoader
import game.shared.navigation.NavigationGrid
import game.shared.protocol.EntitySnapshot
import game.shared.protocol.AttackCommand
import game.shared.protocol.CombatEvent
import game.shared.protocol.DamageEvent
import game.shared.protocol.GameEvent
import game.shared.protocol.GameEventType
import game.shared.protocol.InteractCommand
import game.shared.protocol.InputCommand
import game.shared.protocol.NetworkEntityKind
import game.shared.protocol.WorldSnapshot

/** Authoritative server world state: ECS engine plus gameplay-only map metadata. */
class ServerWorld(
    mapId: String,
    mapPath: String,
    val definitionRegistry: DefinitionRegistry = DefinitionRegistry.empty(),
    private val ecsWorld: ServerEcsWorld = ServerEcsWorld(),
) : Disposable {
    val gameMapData: GameMapData
    val navigationGrid: NavigationGrid
    val engine = ecsWorld.engine
    private val latestReceivedInputByEntityId = mutableMapOf<Int, Long>()
    private val lastSimulatedInputByEntityId = mutableMapOf<Int, Long>()

    init {
        val tiledMap = TmxMapLoader().load(mapPath)
        try {
            gameMapData = TiledGameplayMapParser(::println).parse(mapId, tiledMap)
        } finally {
            tiledMap.dispose()
        }
        TiledCollisionLoader(ecsWorld.physicsWorld).load(gameMapData)
        navigationGrid = NavigationGrid.fromMap(gameMapData)
        ecsWorld.configureNavigation(navigationGrid)
        // DefinitionRegistry.empty() remains useful for focused world tests that do not exercise
        // definitions. Production startup always supplies the loaded, validated registry.
        val managedNpcSpawnPoints = if (definitionRegistry.mobCount == 0) emptyList() else gameMapData.npcSpawnPoints
        ecsWorld.configureMobLifecycle(managedNpcSpawnPoints, definitionRegistry) {
                entityId, definitionId, x, y, spawnId ->
            spawnMob(entityId, definitionId, x, y, spawnId)
        }
    }

    @Synchronized
    fun update(deltaTime: Float) {
        engine.update(deltaTime)
        // Inputs become safe to acknowledge only after the authoritative fixed tick has used
        // their validated state. A receive-time acknowledgement would make clients discard input
        // before the corresponding movement appears in a server snapshot.
        lastSimulatedInputByEntityId.putAll(latestReceivedInputByEntityId)
    }

    @Synchronized
    fun spawnPlayer(
        serverEntityId: Int,
        spawnId: String = DEFAULT_SPAWN_ID,
        savedState: SavedCharacterState? = null,
    ) = gameMapData.requireSpawnPoint(spawnId).let { spawn ->
            val position = savedState
                ?.takeIf { it.mapId == gameMapData.mapId }
                ?.let { it.positionX to it.positionY }
                ?: (spawn.x to spawn.y)
            engine.createEntity().apply {
                add(TransformComponent(x = position.first, y = position.second))
                add(NetworkIdentityComponent(networkEntityId = serverEntityId.toLong()))
                add(PlayerInputComponent())
                add(VelocityComponent())
                add(HealthComponent(GameConstants.PLAYER_MAX_HEALTH, GameConstants.PLAYER_MAX_HEALTH))
                add(
                    AttackComponent(
                        range = GameConstants.PLAYER_ATTACK_RANGE,
                        damage = GameConstants.PLAYER_ATTACK_DAMAGE,
                        minimumDirectionDot = GameConstants.PLAYER_ATTACK_MIN_DIRECTION_DOT,
                    ),
                )
                add(CooldownComponent(GameConstants.PLAYER_ATTACK_COOLDOWN_SECONDS))
                add(MovementSpeedComponent(GameConstants.PLAYER_MOVE_SPEED))
                add(CharacterStateComponent())
                add(PhysicsBodyComponent(PhysicsWorldFactory.createDynamicPlayerBody(ecsWorld.physicsWorld, position.first, position.second)))
                add(ServerAuthorityComponent())
            }.also(engine::addEntity)
        }

    /** Creates a mob instance from validated static data; components hold runtime state only. */
    @Synchronized
    fun spawnMob(
        serverEntityId: Int,
        definitionId: String,
        x: Float,
        y: Float,
        spawnOriginId: String = "manual:$serverEntityId",
    ) =
        definitionRegistry.requireMob(definitionId).let { definition ->
            engine.createEntity().apply {
                add(TransformComponent(x = x, y = y))
                add(NetworkIdentityComponent(networkEntityId = serverEntityId.toLong()))
                add(DefinitionIdComponent(definitionId))
                add(HealthComponent(currentHealth = definition.maxHealth, maxHealth = definition.maxHealth))
                add(MovementSpeedComponent(definition.movementSpeed))
                add(CharacterStateComponent())
                add(VelocityComponent())
                add(PathComponent(definition.collisionRadius))
                add(
                    AttackComponent(
                        range = definition.attackRadius,
                        damage = definition.attackDamage,
                        minimumDirectionDot = MOB_ATTACK_MIN_DIRECTION_DOT,
                    ),
                )
                add(CooldownComponent(definition.attackCooldown))
                add(
                    PhysicsBodyComponent(
                        PhysicsWorldFactory.createDynamicCircleBody(
                            ecsWorld.physicsWorld,
                            x,
                            y,
                            definition.collisionRadius,
                        ),
                    ),
                )
                add(MobComponent())
                add(AiStateComponent(aggroRadius = definition.aggroRadius, attackRadius = definition.attackRadius))
                add(AggroTargetComponent())
                add(HomePositionComponent(x, y))
                add(SpawnOriginComponent(spawnOriginId))
                add(NpcControllerComponent())
                add(ServerAuthorityComponent())
            }.also(engine::addEntity)
        }

    /** Reads an authoritative ECS position at a persistence boundary, never during normal ticks. */
    @Synchronized
    fun playerPosition(serverEntityId: Int): Pair<Float, Float>? =
        engine.entities.firstOrNull { candidate ->
            candidate.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        }?.getComponent(TransformComponent::class.java)?.let { it.x to it.y }

    @Synchronized
    fun despawnPlayer(serverEntityId: Int): Boolean {
        val entity = engine.entities.firstOrNull { candidate ->
            candidate.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return false
        engine.removeEntity(entity)
        latestReceivedInputByEntityId.remove(serverEntityId)
        lastSimulatedInputByEntityId.remove(serverEntityId)
        return true
    }

    @Synchronized
    fun applyInput(serverEntityId: Int, command: InputCommand): Boolean {
        val latestReceived = latestReceivedInputByEntityId[serverEntityId]
        if (latestReceived != null && command.inputSequence <= latestReceived) return false
        val entity = engine.entities.firstOrNull { entity ->
            entity.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return false
        val input = entity.getComponent(PlayerInputComponent::class.java) ?: return false
        if (entity.getComponent(CharacterStateComponent::class.java)?.state != CharacterState.ALIVE) {
            input.state.moveX = 0f
            input.state.moveY = 0f
            input.state.attack = false
            input.state.interact = false
            latestReceivedInputByEntityId[serverEntityId] = command.inputSequence
            return false
        }
        val validated = InputCommandValidator.toInputState(command)
        input.state.moveX = validated.moveX
        input.state.moveY = validated.moveY
        input.state.attack = validated.attack
        input.state.interact = validated.interact
        input.state.aimX = validated.aimX
        input.state.aimY = validated.aimY
        latestReceivedInputByEntityId[serverEntityId] = command.inputSequence
        return true
    }

    /** Applies server-authored damage through DamageSystem; intended for server gameplay/admin hooks. */
    @Synchronized
    fun applyDamage(serverEntityId: Int, amount: Float): Boolean {
        val entity = engine.entities.firstOrNull { candidate ->
            candidate.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return false
        if (entity.getComponent(HealthComponent::class.java) == null) return false
        return ecsWorld.damageSystem.applyDamage(
            DamageEvent(
                eventId = ecsWorld.combatEventSystem.nextEventId(),
                hitEventId = NO_HIT_EVENT_ID,
                sourceEntityId = serverEntityId,
                targetEntityId = serverEntityId,
                amount = amount,
            ),
        )
    }

    /** Queues one attack intent; duplicate/stale sequences are rejected before validation. */
    @Synchronized
    fun queueAttack(serverEntityId: Int, command: AttackCommand): Boolean {
        val entity = engine.entities.firstOrNull { candidate ->
            candidate.getComponent(NetworkIdentityComponent::class.java)?.networkEntityId == serverEntityId.toLong()
        } ?: return false
        return ecsWorld.attackCommandSystem.enqueue(entity, command)
    }

    /** Returns legacy miss notifications produced by authoritative hit detection. */
    @Synchronized
    fun drainAttackEvents(): List<GameEvent> = ecsWorld.attackValidationSystem.drainMissedAttackEvents()

    /** Returns ordered authoritative combat events ready for client delivery. */
    @Synchronized
    fun drainCombatEvents(): List<CombatEvent> = ecsWorld.combatEventSystem.drainOutboundEvents()

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
                    val health = entity.getComponent(HealthComponent::class.java) ?: return@mapNotNull null
                    val movementSpeed = entity.getComponent(MovementSpeedComponent::class.java) ?: return@mapNotNull null
                    val characterState = entity.getComponent(CharacterStateComponent::class.java) ?: return@mapNotNull null
                    val physicsBody = entity.getComponent(PhysicsBodyComponent::class.java)
                        ?: error("Network entity ${identity.networkEntityId} has no authoritative physics body")
                    val collisionRadius = physicsBody.body.fixtureList.firstOrNull()?.shape?.radius
                        ?: error("Network entity ${identity.networkEntityId} has no collision fixture")
                    val definitionId = entity.getComponent(DefinitionIdComponent::class.java)?.definitionId
                    EntitySnapshot(
                        entityId = identity.networkEntityId.toInt(),
                        x = transform.x,
                        y = transform.y,
                        velocityX = velocity?.x ?: 0f,
                        velocityY = velocity?.y ?: 0f,
                        currentHealth = health.currentHealth,
                        maxHealth = health.maxHealth,
                        movementSpeed = movementSpeed.movementSpeed,
                        characterState = characterState.state,
                        collisionRadius = collisionRadius,
                        entityKind = if (entity.getComponent(MobComponent::class.java) != null) {
                            NetworkEntityKind.MOB
                        } else {
                            NetworkEntityKind.PLAYER
                        },
                        definitionId = definitionId,
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
            acknowledgedInputSequence = lastSimulatedInputByEntityId[recipientEntityId]
                ?: snapshot.acknowledgedInputSequence,
        )
    }

    @Synchronized
    fun acknowledgedInputSequence(serverEntityId: Int): Long =
        lastSimulatedInputByEntityId[serverEntityId] ?: WorldSnapshot.NO_ACKNOWLEDGED_INPUT_SEQUENCE

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
        const val NO_HIT_EVENT_ID = 0L
        const val MOB_ATTACK_MIN_DIRECTION_DOT = 0f
    }
}

sealed interface InteractionResult {
    data class Accepted(val event: GameEvent) : InteractionResult
    data class Rejected(val reason: String) : InteractionResult
}
