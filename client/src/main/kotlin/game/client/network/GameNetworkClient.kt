package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.ServerMessage

/** Client-side network boundary used by screens without exposing socket details. */
interface GameNetworkClient : AutoCloseable {
    val connectionState: ConnectionState
    val lastServerMessage: ServerMessage?

    fun connect()
}
