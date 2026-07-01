package com.chatwoot.android.sdk.data

import com.chatwoot.android.sdk.net.ChatwootJson
import com.chatwoot.android.sdk.net.MessageDto
import com.chatwoot.android.sdk.net.MessagesPayloadDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentTest {

    @Test
    fun parsesAttachmentsArrayFromMessagePayload() {
        val payload = ChatwootJson.decodeFromString(
            MessagesPayloadDto.serializer(),
            """
            {"payload":[{
              "id": 42, "message_type": 0, "created_at": 1781250000, "content": null,
              "attachments": [{
                "id": 7, "file_type": "image",
                "data_url": "https://cdn/x.jpg", "thumb_url": "https://cdn/x-thumb.jpg",
                "file_size": 2048, "width": 800, "height": 600, "extension": "jpg",
                "unexpected_field": true
              }]
            }]}
            """.trimIndent(),
        )
        val att = payload.payload.single().attachments.single()
        assertEquals("image", att.fileType)
        assertEquals("https://cdn/x.jpg", att.dataUrl)
        assertEquals(800, att.width)
    }

    @Test
    fun parsesIntegerFileTypeFromWidgetApi() {
        // The widget API serialises file_type as the Rails enum int (image=0, video=2).
        val payload = ChatwootJson.decodeFromString(
            MessagesPayloadDto.serializer(),
            """
            {"payload":[
              {"id": 1, "message_type": 0, "created_at": 1, "attachments": [
                {"file_type": 0, "data_url": "https://cdn/x.jpg"}]},
              {"id": 2, "message_type": 0, "created_at": 2, "attachments": [
                {"file_type": 2, "data_url": "https://cdn/v.mp4"}]}
            ]}
            """.trimIndent(),
        )
        assertEquals(AttachmentType.Image, payload.payload[0].toChatMessage()!!.attachments.single().type)
        assertEquals(AttachmentType.Video, payload.payload[1].toChatMessage()!!.attachments.single().type)
    }

    @Test
    fun trimsSurroundingWhitespaceAndTrailingBlankLines() {
        val message = MessageDto(id = 5, content = "  hi there\n\n  \n", messageType = 1, createdAt = 1).toChatMessage()
        assertEquals("hi there", assertNotNull(message).content)
    }

    @Test
    fun attachmentOnlyMessageWithBlankContentIsNotDropped() {
        val dto = MessageDto(
            id = 1, content = "", messageType = 0, createdAt = 1,
            attachments = listOf(com.chatwoot.android.sdk.net.AttachmentDto(fileType = "audio", dataUrl = "https://cdn/a.m4a")),
        )
        val message = assertNotNull(dto.toChatMessage())
        assertEquals("", message.content)
        assertEquals(AttachmentType.Audio, message.attachments.single().type)
        assertTrue(message.fromContact)
    }

    @Test
    fun emptyMessageWithNoAttachmentsIsDropped() {
        assertNull(MessageDto(id = 1, content = " ", messageType = 0, createdAt = 1).toChatMessage())
    }

    @Test
    fun fileTypeMapsUnknownToFileAndPrefersDataUrl() {
        val dto = MessageDto(
            id = 2, messageType = 1, createdAt = 1,
            attachments = listOf(
                com.chatwoot.android.sdk.net.AttachmentDto(fileType = "share", dataUrl = "https://cdn/doc.pdf"),
                com.chatwoot.android.sdk.net.AttachmentDto(fileType = "video", thumbUrl = "https://cdn/v-thumb.jpg"),
            ),
        )
        val atts = assertNotNull(dto.toChatMessage()).attachments
        assertEquals(AttachmentType.File, atts[0].type)
        assertEquals("https://cdn/doc.pdf", atts[0].url)
        // No data_url → falls back to thumb_url for the URL.
        assertEquals(AttachmentType.Video, atts[1].type)
        assertEquals("https://cdn/v-thumb.jpg", atts[1].url)
    }

    @Test
    fun optimisticSendReconcilesWithRealMessageById() {
        val tempId = -1L
        val file = PickedFile("voice.m4a", "audio/mp4", byteArrayOf(1, 2, 3))
        var messages = emptyList<ChatMessage>().withUpserted(optimistic(tempId, file))

        assertEquals(1, messages.size)
        assertTrue(messages.single().pending)
        assertEquals(file, messages.single().localPreview)

        messages = messages.withProgress(tempId, 0.5f)
        assertEquals(0.5f, messages.single().uploadProgress)

        val real = ChatMessage(
            id = 99, content = "", fromContact = true, isActivity = false,
            senderName = null, createdAt = 1781250000,
            attachments = listOf(ChatAttachment(AttachmentType.Audio, "https://cdn/a.m4a", null, null, 3, null, null)),
        )
        messages = messages.reconcilingTemp(tempId, real)
        // Temp gone, real present, not pending.
        assertEquals(listOf(99L), messages.map { it.id })
        assertTrue(messages.single().attachments.isNotEmpty())

        // A websocket echo of the same real id must not duplicate.
        messages = messages.withUpserted(real)
        assertEquals(1, messages.size)
    }

    @Test
    fun failedUploadKeepsPlaceholderAndMarksFailed() {
        val tempId = -1L
        val messages = emptyList<ChatMessage>()
            .withUpserted(optimistic(tempId, PickedFile("x.jpg", "image/jpeg", byteArrayOf(0))))
            .withFailed(tempId)
        assertTrue(messages.single().failed)
        assertTrue(!messages.single().pending)
    }
}
