package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.InputCommand
import game.shared.protocol.ServerMessage
import game.shared.protocol.WorldSnapshot

/** Client-side network boundary used by screens without exposing socket details. */
interface GameNetworkClient : AutoCloseable {
    val connectionState: ConnectionState
    val lastServerMessage: ServerMessage?
    val localPlayerEntityId: Int?
    val pingMillis: Long?

    fun connect()

    fun sendInput(command: InputCommand)

    /** Returns received snapshots in transport order so acknowledgements cannot be overwritten by later packets. */
    fun drainWorldSnapshots(): List<WorldSnapshot> = listOfNotNull(lastServerMessage as? WorldSnapshot)
}
