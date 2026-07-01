package com.chatwoot.android.sdk.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** SDK-level realtime events, mapped from RoomChannel broadcasts. */
internal sealed interface CableEvent {
    data object Connected : CableEvent
    data object Disconnected : CableEvent
    data class MessageCreated(val message: MessageDto) : CableEvent
    data class MessageUpdated(val message: MessageDto) : CableEvent
    data class TypingChanged(val typing: Boolean) : CableEvent
}

private const val PRESENCE_INTERVAL_MS = 30_000L
private const val MAX_BACKOFF_MS = 30_000L

/**
 * Maintains a RoomChannel subscription on `wss://<host>/cable`, including the 30s
 * `update_presence` keepalive and exponential-backoff reconnects. Collect [events];
 * the connection lives as long as the collector.
 */
internal class CableClient(
    private val client: HttpClient,
    baseUrl: String,
    private val pubsubToken: String,
) {
    private val wsUrl = baseUrl
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")
        .trimEnd('/') + "/cable"

    fun events(): Flow<CableEvent> = channelFlow {
        var attempt = 0
        while (isActive) {
            try {
                client.webSocket(urlString = wsUrl) {
                    send(Frame.Text(CableProtocol.subscribeCommand(pubsubToken)))
                    val keepalive = launch {
                        while (true) {
                            delay(PRESENCE_INTERVAL_MS)
                            send(Frame.Text(CableProtocol.presenceCommand(pubsubToken)))
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            when (val parsed = CableProtocol.parseFrame(frame.readText())) {
                                CableFrame.ConfirmSubscription -> {
                                    attempt = 0
                                    send(CableEvent.Connected)
                                }
                                is CableFrame.Event -> mapEvent(parsed)?.let { send(it) }
                                CableFrame.Disconnect -> return@webSocket
                                else -> Unit
                            }
                        }
                    } finally {
                        keepalive.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // fall through to reconnect
            }
            if (!isActive) break
            send(CableEvent.Disconnected)
            attempt++
            delay(min(MAX_BACKOFF_MS, 1_000L * (1L shl min(attempt, 5))))
        }
        awaitClose()
    }

    private fun mapEvent(event: CableFrame.Event): CableEvent? = when (event.name) {
        "message.created" -> decodeMessage(event)?.let { CableEvent.MessageCreated(it) }
        "message.updated" -> decodeMessage(event)?.let { CableEvent.MessageUpdated(it) }
        "conversation.typing_on" -> typingEvent(event, typing = true)
        "conversation.typing_off" -> typingEvent(event, typing = false)
        else -> null
    }

    private fun decodeMessage(event: CableFrame.Event): MessageDto? =
        runCatching { ChatwootJson.decodeFromJsonElement(MessageDto.serializer(), event.data) }
            .getOrNull()

    /** Only surface agent typing — ignore the contact's own echo and private-note typing. */
    private fun typingEvent(event: CableFrame.Event, typing: Boolean): CableEvent? {
        val data = runCatching { event.data.jsonObject }.getOrNull() ?: return null
        val isPrivate = data["is_private"]?.jsonPrimitive?.contentOrNull == "true"
        val userType = data["user"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (isPrivate || userType == "contact") return null
        return CableEvent.TypingChanged(typing)
    }
}
