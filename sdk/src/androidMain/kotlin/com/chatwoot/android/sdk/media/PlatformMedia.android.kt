package com.chatwoot.android.sdk.media

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chatwoot.android.sdk.appContext
import kotlinx.coroutines.delay

@Composable
internal actual fun VideoPlayer(url: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = { PlayerView(it).apply { this.player = player } },
    )
}

@Composable
internal actual fun rememberAudioPlayback(url: String): AudioPlayback {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    val playback = remember(player) { ExoAudioPlayback(player) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playback.playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) playback.duration = player.duration.coerceAtLeast(0)
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }
    LaunchedEffect(player) {
        while (true) {
            playback.position = player.currentPosition.coerceAtLeast(0)
            delay(200)
        }
    }
    return playback
}

private class ExoAudioPlayback(private val player: ExoPlayer) : AudioPlayback {
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    override val isPlaying: Boolean get() = playing
    override val positionMs: Long get() = position
    override val durationMs: Long get() = duration

    override fun playPause() {
        if (player.isPlaying) player.pause()
        else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    override fun seekToFraction(fraction: Float) {
        val d = player.duration
        if (d > 0) player.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }
}

internal actual fun openExternally(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
}
