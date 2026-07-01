package game.server.ecs.system

import com.badlogic.ashley.core.EntitySystem

/** Reserved first server system; behavior will be added in later iterations. */
class ServerInitializationSystem : EntitySystem(PRIORITY)

private const val PRIORITY = 0
