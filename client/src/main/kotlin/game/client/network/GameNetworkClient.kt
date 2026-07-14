package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.InputCommand
import game.shared.protocol.AttackCommand
import game.shared.protocol.InteractCommand
import game.shared.protocol.GameEvent
import game.shared.protocol.ServerMessage
import game.shared.protocol.WorldSnapshot

/** Client-side network boundary used by screens without exposing socket details. */
interface GameNetworkClient : AutoCloseable {
    val connectionState: ConnectionState
    val lastServerMessage: ServerMessage?
    val localPlayerEntityId: Int?
    val pingMillis: Long?

    fun connect()

    /** Releases an unreliable background connection while retaining resumable session state. */
    fun onApplicationPaused() = Unit

    /** Requests a reconnect after an application returns to the foreground. */
    fun onApplicationResumed() = Unit

    fun sendInput(command: InputCommand)

    fun sendAttack(command: AttackCommand)

    fun sendInteract(command: InteractCommand)

    /** Returns received snapshots in transport order so acknowledgements cannot be overwritten by later packets. */
    fun drainWorldSnapshots(): List<WorldSnapshot> = listOfNotNull(lastServerMessage as? WorldSnapshot)

    fun drainGameEvents(): List<GameEvent> = emptyList()
}
