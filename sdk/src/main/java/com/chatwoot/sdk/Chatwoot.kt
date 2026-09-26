package com.chatwoot.sdk

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent

object Chatwoot {
    internal var client: ChatwootClient? = null

    /**
     * Call from your activity after initializing the client; optional notification data is verified
     * before opening.
     */
    fun showSupport(
        activity: Activity,
        client: ChatwootClient,
        notificationData: Map<String, String> = emptyMap(),
    ) {
        this.client = client
        activity.startActivity(
            Intent(activity, ChatwootActivity::class.java).apply {
                putExtra("base_url", client.baseUrl)
                putExtra("sdk_app_id", client.sdkAppId)
                notificationData.forEach { (key, value) ->
                    if (key.startsWith("chatwoot_")) putExtra(key, value)
                }
            }
        )
    }

    /** Host apps retain their own FirebaseMessagingService and notification permission flow. */
    fun createNotificationChannel(context: Context) {
        context
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    "chatwoot_support",
                    "Support replies",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
    }

    fun notificationData(intent: Intent): Map<String, String> =
        listOf("chatwoot_sdk_app_id", "chatwoot_conversation_id", "chatwoot_inbox_id")
            .mapNotNull { key -> intent.getStringExtra(key)?.let { key to it } }
            .toMap()
}
