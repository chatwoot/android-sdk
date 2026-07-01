package com.chatwoot.android.sdk.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Lenient by design: Chatwoot payloads carry many fields the SDK doesn't need. */
internal val ChatwootJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
internal data class MessageDto(
    val id: Long,
    val content: String? = null,
    // 0 = incoming (contact), 1 = outgoing (agent), 2 = activity, 3 = template
    @SerialName("message_type") val messageType: Int = 0,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("conversation_id") val conversationId: Long? = null,
    val status: String? = null,
    val private: Boolean = false,
    val sender: SenderDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
internal data class AttachmentDto(
    val id: Long? = null,
    // image | audio | video | file | … — anything other than the first three renders as a file.
    // The widget API serialises this Rails enum as an INT (image=0, audio=1, video=2, file=3, …);
    // [FileTypeSerializer] normalises both the int and string forms to the canonical name.
    @SerialName("file_type") @Serializable(with = FileTypeSerializer::class) val fileType: String? = null,
    @SerialName("data_url") val dataUrl: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val extension: String? = null,
)

/**
 * Reads Chatwoot's `file_type` whether it arrives as the Rails enum's integer (image=0, audio=1,
 * video=2, file=3, …) — as the widget API sends it — or as a string name, normalising to the
 * canonical lowercase name so attachment rendering can switch on it.
 */
internal object FileTypeSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("file_type", PrimitiveKind.STRING)

    private val byOrdinal = listOf(
        "image", "audio", "video", "file", "location", "fallback", "share",
        "story_mention", "contact", "ig_reel", "ig_post", "ig_story", "embed",
    )

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return byOrdinal.getOrNull(it) ?: "file" }
        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "")
    }
}

@Serializable
internal data class SenderDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("available_name") val availableName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val thumbnail: String? = null,
    val type: String? = null,
)

@Serializable
internal data class MessagesPayloadDto(
    val payload: List<MessageDto> = emptyList(),
)

@Serializable
internal data class AgentDto(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("availability_status") val availabilityStatus: String? = null,
)

@Serializable
internal data class AgentsPayloadDto(
    val payload: List<AgentDto> = emptyList(),
)

// --- Request bodies (mirrors app/javascript/widget/api/endPoints.js in chatwoot/chatwoot) ---

@Serializable
internal data class OutgoingMessageDto(
    val content: String,
    val timestamp: String,
    // No default — defaults are skipped during encoding and the widget always sends this key.
    @SerialName("referer_url") val refererUrl: String,
)

@Serializable
internal data class SendMessageRequest(
    val message: OutgoingMessageDto,
)

@Serializable
internal data class CreateConversationRequest(
    val message: OutgoingMessageDto,
)

/**
 * `PATCH /contact/set_user` response. Mostly a contact summary the SDK ignores; the load-bearing
 * field is [widgetAuthToken], which the server mints **only** when identifying changes the
 * underlying contact (a merge/swap). When present it supersedes the session's `X-Auth-Token`.
 */
@Serializable
internal data class SetUserResponse(
    @SerialName("widget_auth_token") val widgetAuthToken: String? = null,
)

/**
 * Flat contact body for `PATCH /api/v1/widget/contact[/set_user]`. Null fields are dropped during
 * encoding (`explicitNulls = false`), so each call sends only what the host supplied.
 */
@Serializable
internal data class ContactRequest(
    val identifier: String? = null,
    @SerialName("identifier_hash") val identifierHash: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("custom_attributes") val customAttributes: Map<String, String>? = null,
)
