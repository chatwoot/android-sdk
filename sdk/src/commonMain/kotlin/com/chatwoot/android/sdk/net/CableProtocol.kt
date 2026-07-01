package com.chatwoot.android.sdk.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One inbound frame on the `/cable` socket, in ActionCable's wire format. */
internal sealed interface CableFrame {
    data object Welcome : CableFrame
    data object Ping : CableFrame
    data object ConfirmSubscription : CableFrame
    data object Disconnect : CableFrame

    /** A broadcast Chatwoot event, e.g. `message.created`. */
    data class Event(val name: String, val data: JsonElement) : CableFrame

    data object Unknown : CableFrame
}

/**
 * Rails ActionCable wire protocol for the Chatwoot contact RoomChannel.
 * Contact subscriptions identify with the pubsub token only (no account_id/user_id).
 */
internal object CableProtocol {

    /** The subscription identifier — itself a JSON string nested inside command frames. */
    fun identifier(pubsubToken: String): String =
        ChatwootJson.encodeToString(
            buildJsonObject {
                put("channel", "RoomChannel")
                put("pubsub_token", pubsubToken)
            }
        )

    fun subscribeCommand(pubsubToken: String): String =
        ChatwootJson.encodeToString(
            buildJsonObject {
                put("command", "subscribe")
                put("identifier", identifier(pubsubToken))
            }
        )

    /** Keepalive; the widget sends this every 30 seconds to stay "online". */
    fun presenceCommand(pubsubToken: String): String =
        ChatwootJson.encodeToString(
            buildJsonObject {
                put("command", "message")
                put("identifier", identifier(pubsubToken))
                put("data", """{"action":"update_presence"}""")
            }
        )

    fun parseFrame(text: String): CableFrame {
        val obj = runCatching { ChatwootJson.parseToJsonElement(text).jsonObject }
            .getOrElse { return CableFrame.Unknown }

        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "welcome" -> return CableFrame.Welcome
            "ping" -> return CableFrame.Ping
            "confirm_subscription" -> return CableFrame.ConfirmSubscription
            "disconnect" -> return CableFrame.Disconnect
        }

        val message = obj["message"] as? kotlinx.serialization.json.JsonObject ?: return CableFrame.Unknown
        val event = message["event"]?.jsonPrimitive?.contentOrNull ?: return CableFrame.Unknown
        return CableFrame.Event(name = event, data = message["data"] ?: JsonNull)
    }
}
