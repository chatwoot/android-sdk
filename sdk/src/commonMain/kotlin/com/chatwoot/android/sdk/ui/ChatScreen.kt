package com.chatwoot.android.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.chatwoot.android.sdk.ChatViewModel
import com.chatwoot.android.sdk.style.StyleConfig

@Composable
internal fun ChatScreen(
    onFinish: () -> Unit,
    style: StyleConfig,
    viewModel: ChatViewModel = viewModel { ChatViewModel() },
) {
    // Coil loads attachment images over the same Ktor stack the SDK already uses (KMP-wide).
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Scroll to the newest message whenever one is appended or replaced (sent OR received).
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(style.backgroundColor)
            // Header paints its own status-bar inset (edge-to-edge); here we only inset the
            // sides + bottom. safeDrawing's bottom already maxes the nav bar and IME, so the
            // input bar rises with the keyboard without double-counting.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        ChatHeader(style = style, connected = state.connected, onFinish = onFinish)

        state.error?.let { error ->
            ErrorBanner(error = error, style = style, onDismiss = viewModel::dismissError)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading) {
                CircularProgressIndicator(
                    color = style.primaryColor,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = state.messages.size, key = { state.messages[it].id }) { index ->
                        MessageBubble(message = state.messages[index], style = style)
                    }
                }
            }
        }

        if (state.agentTyping) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "typing…",
                    color = style.secondaryTextColor,
                    fontSize = 13.sp,
                )
            }
        }

        InputBar(
            style = style,
            enabled = !state.loading,
            onSend = viewModel::send,
            onPickAttachment = viewModel::sendAttachment,
        )
    }
}
