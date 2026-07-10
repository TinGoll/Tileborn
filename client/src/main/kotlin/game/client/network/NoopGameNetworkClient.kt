package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.ServerMessage

/** Test/local client that performs no network activity. */
object NoopGameNetworkClient : GameNetworkClient {
    override val connectionState: ConnectionState = ConnectionState.LOCAL
    override val lastServerMessage: ServerMessage? = null

    override fun connect() = Unit

    override fun close() = Unit
}
