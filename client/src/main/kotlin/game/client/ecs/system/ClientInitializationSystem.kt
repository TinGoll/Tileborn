package game.client.ecs.system

import com.badlogic.ashley.core.EntitySystem

/** Reserved first client system; behavior will be added in later iterations. */
class ClientInitializationSystem : EntitySystem(PRIORITY)

private const val PRIORITY = 0
