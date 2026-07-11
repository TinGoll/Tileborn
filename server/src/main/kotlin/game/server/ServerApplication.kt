package game.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import game.server.network.TcpGameServer
import game.shared.protocol.NetworkDefaults

/** Coordinates authoritative server startup, fixed-tick execution, and shutdown. */
class ServerApplication(
    private val mapId: String = DEFAULT_MAP_ID,
    private val mapPath: String = DEFAULT_MAP_PATH,
    private val networkPort: Int = NetworkDefaults.PORT,
    private val worldFactory: (String, String) -> ServerWorld = ::ServerWorld,
    logTicks: Boolean = false,
    private val loop: ServerGameLoop = ServerGameLoop(logTicks = logTicks),
    private val consoleFactory: ((() -> Unit, (String) -> Unit) -> ServerConsole)? = { onStopRequested, consoleLogger ->
        ServerConsole(onStopRequested = onStopRequested, logger = consoleLogger)
    },
    private val logger: (String) -> Unit = ::println,
) {
    private var world: ServerWorld? = null
    private var networkServer: TcpGameServer? = null
    private var ownedHeadlessApplication: HeadlessApplication? = null
    private var stopped: Boolean = true

    fun run(maxTicks: Long? = null) {
        ensureHeadlessApplication()
        stopped = false
        logger("Server starting at ${ServerGameLoop.DEFAULT_TICK_RATE} ticks/sec")
        val serverWorld = worldFactory(mapId, mapPath)
        world = serverWorld
        logger(
            "Loaded gameplay map '${serverWorld.gameMapData.mapId}' " +
                "spawns=${serverWorld.gameMapData.spawnPoints.size} " +
                "collisions=${serverWorld.gameMapData.collisionObjects.size}",
        )
        networkServer = TcpGameServer(
            port = networkPort,
            mapIdProvider = { serverWorld.gameMapData.mapId },
            serverTickProvider = { loop.serverTick },
            initialSnapshotProvider = { playerEntityId ->
                serverWorld.spawnPlayer(playerEntityId)
                serverWorld.buildSnapshot(loop.serverTick)
            },
            inputCommandHandler = { playerEntityId, inputCommand ->
                serverWorld.applyInput(playerEntityId, inputCommand)
                serverWorld.update(loop.fixedTimeStepSeconds)
                serverWorld.buildSnapshot(loop.serverTick)
            },
            disconnectSnapshotProvider = { playerEntityId ->
                serverWorld.despawnPlayer(playerEntityId)
                serverWorld.buildSnapshot(loop.serverTick)
            },
            logger = logger,
        ).also { it.start() }

        Runtime.getRuntime().addShutdownHook(Thread { stop() })
        consoleFactory?.invoke(::stop, logger)?.start()

        try {
            loop.run(maxTicks = maxTicks) { fixedDelta ->
                serverWorld.update(fixedDelta)
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
    }
}
