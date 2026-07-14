package game.client.network

import game.client.debug.ConnectionState
import game.shared.protocol.InputCommand
import game.shared.protocol.AttackCommand
import game.shared.protocol.InteractCommand
import game.shared.protocol.ServerMessage

/** Test/local client that performs no network activity. */
object NoopGameNetworkClient : GameNetworkClient {
    override val connectionState: ConnectionState = ConnectionState.LOCAL
    override val lastServerMessage: ServerMessage? = null
    override val localPlayerEntityId: Int? = null
    override val pingMillis: Long? = null

    override fun connect() = Unit

    override fun sendInput(command: InputCommand) = Unit

    override fun sendAttack(command: AttackCommand) = Unit

    override fun sendInteract(command: InteractCommand) = Unit

    override fun close() = Unit
}
