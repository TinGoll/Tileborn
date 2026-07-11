package game.shared.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

object ProtocolCodec {
    private val gson: Gson = GsonBuilder().create()

    fun encodeClient(message: ClientMessage): String = when (message) {
        is JoinRequest -> gson.toJson(message)
        is InputCommand -> gson.toJson(message)
        is PingRequest -> gson.toJson(message)
    }

    fun decodeClient(payload: String): ClientMessage {
        return when (messageType(payload)) {
            MessageType.JOIN_REQUEST -> gson.fromJson(payload, JoinRequest::class.java)
            MessageType.INPUT_COMMAND -> gson.fromJson(payload, InputCommand::class.java)
            MessageType.PING_REQUEST -> gson.fromJson(payload, PingRequest::class.java)
            else -> throw JsonParseException("Message type is not a client message.")
        }
    }

    fun encodeServer(message: ServerMessage): String = when (message) {
        is JoinAccepted -> gson.toJson(message)
        is JoinRejected -> gson.toJson(message)
        is WorldSnapshot -> gson.toJson(message)
        is PongResponse -> gson.toJson(message)
    }

    fun decodeServer(payload: String): ServerMessage {
        return when (messageType(payload)) {
            MessageType.JOIN_ACCEPTED -> gson.fromJson(payload, JoinAccepted::class.java)
            MessageType.JOIN_REJECTED -> gson.fromJson(payload, JoinRejected::class.java)
            MessageType.WORLD_SNAPSHOT -> gson.fromJson(payload, WorldSnapshot::class.java)
            MessageType.PONG_RESPONSE -> gson.fromJson(payload, PongResponse::class.java)
            else -> throw JsonParseException("Message type is not a server message.")
        }
    }

    private fun messageType(payload: String): MessageType {
        val jsonObject = JsonParser.parseString(payload) as? JsonObject
            ?: throw JsonParseException("Protocol message must be a JSON object.")
        val rawType = jsonObject["type"]?.asString
            ?: throw JsonParseException("Protocol message is missing type.")
        return try {
            MessageType.valueOf(rawType)
        } catch (exception: IllegalArgumentException) {
            throw JsonParseException("Unsupported protocol message type: $rawType", exception)
        }
    }
}
