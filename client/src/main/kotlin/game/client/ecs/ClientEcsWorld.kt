package game.client.ecs

import com.badlogic.ashley.core.Engine
import game.client.ecs.system.ClientInitializationSystem

/** Owns the client-side ECS engine and its explicitly ordered systems. */
class ClientEcsWorld {
    val engine: Engine = Engine().apply {
        addSystem(ClientInitializationSystem())
    }
}
