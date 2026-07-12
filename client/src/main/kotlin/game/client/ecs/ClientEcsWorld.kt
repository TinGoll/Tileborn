package game.client.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Disposable
import game.client.ecs.system.ClientInitializationSystem
import game.client.ecs.system.InputSystem
import game.client.ecs.system.ClientPredictionSystem
import game.client.ecs.system.ServerReconciliationSystem
import game.client.ecs.system.SnapshotInterpolationSystem
import game.client.ecs.system.PhysicsInterpolationSystem
import game.client.input.GameInputSource
import game.client.network.PredictedInputBuffer
import game.client.input.KeyboardInputSource
import game.shared.ecs.system.PhysicsSimulationSystem
import game.shared.physics.PhysicsWorldFactory

/** Owns the client-side ECS engine and its explicitly ordered systems. */
class ClientEcsWorld(
    inputSource: GameInputSource = KeyboardInputSource(),
) : Disposable {
    val physicsWorld: World = PhysicsWorldFactory.create()
    val predictedInputBuffer = PredictedInputBuffer()
    val clientPredictionSystem = ClientPredictionSystem(predictedInputBuffer)
    val serverReconciliationSystem = ServerReconciliationSystem(predictedInputBuffer)
    private val physicsSimulationSystem = PhysicsSimulationSystem(physicsWorld)
    private val physicsInterpolationSystem = PhysicsInterpolationSystem(physicsSimulationSystem)
    val snapshotInterpolationSystem = SnapshotInterpolationSystem()

    val engine: Engine = Engine().apply {
        addSystem(ClientInitializationSystem())
        addSystem(InputSystem(inputSource))
        addSystem(clientPredictionSystem)
        addSystem(snapshotInterpolationSystem)
        addSystem(serverReconciliationSystem)
        addSystem(physicsSimulationSystem)
        addSystem(physicsInterpolationSystem)
    }

    override fun dispose() {
        engine.removeAllEntities()
        engine.removeSystem(physicsSimulationSystem)
        engine.removeSystem(physicsInterpolationSystem)
        engine.removeSystem(snapshotInterpolationSystem)
        engine.removeSystem(clientPredictionSystem)
        engine.removeSystem(serverReconciliationSystem)
        predictedInputBuffer.clear()
        physicsSimulationSystem.dispose()
        physicsWorld.dispose()
    }
}
