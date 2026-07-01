package com.chatwoot.android.sdk.net

import com.chatwoot.android.sdk.ChatwootConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactApiTest {

    private val config = ChatwootConfig("https://app.chatwoot.com", "wt-1")

    /** Captures requests and replies with [responseJson] (sent as application/json). */
    private fun apiCapturing(
        captured: MutableList<RecordedRequest>,
        responseJson: String = "{}",
    ): WidgetApi {
        val engine = MockEngine { request ->
            captured += RecordedRequest(
                method = request.method,
                fullUrl = request.url.toString(),
                authToken = request.headers["X-Auth-Token"],
                body = request.body.asText(),
            )
            respond(responseJson, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(ChatwootJson) } }
        return WidgetApi(config, client)
    }

    @Test
    fun setUserHitsSetUserEndpointWithIdentityBody() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        val api = apiCapturing(requests)

        api.setUser(
            "jwt-token",
            ContactRequest(identifier = "u-1", identifierHash = "h", name = "Ada"),
        )

        val req = requests.single()
        assertEquals(HttpMethod.Patch, req.method)
        assertTrue(req.fullUrl.startsWith("https://app.chatwoot.com/api/v1/widget/contact/set_user"), req.fullUrl)
        assertTrue("website_token=wt-1" in req.fullUrl, req.fullUrl)
        assertEquals("jwt-token", req.authToken)
        assertTrue("\"identifier\":\"u-1\"" in req.body, req.body)
        assertTrue("\"identifier_hash\":\"h\"" in req.body, req.body)
    }

    @Test
    fun setUserParsesWidgetAuthTokenWhenServerSwapsContact() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        // The server mints a fresh session JWT when identifying changes the underlying contact.
        val api = apiCapturing(
            requests,
            responseJson = """{"id":42,"has_email":true,"widget_auth_token":"jwt-swapped"}""",
        )

        val response = api.setUser("jwt-old", ContactRequest(identifier = "u-1"))

        assertEquals("jwt-swapped", response.widgetAuthToken)
    }

    @Test
    fun setUserReturnsNullTokenWhenServerOmitsIt() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        // Inboxes without identity validation return only the contact summary, no token.
        val api = apiCapturing(
            requests,
            responseJson = """{"id":42,"has_email":true,"has_name":true,"has_phone_number":false}""",
        )

        val response = api.setUser("jwt-old", ContactRequest(identifier = "u-1"))

        assertNull(response.widgetAuthToken)
    }

    @Test
    fun updateContactHitsPlainContactEndpoint() = runTest {
        val requests = mutableListOf<RecordedRequest>()
        val api = apiCapturing(requests)

        api.updateContact("jwt-token", ContactRequest(email = "ada@example.com"))

        val req = requests.single()
        assertEquals(HttpMethod.Patch, req.method)
        assertTrue(req.fullUrl.startsWith("https://app.chatwoot.com/api/v1/widget/contact?"), req.fullUrl)
        assertTrue("/set_user" !in req.fullUrl, req.fullUrl)
        assertTrue("\"email\":\"ada@example.com\"" in req.body, req.body)
    }

    private data class RecordedRequest(
        val method: HttpMethod,
        val fullUrl: String,
        val authToken: String?,
        val body: String,
    )
}

private fun io.ktor.http.content.OutgoingContent.asText(): String = when (this) {
    is io.ktor.http.content.TextContent -> text
    is io.ktor.http.content.ByteArrayContent -> bytes().decodeToString()
    else -> ""
}
