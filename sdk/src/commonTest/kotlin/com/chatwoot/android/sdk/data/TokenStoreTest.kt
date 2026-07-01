package com.chatwoot.android.sdk.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {

    @Test
    fun persistsTokenAndIdentifierPerWebsiteToken() {
        val store = TokenStore(MapSettings())
        store.saveConversationToken("wt-a", "jwt-a")
        store.saveActiveIdentifier("wt-a", "user-1")
        store.saveConversationToken("wt-b", "jwt-b")

        assertEquals("jwt-a", store.conversationToken("wt-a"))
        assertEquals("user-1", store.activeIdentifier("wt-a"))
        // A different inbox must not see the first inbox's session.
        assertEquals("jwt-b", store.conversationToken("wt-b"))
        assertNull(store.activeIdentifier("wt-b"))
    }

    @Test
    fun clearSessionForgetsTokenAndIdentifier() {
        val store = TokenStore(MapSettings())
        store.saveConversationToken("wt-a", "jwt-a")
        store.saveActiveIdentifier("wt-a", "user-1")

        store.clearSession("wt-a")

        assertNull(store.conversationToken("wt-a"))
        assertNull(store.activeIdentifier("wt-a"))
    }

    @Test
    fun savingNullIdentifierRemovesAnyStoredValue() {
        val store = TokenStore(MapSettings())
        store.saveActiveIdentifier("wt-a", "user-1")

        store.saveActiveIdentifier("wt-a", null)

        assertNull(store.activeIdentifier("wt-a"))
    }
}
