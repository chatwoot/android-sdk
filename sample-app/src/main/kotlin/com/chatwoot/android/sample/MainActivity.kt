package com.chatwoot.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chatwoot.android.sdk.ChatPage
import com.chatwoot.android.sdk.Chatwoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Chatwoot.configure(
            baseUrl = BuildConfig.CHATWOOT_BASE_URL,
            websiteToken = BuildConfig.CHATWOOT_WEBSITE_TOKEN,
        )

        // Identify the visitor so agents see a named contact instead of an anonymous one.
        // Call after your app's own login; pass `identifierHash` (computed server-side) only if
        // the inbox enforces identity validation. On logout, call Chatwoot.reset().
        Chatwoot.setUser(
            identifier = "sample-user-1",
            name = "Sample User",
            email = "sample@example.com",
            customAttributes = mapOf("plan" to "free"),
        )

        setContent {
            MaterialTheme {
                var showChat by remember { mutableStateOf(false) }

                if (showChat) {
                    ChatPage(show = true, onFinish = { showChat = false })
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { showChat = true }) {
                            Text("Open chat")
                        }
                    }
                }
            }
        }
    }
}
