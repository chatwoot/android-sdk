package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.data.AttachmentType
import com.chatwoot.android.sdk.data.ChatMessage
import com.chatwoot.android.sdk.style.StyleConfig

@Composable
internal fun MessageBubble(message: ChatMessage, style: StyleConfig) {
    if (message.isActivity) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = message.content,
                color = style.secondaryTextColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        return
    }

    val fromContact = message.fromContact
    val hasText = message.content.isNotBlank()
    val hasAttachments = message.attachments.isNotEmpty() || message.pending || message.failed
    // Standalone images/video render without bubble chrome; text, audio and files keep the bubble.
    val bareMedia = !hasText && hasAttachments && (message.pending || message.failed ||
        message.attachments.all { it.type == AttachmentType.Image || it.type == AttachmentType.Video })

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromContact) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (bareMedia) {
            AttachmentContent(message = message, style = style, onContact = fromContact)
            return@Box
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(style.bubbleShape)
                .background(if (fromContact) style.outgoingBubbleColor else style.incomingBubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasText) {
                    Text(
                        text = message.content,
                        color = if (fromContact) style.onOutgoingBubbleColor else style.onIncomingBubbleColor,
                        fontSize = 15.sp,
                    )
                }
                if (hasAttachments) {
                    AttachmentContent(message = message, style = style, onContact = fromContact)
                }
            }
        }
    }
}
