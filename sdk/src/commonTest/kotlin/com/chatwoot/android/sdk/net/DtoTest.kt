package com.chatwoot.android.sdk.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DtoTest {

    @Test
    fun decodesMessagePayloadLeniently() {
        val payload = ChatwootJson.decodeFromString(
            MessagesPayloadDto.serializer(),
            """
            {
              "payload": [
                {
                  "id": 1,
                  "content": "Hi there",
                  "message_type": 1,
                  "content_type": "text",
                  "created_at": 1781250000,
                  "conversation_id": 7,
                  "status": "sent",
                  "sender": {"id": 3, "name": "Pranav", "type": "user", "some_new_field": {"nested": true}},
                  "unknown_future_field": [1, 2, 3]
                },
                {"id": 2, "message_type": 2, "content": "Conversation was resolved", "created_at": 1781250100}
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, payload.payload.size)
        val first = payload.payload.first()
        assertEquals(1L, first.id)
        assertEquals("Hi there", first.content)
        assertEquals(1, first.messageType)
        assertEquals("Pranav", first.sender?.name)
        assertFalse(first.private)
        assertEquals(2, payload.payload[1].messageType)
    }

    @Test
    fun encodesSendMessageRequestWithSnakeCaseKeys() {
        val body = ChatwootJson.encodeToString(
            SendMessageRequest.serializer(),
            SendMessageRequest(OutgoingMessageDto(content = "hello", timestamp = "2026-06-12T00:00:00Z", refererUrl = "")),
        )
        assertTrue("\"referer_url\"" in body, body)
        assertTrue("\"content\":\"hello\"" in body, body)
    }

    @Test
    fun encodesContactRequestWithSnakeCaseKeysAndOmitsNulls() {
        val body = ChatwootJson.encodeToString(
            ContactRequest.serializer(),
            ContactRequest(
                identifier = "u-123",
                identifierHash = "deadbeef",
                name = "Ada",
                email = "ada@example.com",
                customAttributes = mapOf("plan" to "pro"),
            ),
        )
        assertTrue("\"identifier\":\"u-123\"" in body, body)
        assertTrue("\"identifier_hash\":\"deadbeef\"" in body, body)
        assertTrue("\"custom_attributes\":{\"plan\":\"pro\"}" in body, body)
        // phone_number / avatar_url were null — dropped during encoding.
        assertFalse("phone_number" in body, body)
        assertFalse("avatar_url" in body, body)
    }
}
