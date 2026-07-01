package com.chatwoot.android.sdk

import com.chatwoot.android.sdk.data.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Connection settings for a Chatwoot website inbox.
 *
 * @property baseUrl Chatwoot installation URL, e.g. `https://app.chatwoot.com`.
 * @property websiteToken The website inbox token from the Chatwoot dashboard.
 */
public data class ChatwootConfig(
    val baseUrl: String,
    val websiteToken: String,
) {
    init {
        require(baseUrl.startsWith("http")) { "baseUrl must be an http(s) URL, got '$baseUrl'" }
        require(websiteToken.isNotBlank()) { "websiteToken must not be blank" }
    }

    internal val normalizedBaseUrl: String = baseUrl.trimEnd('/')
}

/**
 * The visitor whose conversations Chatwoot should attribute to a known person.
 *
 * @property identifier A stable, host-defined id (e.g. your user id). When set, the SDK
 *   associates the contact via `set_user`; changing it starts a fresh contact session.
 * @property identifierHash HMAC-SHA256 of [identifier] keyed with the inbox's identity-validation
 *   secret, computed **server-side** by the host's backend. Required only when the inbox enforces
 *   identity validation; the SDK never holds the secret.
 * @property customAttributes Inbox-defined custom attributes; numbers/dates are passed as strings.
 */
internal data class ChatwootIdentity(
    val identifier: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val customAttributes: Map<String, String> = emptyMap(),
    val identifierHash: String? = null,
) {
    val isEmpty: Boolean
        get() = identifier == null && name == null && email == null &&
            phoneNumber == null && avatarUrl == null && customAttributes.isEmpty()
}

/**
 * SDK entry point. Either call [configure] once (e.g. in `Application.onCreate` /
 * `application(_:didFinishLaunchingWithOptions:)`) or declare the platform fallback:
 *
 * - Android: `<meta-data>` entries `com.chatwoot.android.BASE_URL` and
 *   `com.chatwoot.android.WEBSITE_TOKEN` in `AndroidManifest.xml`.
 * - iOS: `ChatwootBaseUrl` and `ChatwootWebsiteToken` keys in `Info.plist`.
 */
public object Chatwoot {
    private var explicit: ChatwootConfig? = null

    public fun configure(baseUrl: String, websiteToken: String) {
        explicit = ChatwootConfig(baseUrl, websiteToken)
    }

    private val _identity = MutableStateFlow(ChatwootIdentity())

    /** The current visitor identity; observed by the repository to push updates to the server. */
    internal val identity: StateFlow<ChatwootIdentity> get() = _identity

    /**
     * Identifies the current visitor so agents see a known contact instead of an anonymous one.
     * Call any time (e.g. after the host app's own login), before or while [ChatPage] is shown.
     *
     * Pass an [identifier] to recognise the same person across reinstalls/devices; supplying a
     * different [identifier] than the active session starts a **fresh** contact + conversation on
     * the next [ChatPage] open (no cross-user leakage). [identifierHash] is only needed when the
     * inbox enforces identity validation and must be computed server-side. Unverified attribute-only
     * updates (no [identifier]) are also supported.
     */
    public fun setUser(
        identifier: String? = null,
        name: String? = null,
        email: String? = null,
        phoneNumber: String? = null,
        avatarUrl: String? = null,
        customAttributes: Map<String, String> = emptyMap(),
        identifierHash: String? = null,
    ) {
        _identity.value = ChatwootIdentity(
            identifier = identifier,
            name = name,
            email = email,
            phoneNumber = phoneNumber,
            avatarUrl = avatarUrl,
            customAttributes = customAttributes,
            identifierHash = identifierHash,
        )
    }

    /** Merges inbox-defined custom attributes into the current visitor identity. */
    public fun setCustomAttributes(attributes: Map<String, String>) {
        _identity.update { it.copy(customAttributes = it.customAttributes + attributes) }
    }

    /**
     * Clears the visitor identity and the persisted session for the configured inbox — call on
     * logout. Takes effect on the next [ChatPage] open; a session already on screen continues
     * until it is dismissed (`show = false`).
     */
    public fun reset() {
        _identity.value = ChatwootIdentity()
        TokenStore().clearSession(config.websiteToken)
    }

    internal val config: ChatwootConfig
        get() = explicit ?: platformDefaultConfig() ?: error(
            "Chatwoot SDK is not configured. Call Chatwoot.configure(baseUrl, websiteToken) " +
                "or provide the platform metadata (Android manifest <meta-data> " +
                "com.chatwoot.android.BASE_URL / com.chatwoot.android.WEBSITE_TOKEN, " +
                "iOS Info.plist ChatwootBaseUrl / ChatwootWebsiteToken)."
        )
}

/** Reads the platform fallback config (Android manifest meta-data / iOS Info.plist), if present. */
internal expect fun platformDefaultConfig(): ChatwootConfig?
