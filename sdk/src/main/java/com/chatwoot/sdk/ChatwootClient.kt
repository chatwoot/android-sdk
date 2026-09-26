package com.chatwoot.sdk

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** One client per SDK app. Call reset before switching customer accounts. */
class ChatwootClient(context: Context, val baseUrl: String, val sdkAppId: String) {
    private val store = SessionStore(context.applicationContext, "$baseUrl|$sdkAppId")
    private val http = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(30)).build()
    private val lock = Mutex()
    private val identityLock = Mutex()
    private var session = store.load()?.let(::JSONObject) ?: JSONObject()
    private var connected = false
    private var generation = 0
    private var socket: WebSocket? = null
    private var socketScope: CoroutineScope? = null
    private var pubsubToken = ""
    var config = JSONObject()
        private set

    var contact = JSONObject()
        private set

    val title
        get() = config.optString("website_name", "Support")

    val color
        get() = config.optString("widget_color", "#2781f6")

    val hideBranding
        get() = config.optBoolean("disable_branding")

    val canReplyAfterResolved
        get() = config.optBoolean("allow_messages_after_resolved", true)

    val replyTime
        get() =
            when (config.optString("reply_time")) {
                "in_a_few_minutes" -> "Typically replies in a few minutes"
                "in_a_few_hours" -> "Typically replies in a few hours"
                "in_a_day" -> "Typically replies in a day"
                else -> ""
            }

    internal suspend fun request(
        path: String,
        method: String = "GET",
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        upload: RequestBody? = null,
    ): JSONObject {
        val version = generation
        val token = session.optString("token")
        val result =
            withContext(Dispatchers.IO) {
                val url =
                    (baseUrl.trimEnd('/') + "/api/v1/widget/" + path)
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("sdk_app_id", sdkAppId)
                query.forEach { (key, value) -> url.addQueryParameter(key, value) }
                val builder = Request.Builder().url(url.build()).header("X-Auth-Token", token)
                val data =
                    upload ?: body?.toString()?.toRequestBody("application/json".toMediaType())
                builder.method(method, data)
                http.newCall(builder.build()).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val json =
                        if (text.isBlank()) JSONObject()
                        else
                            runCatching { JSONObject(text) }
                                .getOrElse {
                                    throw IOException("Server returned HTTP ${response.code}")
                                }
                    if (!response.isSuccessful)
                        throw IOException(
                            json.optString("error", "Request failed (${response.code})")
                        )
                    json
                }
            }
        check(version == generation) { "Customer session changed" }
        return result
    }

    suspend fun connect() =
        lock.withLock {
            if (!connected) {
                val result = request("config", "POST", body = JSONObject())
                config = result.getJSONObject("website_channel_config")
                contact = result.getJSONObject("contact")
                session.put("token", config.getString("auth_token"))
                store.save(session.toString())
                pubsubToken = contact.getString("pubsub_token")
                connected = true
            }
        }

    suspend fun conversations(page: Int = 1): JSONObject {
        connect()
        return request("conversations/list", query = mapOf("page" to page.toString()))
    }

    suspend fun messages(id: Int, before: Int? = null): JSONArray {
        connect()
        val query = mutableMapOf("conversation_id" to id.toString())
        before?.let { query["before"] = it.toString() }
        return request("messages", query = query).getJSONArray("payload")
    }

    suspend fun send(id: Int, text: String) {
        require(text.isNotBlank()) { "Enter a message" }
        connect()
        request(
            "messages",
            "POST",
            mapOf("conversation_id" to id.toString()),
            JSONObject().put("message", JSONObject().put("content", text)),
        )
    }

    suspend fun startConversation(text: String, values: Map<String, String> = emptyMap()): Int {
        connect()
        val result =
            request("conversations", "POST", body = PreChat.payload(preChatFields(), text, values))
        val refreshed = request("config", "POST", body = JSONObject())
        contact = refreshed.getJSONObject("contact")
        return result.getInt("id")
    }

    internal fun preChatFields(): List<JSONObject> {
        if (!config.optBoolean("pre_chat_form_enabled")) return emptyList()
        return config
            .optJSONObject("pre_chat_form_options")
            ?.optJSONArray("pre_chat_fields")
            .objects()
            .filter { field ->
                field.optBoolean("enabled") &&
                    when (field.optString("name")) {
                        "emailAddress" ->
                            contact.optString("email").isBlank() || contact.isNull("email")
                        "phoneNumber" ->
                            contact.optString("phone_number").isBlank() ||
                                contact.isNull("phone_number")
                        "fullName" ->
                            listOf("identifier", "email", "phone_number").all {
                                contact.isNull(it) || contact.optString(it).isBlank()
                            }
                        else -> true
                    }
            }
    }

    suspend fun markRead(id: Int) {
        request(
            "conversations/update_last_seen",
            "POST",
            mapOf("conversation_id" to id.toString()),
            JSONObject(),
        )
    }

    suspend fun submitEmail(messageId: Int, email: String) {
        request(
            "messages/$messageId",
            "PATCH",
            body = JSONObject().put("contact", JSONObject().put("email", email)),
        )
    }

    suspend fun upload(id: Int, bytes: ByteArray, filename: String, mime: String) {
        val name = filename.replace(Regex("[\\r\\n\\\"]"), "")
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "message[attachments][]",
                    name,
                    bytes.toRequestBody(mime.toMediaType()),
                )
                .build()
        request("messages", "POST", mapOf("conversation_id" to id.toString()), upload = body)
    }

    suspend fun registerDeviceToken(token: String, name: String = android.os.Build.MODEL) =
        identityLock.withLock {
            check(session.has("token")) { "Connect before registering notifications" }
            connect()
            val body =
                JSONObject()
                    .put("platform", "android")
                    .put("environment", "production")
                    .put("device_token", token)
                    .put("name", name)
            if (session.has("device_id")) body.put("device_id", session.getInt("device_id"))
            val result = request("sdk_push_devices", "POST", body = body)
            session.put("device_id", result.getInt("id"))
            store.save(session.toString())
        }

    suspend fun identify(
        identifier: String,
        signature: String,
        name: String,
        email: String? = null,
    ) =
        identityLock.withLock {
            require(identifier.isNotBlank() && signature.isNotBlank()) {
                "A server-signed identity is required"
            }
            connect()
            unregister()
            val body =
                JSONObject()
                    .put("identifier", identifier)
                    .put("identifier_hash", signature)
                    .put("name", name)
            email?.let { body.put("email", it) }
            val result = request("contact/set_user", "PATCH", body = body)
            result
                .optString("widget_auth_token")
                .takeIf { it.isNotBlank() }
                ?.let { session.put("token", it) }
            store.save(session.toString())
            pause()
            generation++
            connected = false
            connect()
        }

    private suspend fun unregister() {
        if (session.has("device_id")) {
            request("sdk_push_devices/${session.getInt("device_id")}", "DELETE")
            session.remove("device_id")
            store.save(session.toString())
        }
    }

    suspend fun reset() =
        identityLock.withLock {
            unregister()
            pause()
            generation++
            connected = false
            session = JSONObject()
            store.save(null)
        }

    suspend fun notificationConversation(data: Map<String, String>): Int? {
        if (data["chatwoot_sdk_app_id"] != sdkAppId) return null
        val id = data["chatwoot_conversation_id"]?.toIntOrNull() ?: return null
        connect()
        val result = request("conversations", query = mapOf("conversation_id" to id.toString()))
        return id.takeIf { result.getInt("inbox_id").toString() == data["chatwoot_inbox_id"] }
    }

    internal fun resume(onChange: () -> Unit) {
        if (!connected || socketScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        socketScope = scope
        fun open() {
            val url = baseUrl.trimEnd('/').replaceFirst("http", "ws") + "/cable"
            socket =
                http.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val identifier =
                                JSONObject()
                                    .put("channel", "RoomChannel")
                                    .put("pubsub_token", pubsubToken)
                            webSocket.send(
                                JSONObject()
                                    .put("command", "subscribe")
                                    .put("identifier", identifier.toString())
                                    .toString()
                            )
                            scope.launch { onChange() }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val event =
                                runCatching {
                                        JSONObject(text)
                                            .optJSONObject("message")
                                            ?.optString("event")
                                    }
                                    .getOrNull()
                            if (
                                event in
                                    listOf(
                                        "message.created",
                                        "message.updated",
                                        "conversation.status_changed",
                                    )
                            )
                                scope.launch { onChange() }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            scope.launch {
                                delay(3000)
                                open()
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            scope.launch {
                                delay(3000)
                                open()
                            }
                        }
                    },
                )
        }
        open()
    }

    internal fun pause() {
        socketScope?.cancel()
        socketScope = null
        socket?.cancel()
        socket = null
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
