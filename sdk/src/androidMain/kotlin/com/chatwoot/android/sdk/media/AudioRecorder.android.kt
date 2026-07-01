package com.chatwoot.android.sdk.media

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.chatwoot.android.sdk.appContext
import com.chatwoot.android.sdk.data.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal actual fun rememberAudioRecorder(): AudioRecorder = remember { AndroidAudioRecorder() }

private class AndroidAudioRecorder : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun start() {
        cancel()
        val file = File(appContext.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(64_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        outputFile = file
    }

    override suspend fun stop(): PickedFile? {
        val rec = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        val stopped = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        if (!stopped || file == null || !file.exists()) {
            file?.delete()
            return null
        }
        return withContext(Dispatchers.IO) {
            val bytes = file.readBytes()
            file.delete()
            if (bytes.isEmpty()) null else PickedFile(file.name, "audio/mp4", bytes)
        }
    }

    override fun cancel() {
        recorder?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

@Composable
internal actual fun rememberMicPermission(): MicPermission {
    val controller = remember { AndroidMicPermission() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.onResult(it)
    }
    SideEffect { controller.launcher = launcher }
    return controller
}

private class AndroidMicPermission : MicPermission {
    var launcher: ActivityResultLauncher<String>? = null

    private var grantedState by mutableStateOf(
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
    )
    private var deniedState by mutableStateOf(false)

    override val granted: Boolean get() = grantedState
    override val denied: Boolean get() = deniedState

    override fun request() {
        if (grantedState) return
        launcher?.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun onResult(granted: Boolean) {
        grantedState = granted
        deniedState = !granted
    }
}
