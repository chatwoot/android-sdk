package com.chatwoot.android.sdk.net

/**
 * A widget session as bootstrapped by `GET /widget?website_token=…`.
 *
 * @property authToken JWT identifying the contact session; sent as `X-Auth-Token` on REST calls
 *   and persisted as the `cw_conversation` token so the contact survives restarts.
 * @property pubsubToken The contact's ActionCable RoomChannel subscription token.
 */
internal data class WidgetSession(
    val authToken: String,
    val pubsubToken: String,
)

/**
 * The widget bootstrap endpoint returns HTML (there is no JSON variant); the session tokens are
 * embedded as `window.authToken = '…'` and `window.chatwootPubsubToken = '…'` script globals.
 */
internal object WidgetPageParser {
    private val authToken = Regex("""window\.authToken\s*=\s*['"]([^'"]+)['"]""")
    private val pubsubToken = Regex("""window\.chatwootPubsubToken\s*=\s*['"]([^'"]+)['"]""")

    fun parse(html: String): WidgetSession? {
        val auth = authToken.find(html)?.groupValues?.get(1) ?: return null
        val pubsub = pubsubToken.find(html)?.groupValues?.get(1) ?: return null
        return WidgetSession(authToken = auth, pubsubToken = pubsub)
    }
}
