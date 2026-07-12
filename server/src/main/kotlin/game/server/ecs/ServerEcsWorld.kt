package game.server.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.system.ServerInitializationSystem
import game.shared.ecs.system.MovementSystem
import game.shared.ecs.system.PhysicsSimulationSystem
import game.shared.physics.PhysicsWorldFactory

/** Owns the authoritative ECS engine and its explicitly ordered systems. */
class ServerEcsWorld : Disposable {
    /** The server-owned authoritative Box2D world. */
    val physicsWorld = PhysicsWorldFactory.create()
    private val physicsSimulationSystem = PhysicsSimulationSystem(physicsWorld)

    val engine: Engine = Engine().apply {
        addSystem(ServerInitializationSystem())
        addSystem(MovementSystem())
        addSystem(physicsSimulationSystem)
    }

    override fun dispose() {
        engine.removeAllEntities()
        val systems = mutableListOf<EntitySystem>()
        for (system in engine.systems) {
            systems += system
        }
        systems.forEach { system ->
            if (system is Disposable) system.dispose()
            engine.removeSystem(system)
        }
        physicsWorld.dispose()
    }
}
