package com.chatwoot.android.sdk.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Visual customisation for [com.chatwoot.android.sdk.ChatPage]. All theming flows through
 * this one object; pass a copy of [DefaultStyle] with overrides.
 *
 * @property primaryColor Brand color — fills the header bar and tints the send button.
 * @property onPrimaryColor Content (title, close icon) drawn on top of [primaryColor].
 * @property backgroundColor Background of the message list area.
 * @property surfaceColor Background of the input bar at the bottom.
 * @property outgoingBubbleColor Bubble fill for messages sent by the contact (the app user).
 * @property onOutgoingBubbleColor Text color inside outgoing bubbles.
 * @property incomingBubbleColor Bubble fill for agent/bot messages.
 * @property onIncomingBubbleColor Text color inside incoming bubbles.
 * @property textColor Primary text color, used for the message input.
 * @property secondaryTextColor De-emphasised text: input placeholder, typing indicator,
 *   activity messages (e.g. "Conversation was resolved").
 * @property bubbleShape Shape clipping every message bubble.
 * @property title Header title of the chat page.
 */
public data class StyleConfig(
    val primaryColor: Color = Color(0xFF1F93FF),
    val onPrimaryColor: Color = Color.White,
    val backgroundColor: Color = Color(0xFFF7F8FA),
    val surfaceColor: Color = Color.White,
    val outgoingBubbleColor: Color = Color(0xFF1F93FF),
    val onOutgoingBubbleColor: Color = Color.White,
    val incomingBubbleColor: Color = Color.White,
    val onIncomingBubbleColor: Color = Color(0xFF1B1B33),
    val textColor: Color = Color(0xFF1B1B33),
    val secondaryTextColor: Color = Color(0xFF6E7191),
    val bubbleShape: Shape = RoundedCornerShape(12.dp),
    val title: String = "Chat with us",
)

/** The stock Chatwoot look. */
public val DefaultStyle: StyleConfig = StyleConfig()
