package com.chatwoot.android.sdk.data

import com.chatwoot.android.sdk.Chatwoot
import com.chatwoot.android.sdk.ChatwootConfig
import com.chatwoot.android.sdk.net.ChatwootJson
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives [ChatRepository] against a [MockEngine] backend. The websocket plugin is intentionally
 * not installed, so [com.chatwoot.android.sdk.net.CableClient] fails fast and reconnect-loops
 * harmlessly inside [backgroundScope] (cancelled when the test ends).
 */
class ChatRepositoryTest {

    private val config = ChatwootConfig("https://app.chatwoot.com", "wt-test")

    private val bootstrapHtml =
        "<script>window.authToken = 'jwt-initial'; window.chatwootPubsubToken = 'pub-1';</script>"

    private data class Recorded(val method: HttpMethod, val path: String, val authToken: String?)

    @BeforeTest
    fun resetIdentity() {
        // Identity is global singleton state — start each test from anonymous.
        Chatwoot.setUser()
    }

    @AfterTest
    fun clearIdentity() {
        Chatwoot.setUser()
    }

    private fun repo(
        recorded: MutableList<Recorded>,
        store: TokenStore,
        handler: (path: String, method: HttpMethod) -> Pair<String, ContentType> = { _, _ -> "{}" to ContentType.Application.Json },
    ): ChatRepository {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            recorded += Recorded(request.method, path, request.headers["X-Auth-Token"])
            val (body, type) = when {
                path.endsWith("/widget") -> bootstrapHtml to ContentType.Text.Html
                else -> handler(path, request.method)
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, type.toString()))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(ChatwootJson) } }
        return ChatRepository(config, client, tokenStore = store)
    }

    @Test
    fun attachmentFirstSessionPostsToMessagesNotConversations() = runTest {
        val recorded = mutableListOf<Recorded>()
        val attachmentResponse = """
            {"id":700,"content":null,"message_type":0,"created_at":1781606671,"conversation_id":5,
             "attachments":[{"id":78,"file_type":"image","data_url":"https://x/probe.png",
             "thumb_url":"https://x/thumb.png","file_size":67,"extension":"png"}]}
        """.trimIndent()
        val repo = repo(recorded, TokenStore(MapSettings())) { path, method ->
            when {
                path.endsWith("/messages") && method == HttpMethod.Post ->
                    attachmentResponse to ContentType.Application.Json
                path.endsWith("/messages") -> """{"payload":[]}""" to ContentType.Application.Json
                else -> "{}" to ContentType.Application.Json
            }
        }
        repo.connect(backgroundScope)

        repo.sendAttachment(PickedFile("probe.png", "image/png", byteArrayOf(1, 2, 3)))

        // Requirement: attachment-first must go through POST /messages, never POST /conversations.
        assertTrue(
            recorded.any { it.method == HttpMethod.Post && it.path.endsWith("/messages") },
            "expected a POST /messages; got $recorded",
        )
        assertFalse(
            recorded.any { it.path.endsWith("/conversations") },
            "must not POST /conversations for an attachment; got $recorded",
        )
        // Optimistic bubble reconciled directly from the parseable response — no lingering placeholder.
        val messages = repo.state.value.messages
        assertEquals(1, messages.size, "expected one reconciled message; got $messages")
        assertTrue(messages.single().attachments.isNotEmpty())
        assertFalse(messages.single().pending)
    }

    @Test
    fun setUserAdoptsAndPersistsReturnedWidgetAuthToken() = runTest {
        Chatwoot.setUser(identifier = "user-x", name = "X")
        val recorded = mutableListOf<Recorded>()
        val store = TokenStore(MapSettings())
        val repo = repo(recorded, store) { path, method ->
            when {
                path.endsWith("/contact/set_user") ->
                    """{"id":42,"widget_auth_token":"jwt-swapped"}""" to ContentType.Application.Json
                path.endsWith("/messages") -> """{"payload":[]}""" to ContentType.Application.Json
                else -> "{}" to ContentType.Application.Json
            }
        }

        repo.connect(backgroundScope)

        // The swapped token replaces the persisted cw_conversation JWT.
        assertEquals("jwt-swapped", store.conversationToken("wt-test"))
        // And the history refetch that follows set_user authenticates with the new token.
        val messagesAfterSetUser = recorded
            .dropWhile { !(it.path.endsWith("/contact/set_user")) }
            .firstOrNull { it.path.endsWith("/messages") && it.method == HttpMethod.Get }
        assertEquals("jwt-swapped", messagesAfterSetUser?.authToken, "got $recorded")
    }
}
