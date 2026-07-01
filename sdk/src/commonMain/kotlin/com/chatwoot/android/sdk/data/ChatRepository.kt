package com.chatwoot.android.sdk.data

import com.chatwoot.android.sdk.Chatwoot
import com.chatwoot.android.sdk.ChatwootConfig
import com.chatwoot.android.sdk.ChatwootIdentity
import com.chatwoot.android.sdk.net.AttachmentDto
import com.chatwoot.android.sdk.net.CableClient
import com.chatwoot.android.sdk.net.CableEvent
import com.chatwoot.android.sdk.net.ContactRequest
import com.chatwoot.android.sdk.net.MessageDto
import com.chatwoot.android.sdk.net.WidgetApi
import com.chatwoot.android.sdk.net.WidgetSession
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal enum class AttachmentType { Image, Video, Audio, File }

/** A rendered attachment. [url] is the original; [thumbUrl] is a preview when the server has one. */
internal data class ChatAttachment(
    val type: AttachmentType,
    val url: String,
    val thumbUrl: String?,
    val fileName: String?,
    val fileSize: Long?,
    val width: Int?,
    val height: Int?,
)

/** A chat entry as rendered by the UI. */
internal data class ChatMessage(
    val id: Long,
    val content: String,
    val fromContact: Boolean,
    val isActivity: Boolean,
    val senderName: String?,
    val createdAt: Long,
    val attachments: List<ChatAttachment> = emptyList(),
    // Optimistic upload state — only set on the local placeholder while a send is in flight.
    val pending: Boolean = false,
    val failed: Boolean = false,
    val uploadProgress: Float? = null,
    val localPreview: PickedFile? = null,
)

internal data class ChatUiState(
    val loading: Boolean = true,
    val connected: Boolean = false,
    val agentTyping: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
)

/**
 * Owns the session lifecycle: bootstrap (resuming any persisted `cw_conversation` token),
 * history fetch, live RoomChannel events, and sending. The first send of a fresh session
 * goes through conversation creation.
 */
internal class ChatRepository(
    private val config: ChatwootConfig,
    private val client: HttpClient,
    private val api: WidgetApi = WidgetApi(config, client),
    private val tokenStore: TokenStore = TokenStore(),
) {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var session: WidgetSession? = null
    private var hasConversation = false

    // Optimistic placeholders get descending negative ids so they never collide with real ones.
    private var nextTempId = -1L

    suspend fun connect(scope: CoroutineScope) {
        // A different identified user than the persisted session means we must not resume the
        // previous contact's conversation — drop the stored token so bootstrap creates a fresh one.
        val wanted = Chatwoot.identity.value
        if (wanted.identifier != null &&
            wanted.identifier != tokenStore.activeIdentifier(config.websiteToken)
        ) {
            tokenStore.clearSession(config.websiteToken)
        }

        val s = api.fetchSession(tokenStore.conversationToken(config.websiteToken))
        tokenStore.saveConversationToken(config.websiteToken, s.authToken)
        tokenStore.saveActiveIdentifier(config.websiteToken, wanted.identifier)
        session = s

        // May adopt a server-minted token (set_user merge/swap), updating `session` in place — so
        // it runs before the refetch/collectors below, which all read the current `session`.
        runCatching { flushIdentity(wanted) }

        refreshMessages()
        _state.update { it.copy(loading = false) }

        scope.launch {
            CableClient(client, config.normalizedBaseUrl, s.pubsubToken).events().collect { event ->
                onCableEvent(event)
            }
        }
        // Push later attribute changes (the value we just flushed is dropped).
        scope.launch {
            Chatwoot.identity.drop(1).collect { runCatching { flushIdentity(it) } }
        }
    }

    /**
     * Sends the current identity to Chatwoot: `set_user` when identified, plain update otherwise.
     * Reads the active token from [session] (never a captured copy) so a previously-adopted
     * swapped token is honoured.
     */
    private suspend fun flushIdentity(id: ChatwootIdentity) {
        if (id.isEmpty) return
        val authToken = session?.authToken ?: return
        val body = ContactRequest(
            identifier = id.identifier,
            identifierHash = id.identifierHash,
            name = id.name,
            email = id.email,
            phoneNumber = id.phoneNumber,
            avatarUrl = id.avatarUrl,
            customAttributes = id.customAttributes.ifEmpty { null },
        )
        if (id.identifier != null) {
            // set_user mints a fresh session JWT when identifying changes the underlying contact —
            // adopt it so later REST calls follow the contact the server resolved (ADR 0004).
            api.setUser(authToken, body).widgetAuthToken?.let(::adoptAuthToken)
        } else {
            api.updateContact(authToken, body)
        }
    }

    /** Replaces the active+persisted `X-Auth-Token` with a server-minted one. */
    private fun adoptAuthToken(token: String) {
        session = session?.copy(authToken = token)
        tokenStore.saveConversationToken(config.websiteToken, token)
    }

    suspend fun send(content: String) {
        val s = session ?: error("ChatRepository.send called before connect")
        if (hasConversation) {
            upsert(api.sendMessage(s.authToken, content))
        } else {
            api.createConversation(s.authToken, content)
            hasConversation = true
            refreshMessages()
        }
    }

    /**
     * Uploads [file] as an attachment-only message. Shows an optimistic local bubble
     * immediately, then reconciles it with the server's response (the websocket echo of the
     * same id is deduped by [upsert]). On failure the placeholder is marked [ChatMessage.failed].
     */
    suspend fun sendAttachment(file: PickedFile) {
        val s = session ?: error("ChatRepository.sendAttachment called before connect")
        val tempId = nextTempId--
        val onProgress = { progress: Float ->
            _state.update { it.copy(messages = it.messages.withProgress(tempId, progress)) }
        }
        _state.update { it.copy(messages = it.messages.withUpserted(optimistic(tempId, file))) }
        try {
            // POST /messages lazily creates the conversation when none exists yet and returns a
            // parseable Message — reconcile the optimistic bubble directly. Attachments never go
            // through POST /conversations, so this single path covers attachment-first too.
            val real = api.sendAttachment(s.authToken, file, onProgress).toChatMessage()
            hasConversation = true
            _state.update { it.copy(messages = it.messages.reconcilingTemp(tempId, real)) }
        } catch (e: Throwable) {
            _state.update { it.copy(messages = it.messages.withFailed(tempId)) }
            throw e
        }
    }

    private suspend fun onCableEvent(event: CableEvent) {
        when (event) {
            is CableEvent.MessageCreated -> upsert(event.message)
            is CableEvent.MessageUpdated -> upsert(event.message)
            is CableEvent.TypingChanged -> _state.update { it.copy(agentTyping = event.typing) }
            CableEvent.Connected -> {
                _state.update { it.copy(connected = true) }
                // Catch up on anything broadcast while we were offline.
                runCatching { refreshMessages() }
            }
            CableEvent.Disconnected ->
                _state.update { it.copy(connected = false, agentTyping = false) }
        }
    }

    private suspend fun refreshMessages() {
        val authToken = session?.authToken ?: return
        val history = api.getMessages(authToken).mapNotNull { it.toChatMessage() }
        hasConversation = hasConversation || history.isNotEmpty()
        _state.update { state ->
            state.copy(messages = (history + state.messages.filter { m -> history.none { it.id == m.id } }).sorted())
        }
    }

    private fun upsert(dto: MessageDto) {
        val message = dto.toChatMessage() ?: return
        _state.update { state -> state.copy(messages = state.messages.withUpserted(message)) }
    }
}

// --- Pure message-list reducers (kept top-level so they're unit-testable without HTTP) ---

private fun List<ChatMessage>.sorted() = sortedWith(compareBy({ it.createdAt }, { it.id }))

/** Replaces any same-id entry with [message], keeping the list sorted. */
internal fun List<ChatMessage>.withUpserted(message: ChatMessage): List<ChatMessage> =
    (filter { it.id != message.id } + message).sorted()

/** Removes the optimistic [tempId] placeholder and upserts the reconciled [real] message. */
internal fun List<ChatMessage>.reconcilingTemp(tempId: Long, real: ChatMessage?): List<ChatMessage> {
    val withoutTemp = filter { it.id != tempId }
    return if (real == null) withoutTemp.sorted() else withoutTemp.withUpserted(real)
}

internal fun List<ChatMessage>.withProgress(tempId: Long, progress: Float): List<ChatMessage> =
    map { if (it.id == tempId) it.copy(uploadProgress = progress.coerceIn(0f, 1f)) else it }

internal fun List<ChatMessage>.withFailed(tempId: Long): List<ChatMessage> =
    map { if (it.id == tempId) it.copy(failed = true, pending = false) else it }

@OptIn(ExperimentalTime::class)
internal fun optimistic(tempId: Long, file: PickedFile): ChatMessage = ChatMessage(
    id = tempId,
    content = "",
    fromContact = true,
    isActivity = false,
    senderName = null,
    createdAt = Clock.System.now().epochSeconds,
    attachments = emptyList(),
    pending = true,
    uploadProgress = 0f,
    localPreview = file,
)

internal fun MessageDto.toChatMessage(): ChatMessage? {
    if (private) return null
    // Trim surrounding whitespace and trailing blank lines for display (sent and received alike).
    val text = content?.trim()?.takeIf { it.isNotEmpty() }
    val isActivity = messageType == 2
    val mapped = attachments.mapNotNull { it.toChatAttachment() }
    // Drop empty noise, but never an attachment- or activity-carrying message.
    if (text == null && mapped.isEmpty() && !isActivity) return null
    return ChatMessage(
        id = id,
        content = text.orEmpty(),
        fromContact = messageType == 0,
        isActivity = isActivity,
        senderName = sender?.availableName ?: sender?.name,
        createdAt = createdAt,
        attachments = mapped,
    )
}

private fun AttachmentDto.toChatAttachment(): ChatAttachment? {
    val url = dataUrl ?: thumbUrl ?: return null
    val type = when (fileType?.lowercase()) {
        "image" -> AttachmentType.Image
        "video" -> AttachmentType.Video
        "audio" -> AttachmentType.Audio
        else -> AttachmentType.File
    }
    return ChatAttachment(
        type = type,
        url = url,
        thumbUrl = thumbUrl,
        fileName = extension?.let { "file.$it" },
        fileSize = fileSize,
        width = width,
        height = height,
    )
}
