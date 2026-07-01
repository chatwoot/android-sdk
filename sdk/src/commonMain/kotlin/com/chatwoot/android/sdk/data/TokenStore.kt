package com.chatwoot.android.sdk.data

import com.russhwolf.settings.Settings

/**
 * Persists the `cw_conversation` session JWT (SharedPreferences / NSUserDefaults) so the
 * same contact + conversation is resumed across app launches. Keyed per website token —
 * switching inboxes must not leak another inbox's session.
 */
internal class TokenStore(private val settings: Settings = Settings()) {

    fun conversationToken(websiteToken: String): String? =
        settings.getStringOrNull(key(websiteToken))

    fun saveConversationToken(websiteToken: String, token: String) {
        settings.putString(key(websiteToken), token)
    }

    /** The host-supplied identifier the persisted session belongs to, if any. */
    fun activeIdentifier(websiteToken: String): String? =
        settings.getStringOrNull(identifierKey(websiteToken))

    fun saveActiveIdentifier(websiteToken: String, identifier: String?) {
        if (identifier == null) settings.remove(identifierKey(websiteToken))
        else settings.putString(identifierKey(websiteToken), identifier)
    }

    /** Forgets the persisted session so the next bootstrap creates a fresh contact. */
    fun clearSession(websiteToken: String) {
        settings.remove(key(websiteToken))
        settings.remove(identifierKey(websiteToken))
    }

    private fun key(websiteToken: String) = "cw_conversation_$websiteToken"

    private fun identifierKey(websiteToken: String) = "cw_identifier_$websiteToken"
}
