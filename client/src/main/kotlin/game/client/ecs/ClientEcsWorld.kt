package game.client.ecs

import com.badlogic.ashley.core.Engine
import game.client.ecs.system.ClientMovementSystem
import game.client.ecs.system.ClientInitializationSystem
import game.client.ecs.system.InputSystem
import game.client.input.GameInputSource
import game.client.input.KeyboardInputSource

/** Owns the client-side ECS engine and its explicitly ordered systems. */
class ClientEcsWorld(
    inputSource: GameInputSource = KeyboardInputSource(),
) {
    val engine: Engine = Engine().apply {
        addSystem(ClientInitializationSystem())
        addSystem(InputSystem(inputSource))
        addSystem(ClientMovementSystem())
    }
}
