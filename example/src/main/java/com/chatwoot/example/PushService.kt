package com.chatwoot.example

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.chatwoot.sdk.Chatwoot
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

/** The host owns Firebase. Forward refreshed tokens and handle foreground messages here. */
class PushService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val client = DemoSession.get(applicationContext) ?: return
        scope.launch {
            try {
                client.registerDeviceToken(token)
            } catch (e: Exception) {
                android.util.Log.w(
                    "ChatwootDemo",
                    "Token registration failed; enable notifications to retry",
                    e,
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val client = DemoSession.get(applicationContext) ?: return
        if (message.data["chatwoot_sdk_app_id"] != client.sdkAppId) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        Chatwoot.createNotificationChannel(this)
        val id = message.data["chatwoot_conversation_id"]?.toIntOrNull() ?: return
        val intent =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        message.data.forEach { (key, value) -> intent.putExtra(key, value) }
        val pending =
            PendingIntent.getActivity(
                this,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification.Builder(this, "chatwoot_support")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(message.notification?.title ?: "Support")
                .setContentText(message.notification?.body ?: "You have a new support reply")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        getSystemService(NotificationManager::class.java).notify("chatwoot_$id", id, notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
