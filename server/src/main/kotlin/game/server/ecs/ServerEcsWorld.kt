package game.server.ecs

import com.badlogic.ashley.core.Engine
import game.server.ecs.system.ServerInitializationSystem

/** Owns the authoritative ECS engine and its explicitly ordered systems. */
class ServerEcsWorld {
    val engine: Engine = Engine().apply {
        addSystem(ServerInitializationSystem())
    }
}
