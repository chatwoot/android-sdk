package com.chatwoot.android.sdk

import androidx.compose.runtime.Composable
import com.chatwoot.android.sdk.style.DefaultStyle
import com.chatwoot.android.sdk.style.StyleConfig
import com.chatwoot.android.sdk.ui.ChatScreen

/**
 * The Chatwoot chat screen.
 *
 * Requires the SDK to be configured first — see [Chatwoot.configure].
 *
 * @param show Whether the page is visible. When false, nothing is composed and the
 *   chat session (including the websocket) is torn down.
 * @param onFinish Called when the user closes the chat via the header close button.
 * @param styleConfig Visual customisation; defaults to the stock Chatwoot look.
 */
@Composable
public fun ChatPage(
    show: Boolean,
    onFinish: () -> Unit,
    styleConfig: StyleConfig = DefaultStyle,
) {
    if (!show) return
    ChatScreen(onFinish = onFinish, style = styleConfig)
}
