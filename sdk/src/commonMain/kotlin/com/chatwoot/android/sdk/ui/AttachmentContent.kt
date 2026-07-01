package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.chatwoot.android.sdk.data.ChatAttachment
import com.chatwoot.android.sdk.data.ChatMessage
import com.chatwoot.android.sdk.data.AttachmentType
import com.chatwoot.android.sdk.media.VideoPlayer
import com.chatwoot.android.sdk.media.rememberAudioPlayback
import com.chatwoot.android.sdk.style.StyleConfig

private val MediaWidth = 240.dp
private val MediaShape = RoundedCornerShape(10.dp)

@Composable
internal fun AttachmentContent(message: ChatMessage, style: StyleConfig, onContact: Boolean) {
    if (message.pending || message.failed) {
        UploadingAttachment(message, style, onContact)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.attachments.forEach { attachment ->
            when (attachment.type) {
                AttachmentType.Image -> ImageAttachment(attachment)
                AttachmentType.Video -> VideoAttachment(attachment)
                AttachmentType.Audio -> AudioAttachment(attachment, style, onContact)
                AttachmentType.File -> FileAttachment(attachment, style, onContact)
            }
        }
    }
}

@Composable
private fun ImageAttachment(attachment: ChatAttachment) {
    var fullScreen by remember { mutableStateOf(false) }
    AsyncImage(
        model = attachment.thumbUrl ?: attachment.url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(MediaWidth)
            .aspectRatio(attachment.aspectRatio())
            .clip(MediaShape)
            .clickable { fullScreen = true },
    )
    if (fullScreen) {
        FullScreenViewer(onDismiss = { fullScreen = false }) {
            AsyncImage(
                model = attachment.url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VideoAttachment(attachment: ChatAttachment) {
    var playing by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(MediaWidth)
            .aspectRatio(attachment.aspectRatio())
            .clip(MediaShape)
            .background(Color.Black)
            .clickable { playing = true },
        contentAlignment = Alignment.Center,
    ) {
        attachment.thumbUrl?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)),
            contentAlignment = Alignment.Center,
        ) {
            Text("▶", color = Color.White, fontSize = 22.sp)
        }
    }
    if (playing) {
        FullScreenViewer(onDismiss = { playing = false }) {
            VideoPlayer(url = attachment.url, modifier = Modifier.fillMaxWidth().aspectRatio(attachment.aspectRatio(landscape = true)))
        }
    }
}

@Composable
private fun AudioAttachment(attachment: ChatAttachment, style: StyleConfig, onContact: Boolean) {
    val playback = rememberAudioPlayback(attachment.url)
    val tint = if (onContact) style.onOutgoingBubbleColor else style.primaryColor
    val fraction = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f
    Row(
        modifier = Modifier.width(MediaWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (playback.isPlaying) "⏸" else "▶",
            color = tint,
            fontSize = 22.sp,
            modifier = Modifier.clickable { playback.playPause() }.padding(4.dp),
        )
        Slider(
            value = fraction,
            onValueChange = { playback.seekToFraction(it) },
            modifier = Modifier.weight(1f),
        )
        Text(formatDuration(if (playback.isPlaying || fraction > 0f) playback.positionMs else playback.durationMs), color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun FileAttachment(attachment: ChatAttachment, style: StyleConfig, onContact: Boolean) {
    val content = if (onContact) style.onOutgoingBubbleColor else style.onIncomingBubbleColor
    Row(
        modifier = Modifier.width(MediaWidth).clickable { com.chatwoot.android.sdk.media.openExternally(attachment.url) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("📄", fontSize = 26.sp)
        Column {
            Text(attachment.fileName ?: "Attachment", color = content, fontSize = 14.sp, maxLines = 1)
            attachment.fileSize?.let { Text(formatBytes(it), color = content.copy(alpha = 0.7f), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun UploadingAttachment(message: ChatMessage, style: StyleConfig, onContact: Boolean) {
    val preview = message.localPreview
    val tint = if (onContact) style.onOutgoingBubbleColor else style.primaryColor
    Box(
        modifier = Modifier.width(MediaWidth).aspectRatio(1.4f).clip(MediaShape).background(Color(0x22000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null && preview.attachmentType == AttachmentType.Image) {
            AsyncImage(model = preview.bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (message.failed) {
            Text("Upload failed", color = tint, fontSize = 13.sp)
        } else {
            CircularProgressIndicator(
                progress = { message.uploadProgress ?: 0f },
                color = tint,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun FullScreenViewer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xEE000000)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

private fun ChatAttachment.aspectRatio(landscape: Boolean = false): Float {
    val w = width
    val h = height
    return if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h else if (landscape) 16f / 9f else 4f / 3f
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
