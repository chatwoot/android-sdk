package com.chatwoot.android.sdk

import platform.Foundation.NSBundle

internal actual fun platformDefaultConfig(): ChatwootConfig? {
    val bundle = NSBundle.mainBundle
    val baseUrl = bundle.objectForInfoDictionaryKey("ChatwootBaseUrl") as? String ?: return null
    val token = bundle.objectForInfoDictionaryKey("ChatwootWebsiteToken") as? String ?: return null
    return ChatwootConfig(baseUrl, token)
}
