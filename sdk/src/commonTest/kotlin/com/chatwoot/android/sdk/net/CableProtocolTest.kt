package com.chatwoot.android.sdk.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CableProtocolTest {

    @Test
    fun subscribeCommandNestsIdentifierAsJsonString() {
        val command = Json.parseToJsonElement(CableProtocol.subscribeCommand("tok123")).jsonObject
        assertEquals("subscribe", command["command"]?.jsonPrimitive?.content)

        // ActionCable requires the identifier to be a JSON-encoded *string*, not an object.
        val identifier = Json.parseToJsonElement(
            command["identifier"]!!.jsonPrimitive.content
        ).jsonObject
        assertEquals("RoomChannel", identifier["channel"]?.jsonPrimitive?.content)
        assertEquals("tok123", identifier["pubsub_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun presenceCommandCarriesUpdatePresenceAction() {
        val command = Json.parseToJsonElement(CableProtocol.presenceCommand("tok123")).jsonObject
        assertEquals("message", command["command"]?.jsonPrimitive?.content)
        val data = Json.parseToJsonElement(command["data"]!!.jsonPrimitive.content).jsonObject
        assertEquals("update_presence", data["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesControlFrames() {
        assertEquals(CableFrame.Welcome, CableProtocol.parseFrame("""{"type":"welcome"}"""))
        assertEquals(CableFrame.Ping, CableProtocol.parseFrame("""{"type":"ping","message":1781250000}"""))
        assertEquals(
            CableFrame.ConfirmSubscription,
            CableProtocol.parseFrame("""{"type":"confirm_subscription","identifier":"{\"channel\":\"RoomChannel\"}"}"""),
        )
        assertEquals(CableFrame.Disconnect, CableProtocol.parseFrame("""{"type":"disconnect","reason":"unauthorized"}"""))
    }

    @Test
    fun parsesBroadcastEventFrame() {
        val frame = CableProtocol.parseFrame(
            """
            {
              "identifier": "{\"channel\":\"RoomChannel\",\"pubsub_token\":\"tok\"}",
              "message": {
                "event": "message.created",
                "data": {"id": 42, "content": "hello", "message_type": 1, "created_at": 1781250000}
              }
            }
            """.trimIndent()
        )
        val event = assertIs<CableFrame.Event>(frame)
        assertEquals("message.created", event.name)
        assertEquals("42", event.data.jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun unknownPayloadsDoNotThrow() {
        assertEquals(CableFrame.Unknown, CableProtocol.parseFrame("not json at all"))
        assertEquals(CableFrame.Unknown, CableProtocol.parseFrame("""{"something":"else"}"""))
        assertTrue(CableProtocol.parseFrame("""{"message":{"no_event":true}}""") is CableFrame.Unknown)
    }
}
