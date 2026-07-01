package com.chatwoot.android.sdk.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays [url] inline with the platform's native player and built-in transport controls
 * (Media3 `PlayerView` on Android, `AVPlayer` on iOS). Used for video attachments.
 */
@Composable
internal expect fun VideoPlayer(url: String, modifier: Modifier)

/**
 * A lightweight audio player for voice-note bubbles, exposing Compose-observable state so a
 * custom play/pause + progress row can drive it. Backed by Media3 (Android) / AVPlayer (iOS).
 */
internal interface AudioPlayback {
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    fun playPause()
    fun seekToFraction(fraction: Float)
}

@Composable
internal expect fun rememberAudioPlayback(url: String): AudioPlayback

/** Opens [url] in the system handler (browser / viewer). Used for generic file attachments. */
internal expect fun openExternally(url: String)
