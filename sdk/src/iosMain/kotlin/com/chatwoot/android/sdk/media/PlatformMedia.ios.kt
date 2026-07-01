package com.chatwoot.android.sdk.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun VideoPlayer(url: String, modifier: Modifier) {
    val player = remember(url) { AVPlayer(uRL = NSURL.URLWithString(url) ?: NSURL()) }
    DisposableEffect(player) {
        player.play()
        onDispose { player.pause() }
    }
    UIKitViewController(
        factory = { AVPlayerViewController().apply { this.player = player } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberAudioPlayback(url: String): AudioPlayback {
    val player = remember(url) { AVPlayer(uRL = NSURL.URLWithString(url) ?: NSURL()) }
    val playback = remember(player) { AvAudioPlayback(player) }
    DisposableEffect(player) {
        val interval = CMTimeMakeWithSeconds(0.2, preferredTimescale = 600)
        val observer = player.addPeriodicTimeObserverForInterval(interval, queue = null) { _ ->
            playback.refresh()
        }
        onDispose {
            player.removeTimeObserver(observer)
            player.pause()
        }
    }
    return playback
}

@OptIn(ExperimentalForeignApi::class)
private class AvAudioPlayback(private val player: AVPlayer) : AudioPlayback {
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    override val isPlaying: Boolean get() = playing
    override val positionMs: Long get() = position
    override val durationMs: Long get() = duration

    fun refresh() {
        playing = player.rate != 0f
        position = (CMTimeGetSeconds(player.currentTime()) * 1000).toLongOrZero()
        player.currentItem?.let { duration = (CMTimeGetSeconds(it.duration) * 1000).toLongOrZero() }
    }

    override fun playPause() {
        if (player.rate != 0f) player.pause() else player.play()
        playing = player.rate != 0f
    }

    override fun seekToFraction(fraction: Float) {
        val seconds = player.currentItem?.let { CMTimeGetSeconds(it.duration) } ?: return
        if (seconds.isNaN() || seconds <= 0) return
        player.seekToTime(CMTimeMakeWithSeconds(seconds * fraction.coerceIn(0f, 1f), preferredTimescale = 600))
    }
}

private fun Double.toLongOrZero(): Long = if (isNaN() || isInfinite()) 0L else toLong()

internal actual fun openExternally(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
}
