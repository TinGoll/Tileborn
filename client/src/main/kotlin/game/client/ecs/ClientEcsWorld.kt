package game.client.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Disposable
import game.client.ecs.system.ClientInitializationSystem
import game.client.ecs.system.InputSystem
import game.client.input.GameInputSource
import game.client.input.KeyboardInputSource
import game.shared.ecs.system.PhysicsSimulationSystem
import game.shared.physics.PhysicsWorldFactory

/** Owns the client-side ECS engine and its explicitly ordered systems. */
class ClientEcsWorld(
    inputSource: GameInputSource = KeyboardInputSource(),
) : Disposable {
    val physicsWorld: World = PhysicsWorldFactory.create()
    private val physicsSimulationSystem = PhysicsSimulationSystem(physicsWorld)

    val engine: Engine = Engine().apply {
        addSystem(ClientInitializationSystem())
        addSystem(InputSystem(inputSource))
        addSystem(physicsSimulationSystem)
    }

    override fun dispose() {
        engine.removeAllEntities()
        engine.removeSystem(physicsSimulationSystem)
        physicsSimulationSystem.dispose()
        physicsWorld.dispose()
    }
}
