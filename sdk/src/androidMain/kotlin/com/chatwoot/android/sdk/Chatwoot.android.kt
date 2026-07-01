package com.chatwoot.android.sdk

import android.content.Context
import android.content.pm.PackageManager
import androidx.startup.Initializer

internal lateinit var appContext: Context

/** Captures the application context before any SDK call; wired via androidx.startup. */
public class ChatwootInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        appContext = context.applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

internal actual fun platformDefaultConfig(): ChatwootConfig? {
    if (!::appContext.isInitialized) return null
    val metaData = appContext.packageManager
        .getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
        .metaData ?: return null
    val baseUrl = metaData.getString("com.chatwoot.android.BASE_URL") ?: return null
    val token = metaData.getString("com.chatwoot.android.WEBSITE_TOKEN") ?: return null
    return ChatwootConfig(baseUrl, token)
}
