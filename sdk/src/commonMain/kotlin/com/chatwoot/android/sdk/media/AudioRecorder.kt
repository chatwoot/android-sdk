package com.chatwoot.android.sdk.media

import androidx.compose.runtime.Composable
import com.chatwoot.android.sdk.data.PickedFile

/**
 * Records a single voice note with the platform's native recorder (`MediaRecorder` on Android,
 * `AVAudioRecorder` on iOS), producing AAC/m4a. [stop] returns the clip ready to upload through
 * the existing attachment path, or null if nothing usable was captured.
 */
internal interface AudioRecorder {
    fun start()
    suspend fun stop(): PickedFile?
    fun cancel()
}

@Composable
internal expect fun rememberAudioRecorder(): AudioRecorder

/**
 * The microphone permission, surfaced as Compose-observable state. [request] triggers the system
 * prompt; the SDK never shows its own UI for a denial — the caller just hides the mic affordance.
 */
internal interface MicPermission {
    val granted: Boolean
    val denied: Boolean
    fun request()
}

@Composable
internal expect fun rememberMicPermission(): MicPermission
