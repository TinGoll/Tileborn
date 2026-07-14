package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.network.TcpGameServer
import game.server.persistence.CharacterPersistenceService
import game.server.persistence.CharacterRepository
import game.server.persistence.InMemoryCharacterRepository
import game.server.persistence.InMemorySessionRepository
import game.server.persistence.SessionRepository
import game.shared.definition.DefinitionLoader
import game.shared.definition.DefinitionRegistry
import game.shared.protocol.InteractCommand
import game.shared.protocol.NetworkDefaults

/** Coordinates authoritative server startup, fixed-tick execution, and shutdown. */
class ServerApplication(
    private val mapId: String = DEFAULT_MAP_ID,
    private val mapPath: String = DEFAULT_MAP_PATH,
    private val networkPort: Int = NetworkDefaults.PORT,
    private val worldFactory: (String, String, DefinitionRegistry) -> ServerWorld = ::ServerWorld,
    private val definitionLoader: () -> DefinitionRegistry = {
        DefinitionLoader().load(
            Gdx.files.internal(DEFAULT_MOBS_PATH),
            Gdx.files.internal(DEFAULT_ITEMS_PATH),
        )
    },
    logTicks: Boolean = false,
    private val loop: ServerGameLoop = ServerGameLoop(logTicks = logTicks),
    private val consoleFactory: ((() -> Unit, (String) -> Unit) -> ServerConsole)? = { onStopRequested, consoleLogger ->
        ServerConsole(onStopRequested = onStopRequested, logger = consoleLogger)
    },
    private val characterRepository: CharacterRepository = InMemoryCharacterRepository(),
    private val sessionRepository: SessionRepository = InMemorySessionRepository(),
    private val logger: (String) -> Unit = ::println,
) {
    private var world: ServerWorld? = null
    private var networkServer: TcpGameServer? = null
    private var ownedHeadlessApplication: HeadlessApplication? = null
    private var stopped: Boolean = true
    private val characterPersistence = CharacterPersistenceService(characterRepository, sessionRepository)

    fun run(maxTicks: Long? = null) {
        ensureHeadlessApplication()
        stopped = false
        logger("Server starting at ${ServerGameLoop.DEFAULT_TICK_RATE} ticks/sec")
        val definitions = definitionLoader()
        logger("Loaded gameplay definitions mobs=${definitions.mobCount} items=${definitions.itemCount}")
        val serverWorld = worldFactory(mapId, mapPath, definitions)
        world = serverWorld
        logger(
            "Loaded gameplay map '${serverWorld.gameMapData.mapId}' " +
                "spawns=${serverWorld.gameMapData.spawnPoints.size} " +
                "collisions=${serverWorld.gameMapData.collisionObjects.size}",
        )
        logger("Loaded NPC spawn points=${serverWorld.gameMapData.npcSpawnPoints.size}")
        networkServer = TcpGameServer(
            port = networkPort,
            mapIdProvider = { serverWorld.gameMapData.mapId },
            serverTickProvider = { loop.serverTick },
            initialSnapshotProvider = { playerEntityId ->
                serverWorld.spawnPlayer(playerEntityId, savedState = characterPersistence.stateForEntity(playerEntityId))
                serverWorld.buildSnapshot(loop.serverTick)
            },
            inputCommandHandler = { playerEntityId, inputCommand ->
                serverWorld.applyInput(playerEntityId, inputCommand)
                // Input is consumed by the fixed authoritative loop. Returning no snapshot keeps
                // outbound state on the fixed 20 Hz broadcast instead of echoing a full world
                // snapshot for every render-rate input command.
                null
            },
            attackCommandHandler = { playerEntityId, command ->
                if (serverWorld.queueAttack(playerEntityId, command)) {
                    logger("Attack queued entity=$playerEntityId sequence=${command.inputSequence}")
                } else {
                    logger("Attack rejected entity=$playerEntityId sequence=${command.inputSequence}: duplicate, stale, or invalid")
                }
            },
            interactCommandHandler = { playerEntityId, command ->
                when (val result = serverWorld.interact(playerEntityId, command)) {
                    is InteractionResult.Accepted -> {
                        logger("Interaction accepted entity=$playerEntityId object=${command.targetObjectId} event=${result.event.eventType}")
                        result.event
                    }
                    is InteractionResult.Rejected -> {
                        logger("Interaction rejected entity=$playerEntityId object=${command.targetObjectId}: ${result.reason}")
                        null
                    }
                }
            },
            disconnectSnapshotProvider = { playerEntityId ->
                serverWorld.despawnPlayer(playerEntityId)
                characterPersistence.forgetEntity(playerEntityId)
                serverWorld.buildSnapshot(loop.serverTick)
            },
            reconnectSnapshotProvider = {
                serverWorld.buildSnapshot(loop.serverTick)
            },
            snapshotForRecipient = serverWorld::filterSnapshotForRecipient,
            sessionEstablishedHandler = { session ->
                val spawn = serverWorld.gameMapData.requireSpawnPoint("default")
                characterPersistence.restoreForJoin(
                    entityId = session.entityId,
                    sessionToken = session.sessionToken,
                    nickname = session.playerName,
                    defaultMapId = serverWorld.gameMapData.mapId,
                    defaultPositionX = spawn.x,
                    defaultPositionY = spawn.y,
                )
            },
            sessionDisconnectedHandler = { playerEntityId ->
                serverWorld.playerPosition(playerEntityId)?.let { (x, y) ->
                    characterPersistence.saveOnDisconnect(
                        entityId = playerEntityId,
                        mapId = serverWorld.gameMapData.mapId,
                        positionX = x,
                        positionY = y,
                    )
                }
            },
            logger = logger,
        ).also { it.start() }

        Runtime.getRuntime().addShutdownHook(Thread { stop() })
        consoleFactory?.invoke(::stop, logger)?.start()

        try {
            loop.run(maxTicks = maxTicks) { fixedDelta ->
                serverWorld.update(fixedDelta)
                serverWorld.drainCombatEvents().forEach { event ->
                    networkServer?.broadcastCombatEvent(event)
                }
                serverWorld.drainAttackEvents().forEach { event ->
                    networkServer?.broadcastGameEvent(event)
                }
                networkServer?.broadcastSnapshot(serverWorld.buildSnapshot(loop.serverTick))
            }
        } finally {
            stop()
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        loop.stop()
        networkServer?.close()
        networkServer = null
        world?.dispose()
        world = null
        ownedHeadlessApplication?.exit()
        ownedHeadlessApplication = null
        logger("Server stopped")
    }

    private fun ensureHeadlessApplication() {
        if (Gdx.app == null) {
            ownedHeadlessApplication = HeadlessApplication(object : ApplicationAdapter() {})
        }
    }

    private companion object {
        const val DEFAULT_MAP_ID = "debug_map"
        const val DEFAULT_MAP_PATH = "maps/debug_map.tmx"
        const val DEFAULT_MOBS_PATH = "definitions/mobs.json"
        const val DEFAULT_ITEMS_PATH = "definitions/items.json"
    }
}
