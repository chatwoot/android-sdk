package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwoot.android.sdk.style.StyleConfig

@Composable
internal fun ChatHeader(style: StyleConfig, connected: Boolean, onFinish: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.primaryColor)
            // Fill behind the status bar (no gap above the header); content sits below it.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.title,
                color = style.onPrimaryColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (!connected) {
                Text(
                    text = "connecting…",
                    color = style.onPrimaryColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
        }
        IconButton(onClick = onFinish) {
            Text(text = "✕", color = style.onPrimaryColor, fontSize = 18.sp)
        }
    }
}

@Composable
internal fun ErrorBanner(error: String, style: StyleConfig, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFFFFE5E5))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = error,
            color = androidx.compose.ui.graphics.Color(0xFFB3261E),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Text(text = "✕", color = androidx.compose.ui.graphics.Color(0xFFB3261E), fontSize = 14.sp)
        }
    }
}
