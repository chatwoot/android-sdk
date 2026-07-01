package com.chatwoot.android.sdk.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chatwoot.android.sdk.data.PickedFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.AVFAudio.AVAudioQualityHigh
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@Composable
internal actual fun rememberAudioRecorder(): AudioRecorder = remember { IosAudioRecorder() }

@OptIn(ExperimentalForeignApi::class)
private class IosAudioRecorder : AudioRecorder {
    private var recorder: AVAudioRecorder? = null
    private var url: NSURL? = null

    override fun start() {
        cancel()
        val session = AVAudioSession.sharedInstance()
        runCatching {
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
            session.setActive(true, null)
        }
        // NSTemporaryDirectory() ends with '/'. A fixed name is safe: start() cancels any prior take.
        val fileUrl = NSURL.fileURLWithPath(NSTemporaryDirectory() + "cw_voice_note.m4a")
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber(double = 44_100.0),
            AVNumberOfChannelsKey to NSNumber(int = 1),
            AVEncoderAudioQualityKey to NSNumber(long = AVAudioQualityHigh),
        )
        val rec = AVAudioRecorder(fileUrl, settings, null)
        rec.record()
        recorder = rec
        url = fileUrl
    }

    override suspend fun stop(): PickedFile? {
        val rec = recorder ?: return null
        val fileUrl = url
        recorder = null
        url = null
        rec.stop()
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
        val bytes = fileUrl?.let { readBytes(it) }
        return if (bytes == null || bytes.isEmpty()) null else PickedFile("voice_note.m4a", "audio/mp4", bytes)
    }

    override fun cancel() {
        recorder?.stop()
        recorder = null
        url = null
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
    }

    private fun readBytes(fileUrl: NSURL): ByteArray? {
        val data: NSData = NSData.dataWithContentsOfURL(fileUrl) ?: return null
        val length = data.length.toInt()
        if (length == 0) return null
        val bytes = ByteArray(length)
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, length.toULong()) }
        return bytes
    }
}

@Composable
internal actual fun rememberMicPermission(): MicPermission {
    val controller = remember { IosMicPermission() }
    return controller
}

private class IosMicPermission : MicPermission {
    private var grantedState by mutableStateOf(
        AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted,
    )
    private var deniedState by mutableStateOf(
        AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionDenied,
    )

    override val granted: Boolean get() = grantedState
    override val denied: Boolean get() = deniedState

    override fun request() {
        if (grantedState) return
        AVAudioSession.sharedInstance().requestRecordPermission { allowed ->
            dispatch_async(dispatch_get_main_queue()) {
                grantedState = allowed
                deniedState = !allowed
            }
        }
    }
}
