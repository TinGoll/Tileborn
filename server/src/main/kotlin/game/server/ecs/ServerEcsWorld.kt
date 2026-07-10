package game.server.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.utils.Disposable
import game.server.ecs.system.ServerInitializationSystem

/** Owns the authoritative ECS engine and its explicitly ordered systems. */
class ServerEcsWorld : Disposable {
    val engine: Engine = Engine().apply {
        addSystem(ServerInitializationSystem())
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
    }
}
