package com.chatwoot.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import com.chatwoot.sdk.Chatwoot
import com.chatwoot.sdk.ChatwootClient
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var status: TextView
    private var client: ChatwootClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Chatwoot.createNotificationChannel(this)
        val prefs = getSharedPreferences("demo", MODE_PRIVATE)
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 48, 32, 32)
            }
        layout.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                32,
                insets.systemWindowInsetTop + 32,
                32,
                insets.systemWindowInsetBottom + 16,
            )
            insets
        }
        layout.addView(
            TextView(this).apply {
                text = "Chatwoot SDK"
                textSize = 28f
                setPadding(0, 0, 0, 24)
            }
        )
        val url =
            EditText(this).apply {
                hint = "Chatwoot URL"
                setText(prefs.getString("url", "http://10.0.2.2:3127"))
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_URI
            }
        val id =
            EditText(this).apply {
                hint = "SDK app identifier"
                setText(prefs.getString("app_id", intent.getStringExtra("sdk_app_id") ?: ""))
                isSingleLine = true
            }
        layout.addView(url)
        layout.addView(id)
        status = TextView(this).apply { textSize = 14f }
        layout.addView(
            Button(this).apply {
                text = "Connect and open support"
                setOnClickListener {
                    scope.launch {
                        try {
                            client?.let {
                                if (
                                    it.baseUrl != url.text.toString() ||
                                        it.sdkAppId != id.text.toString()
                                )
                                    it.reset()
                            }
                            val connected =
                                ChatwootClient(
                                    applicationContext,
                                    url.text.toString(),
                                    id.text.toString(),
                                )
                            require(id.text.isNotBlank()) { "Enter the SDK app identifier" }
                            connected.connect()
                            client = connected
                            DemoSession.client = connected
                            prefs
                                .edit()
                                .putString("url", connected.baseUrl)
                                .putString("app_id", connected.sdkAppId)
                                .apply()
                            status.text = "Connected"
                            Chatwoot.showSupport(this@MainActivity, connected)
                        } catch (e: Exception) {
                            status.text = e.message
                        }
                    }
                }
            }
        )
        layout.addView(
            Button(this).apply {
                text = "Enable notifications"
                setOnClickListener {
                    if (
                        Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                    )
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                    else registerPush()
                }
            }
        )
        layout.addView(
            Button(this).apply {
                text = "Sign out and clear session"
                setOnClickListener {
                    scope.launch {
                        try {
                            (client ?: DemoSession.get(applicationContext))?.reset()
                            client = null
                            DemoSession.client = null
                            prefs.edit().remove("app_id").apply()
                            id.text.clear()
                            status.text = "Signed out"
                        } catch (e: Exception) {
                            status.text = e.message
                        }
                    }
                }
            }
        )
        layout.addView(status)
        setContentView(layout)
        openNotification(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }

    private fun openNotification(intent: Intent) {
        val data = Chatwoot.notificationData(intent)
        if (data.isEmpty()) return
        val saved = DemoSession.get(applicationContext) ?: return
        client = saved
        Chatwoot.showSupport(this, saved, data)
        data.keys.forEach { intent.removeExtra(it) }
    }

    private fun registerPush() {
        val current = client ?: DemoSession.get(applicationContext)
        if (current == null) {
            status.text = "Connect to your SDK app first"
            return
        }
        if (FirebaseApp.getApps(this).isEmpty()) {
            status.text =
                "Add example/google-services.json from Firebase, then rebuild to test push."
            return
        }
        FirebaseMessaging.getInstance()
            .token
            .addOnSuccessListener { token ->
                scope.launch {
                    try {
                        current.registerDeviceToken(token)
                        status.text = "Push device registered"
                    } catch (e: Exception) {
                        status.text = e.message
                    }
                }
            }
            .addOnFailureListener { status.text = it.message }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            registerPush()
        else status.text = "Notification permission was not granted"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal object DemoSession {
    var client: ChatwootClient? = null

    fun get(context: android.content.Context): ChatwootClient? {
        client?.let {
            return it
        }
        val prefs = context.getSharedPreferences("demo", android.content.Context.MODE_PRIVATE)
        val id = prefs.getString("app_id", null) ?: return null
        return ChatwootClient(context, prefs.getString("url", null) ?: return null, id).also {
            client = it
        }
    }
}
