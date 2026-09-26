package com.chatwoot.sdk

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*
import org.json.JSONObject

/** Native, dependency-light support UI. Launch through Chatwoot.showSupport. */
class ChatwootActivity : Activity() {
    private lateinit var client: ChatwootClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var footer: LinearLayout
    private lateinit var error: TextView
    private var conversationId: Int? = null
    private var screen = "list"
    private var page = 1
    private var history = mutableListOf<JSONObject>()
    private var rows = mutableListOf<JSONObject>()
    private var refreshJob: Job? = null
    private var sending = false
    private val muted = Color.rgb(106, 106, 106)
    private val surface = Color.rgb(250, 250, 250)
    private val teal = Color.rgb(13, 165, 148)
    private val brand
        get() = runCatching { Color.parseColor(client.color) }.getOrDefault(Color.rgb(39, 129, 246))

    private val brandText
        get() = if (Color.luminance(brand) > .45) Color.BLACK else Color.WHITE

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 14, border: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            border?.let { setStroke(dp(1), it) }
        }

    private fun text(value: String, size: Float = 14f, color: Int = Color.rgb(32, 32, 32)) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun button(value: String, action: () -> Unit) =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(brandText)
            background = rounded(brand, 12)
            stateListAnimator = null
            setOnClickListener { action() }
        }

    private fun field(hint: String) =
        EditText(this).apply {
            this.hint = hint
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setHintTextColor(muted)
            background = rounded(Color.rgb(241, 241, 241), 10)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.rgb(32, 32, 32)
        client =
            Chatwoot.client?.takeIf {
                it.sdkAppId == intent.getStringExtra("sdk_app_id") &&
                    it.baseUrl == intent.getStringExtra("base_url")
            }
                ?: ChatwootClient(
                    applicationContext,
                    intent.getStringExtra("base_url") ?: return finish(),
                    intent.getStringExtra("sdk_app_id") ?: return finish(),
                )
        root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(surface)
            }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
            insets
        }
        header =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundColor(Color.WHITE)
            }
        error =
            text("", 13f, Color.rgb(180, 35, 55)).apply {
                visibility = View.GONE
                setPadding(dp(20), dp(4), dp(20), dp(4))
            }
        body =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(16), dp(20), dp(16))
            }
        scroll =
            ScrollView(this).apply {
                isFillViewport = true
                addView(body)
            }
        footer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12))
            }
        root.addView(header)
        root.addView(error)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(footer)
        setContentView(root)
        heading("Support", false)
        body.addView(text("Connecting…", 14f, muted))
        task {
            client.connect()
            val notification = Chatwoot.notificationData(intent)
            val target =
                if (notification.isNotEmpty()) {
                    try {
                        client.notificationConversation(notification)
                    } catch (e: java.io.IOException) {
                        showList()
                        throw e
                    }
                } else savedInstanceState?.getInt("conversation_id", 0)?.takeIf { it > 0 }
            if (target != null) openConversation(target) else showList()
            client.resume { refresh() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        conversationId?.let { outState.putInt("conversation_id", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (::client.isInitialized) client.resume { refresh() }
    }

    override fun onStop() {
        client.pause()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Native back navigation")
    override fun onBackPressed() {
        if (screen != "list") showList() else super.onBackPressed()
    }

    private fun task(block: suspend () -> Unit) =
        scope.launch {
            try {
                error.visibility = View.GONE
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.text = e.message ?: "Please try again"
                error.visibility = View.VISIBLE
            }
        }

    private fun heading(title: String, back: Boolean) {
        header.removeAllViews()
        header.addView(
            text(if (back) "‹" else "", 26f).apply {
                gravity = Gravity.CENTER
                contentDescription = "Back to messages"
                setOnClickListener { showList() }
            },
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        val labels =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
        labels.addView(
            text(title, 16f).apply {
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
            }
        )
        if (client.replyTime.isNotEmpty())
            labels.addView(text(client.replyTime, 12f, muted).apply { gravity = Gravity.CENTER })
        header.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(
            text("×", 26f, muted).apply {
                gravity = Gravity.CENTER
                contentDescription = "Close support"
                setOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
    }

    private fun branding() {
        if (!client.hideBranding)
            footer.addView(
                text("Powered by Chatwoot", 11f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, 0)
                }
            )
    }

    private fun showList() {
        refreshJob?.cancel()
        screen = "list"
        conversationId = null
        page = 1
        heading("Messages", false)
        footer.removeAllViews()
        footer.addView(button("Start a new conversation  ›") { showPreChat() })
        branding()
        body.removeAllViews()
        body.addView(text("Loading conversations…", 14f, muted))
        refresh()
    }

    private fun refresh() {
        if (screen == "form") return
        refreshJob?.cancel()
        refreshJob = task {
            if (screen == "list") loadList(false) else conversationId?.let { loadMessages(it) }
        }
    }

    private suspend fun loadList(more: Boolean) {
        val data = client.conversations(if (more) page + 1 else 1)
        if (screen != "list") return
        if (more) {
            page++
            rows.addAll(data.getJSONArray("payload").objects())
        } else {
            page = 1
            rows = data.getJSONArray("payload").objects().toMutableList()
        }
        body.removeAllViews()
        if (rows.isEmpty())
            body.addView(
                text("No conversations yet. Send us a message to get started.", 14f, muted)
            )
        rows
            .distinctBy { it.getInt("id") }
            .forEach { conversation ->
                val message = conversation.optJSONObject("last_message")
                val card =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = rounded(Color.WHITE, 14, Color.rgb(231, 231, 231))
                        setOnClickListener { openConversation(conversation.getInt("id")) }
                    }
                val sender =
                    message?.optJSONObject("sender")?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: client.title
                card.addView(
                    text(sender.take(1).uppercase(), 18f, muted).apply {
                        gravity = Gravity.CENTER
                        background = rounded(Color.rgb(235, 235, 235), 24)
                    },
                    LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) },
                )
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                labels.addView(text(client.title, 14f).apply { setTypeface(null, Typeface.BOLD) })
                val prefix = if (message?.optInt("message_type") == 0) "↶ " else ""
                val preview =
                    message?.optString("content")?.takeUnless { it == "null" || it.isBlank() }
                        ?: "Attachment"
                labels.addView(
                    text(prefix + preview, 14f, muted).apply {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                )
                card.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                val metadata =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.END
                    }
                metadata.addView(
                    text(relativeTime(conversation.optLong("last_activity_at")), 11f, muted)
                )
                val unread = conversation.optInt("unread_count")
                if (unread > 0)
                    metadata.addView(
                        text(if (unread > 99) "99+" else unread.toString(), 11f, Color.WHITE)
                            .apply {
                                gravity = Gravity.CENTER
                                minWidth = dp(20)
                                background = rounded(teal, 12)
                                setPadding(dp(5), dp(2), dp(5), dp(2))
                            }
                    )
                card.addView(metadata)
                body.addView(
                    card,
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) },
                )
            }
        if (rows.isNotEmpty() && data.getJSONObject("meta").optBoolean("has_next_page"))
            body.addView(button("Load more") { task { loadList(true) } })
    }

    private fun showPreChat() {
        refreshJob?.cancel()
        screen = "form"
        heading(client.title, true)
        body.removeAllViews()
        footer.removeAllViews()
        val intro =
            client.config
                .optJSONObject("pre_chat_form_options")
                ?.optString("pre_chat_message")
                ?.takeUnless { it == "null" }
                .orEmpty()
        if (intro.isNotBlank()) body.addView(text(intro, 16f))
        val inputs = mutableMapOf<String, () -> String>()
        client.preChatFields().forEach { config ->
            val name = config.getString("name")
            val type = config.getString("type")
            body.addView(
                text(config.getString("label") + if (config.optBoolean("required")) " *" else "")
            )
            when (type) {
                "checkbox" -> {
                    val input = CheckBox(this)
                    body.addView(input)
                    inputs[name] = { input.isChecked.toString() }
                }
                "list",
                "select" -> {
                    val options = config.optJSONArray("values")
                    val values =
                        listOf("Choose an option") +
                            (0 until (options?.length() ?: 0)).map { options!!.getString(it) }
                    val input =
                        Spinner(this).apply {
                            adapter =
                                ArrayAdapter(
                                    this@ChatwootActivity,
                                    android.R.layout.simple_spinner_dropdown_item,
                                    values,
                                )
                        }
                    body.addView(input)
                    inputs[name] = {
                        if (input.selectedItemPosition == 0) ""
                        else values[input.selectedItemPosition]
                    }
                }
                else -> {
                    val input =
                        field(config.optString("placeholder").takeUnless { it == "null" }.orEmpty())
                            .apply {
                                inputType =
                                    when (type) {
                                        "email" ->
                                            InputType.TYPE_CLASS_TEXT or
                                                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                                        "number" ->
                                            InputType.TYPE_CLASS_NUMBER or
                                                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                                                InputType.TYPE_NUMBER_FLAG_SIGNED
                                        else -> InputType.TYPE_CLASS_TEXT
                                    }
                                if (type == "textarea") {
                                    minLines = 3
                                    inputType =
                                        InputType.TYPE_CLASS_TEXT or
                                            InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                }
                                if (type == "date") hint = "YYYY-MM-DD"
                            }
                    body.addView(
                        input,
                        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) },
                    )
                    inputs[name] = { input.text.toString() }
                }
            }
        }
        body.addView(text("Your message"))
        val message =
            field("How can we help?").apply {
                minLines = 3
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        body.addView(message)
        val send = button("Send message  ›") {}
        send.setOnClickListener {
            if (sending) return@setOnClickListener
            sending = true
            send.isEnabled = false
            task {
                try {
                    openConversation(
                        client.startConversation(
                            message.text.toString(),
                            inputs.mapValues { it.value() },
                        )
                    )
                } finally {
                    sending = false
                    send.isEnabled = true
                }
            }
        }
        footer.addView(send)
        branding()
    }

    private fun openConversation(id: Int) {
        refreshJob?.cancel()
        screen = "conversation"
        conversationId = id
        history.clear()
        heading(client.title, true)
        footer.removeAllViews()
        body.removeAllViews()
        val composer =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.rgb(241, 241, 241), 12)
            }
        val input =
            field("Write a message…").apply {
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(
            text("＋", 24f, muted).apply {
                contentDescription = "Add attachment"
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "*/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        },
                        41,
                    )
                }
            }
        )
        val send =
            text("➤", 22f, muted).apply {
                contentDescription = "Send message"
                setPadding(dp(10), dp(8), dp(12), dp(8))
            }
        send.setOnClickListener {
            if (sending || input.text.isBlank()) return@setOnClickListener
            sending = true
            send.isEnabled = false
            task {
                try {
                    client.send(id, input.text.toString())
                    input.text.clear()
                    loadMessages(id)
                } finally {
                    sending = false
                    send.isEnabled = true
                }
            }
        }
        composer.addView(send)
        footer.addView(composer)
        branding()
        task {
            val conversation =
                client.request("conversations", query = mapOf("conversation_id" to id.toString()))
            if (conversationId != id) return@task
            if (conversation.optString("status") == "resolved" && !client.canReplyAfterResolved) {
                footer.removeAllViews()
                footer.addView(
                    text(
                        "This conversation is resolved. Start a new conversation for more help.",
                        14f,
                        muted,
                    )
                )
                branding()
            }
            loadMessages(id)
        }
    }

    private suspend fun loadMessages(id: Int, older: Boolean = false) {
        val page =
            client
                .messages(id, if (older) history.minOfOrNull { it.getInt("id") } else null)
                .objects()
        if (conversationId != id || screen != "conversation") return
        val atBottom = scroll.getChildAt(0).height - scroll.height - scroll.scrollY < dp(100)
        history =
            (if (older) page + history else history + page)
                .associateBy { it.getInt("id") }
                .values
                .sortedBy { it.getInt("id") }
                .toMutableList()
        body.removeAllViews()
        if (page.size >= 20 || older && page.isNotEmpty())
            body.addView(button("Earlier messages") { task { loadMessages(id, true) } })
        history.forEach { message ->
            val outgoing = message.optInt("message_type") == 0
            if (message.optInt("message_type") == 2 || message.optBoolean("private")) return@forEach
            val bubble =
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(7), dp(12), dp(7))
                    background =
                        rounded(if (outgoing) blend(brand) else Color.rgb(233, 233, 233), 14)
                }
            val content = message.optString("content").takeUnless { it == "null" }.orEmpty()
            if (content.isNotBlank())
                bubble.addView(
                    text(content).apply {
                        maxWidth = (resources.displayMetrics.widthPixels * .72).toInt()
                        autoLinkMask = Linkify.WEB_URLS
                        linksClickable = true
                    }
                )
            message.optJSONArray("attachments").objects().forEach { attachment ->
                val url = attachment.optString("data_url")
                val label = attachment.optString("file_type", "Attachment")
                bubble.addView(
                    text("↗ $label", 14f, Color.rgb(30, 100, 190)).apply {
                        setOnClickListener { openLink(url) }
                    }
                )
            }
            if (message.optString("content_type") == "input_email") {
                val email = field("Your email address")
                bubble.addView(email)
                bubble.addView(
                    button("Save email") {
                        task {
                            client.submitEmail(message.getInt("id"), email.text.toString())
                            loadMessages(id)
                        }
                    }
                )
            }
            val row =
                LinearLayout(this).apply { gravity = if (outgoing) Gravity.END else Gravity.START }
            row.addView(bubble, LinearLayout.LayoutParams(-2, -2))
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            val time = message.optLong("created_at")
            val sender =
                message.optJSONObject("sender")?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: "Support"
            body.addView(
                text(
                        (if (outgoing) "" else "$sender · ") +
                            if (time > 0)
                                SimpleDateFormat("h:mm a", Locale.getDefault())
                                    .format(Date(time * 1000))
                            else "",
                        11f,
                        muted,
                    )
                    .apply { gravity = if (outgoing) Gravity.END else Gravity.START }
            )
        }
        client.markRead(id)
        if (!older && atBottom) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun openLink(url: String) {
        val uri = android.net.Uri.parse(url)
        if (uri.scheme in listOf("http", "https")) startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    @Deprecated("Activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        val id = conversationId ?: return
        if (requestCode != 41 || resultCode != RESULT_OK) return
        task {
            val info =
                withContext(Dispatchers.IO) {
                    var name = "attachment"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst())
                            name =
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(
                                        android.provider.OpenableColumns.DISPLAY_NAME
                                    )
                                )
                    }
                    val bytes =
                        contentResolver.openInputStream(uri)!!.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 20 * 1024 * 1024) {
                                    "Choose a file smaller than 20 MB"
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    require(bytes.size <= 20 * 1024 * 1024) { "Choose a file smaller than 20 MB" }
                    Triple(bytes, name, contentResolver.getType(uri) ?: "application/octet-stream")
                }
            client.upload(id, info.first, info.second, info.third)
            loadMessages(id)
        }
    }

    private fun blend(color: Int) =
        Color.rgb(
            (Color.red(color) * .18 + 255 * .82).toInt(),
            (Color.green(color) * .18 + 255 * .82).toInt(),
            (Color.blue(color) * .18 + 255 * .82).toInt(),
        )

    private fun relativeTime(seconds: Long): String {
        val minutes = (System.currentTimeMillis() / 1000 - seconds) / 60
        return when {
            seconds <= 0 -> ""
            minutes < 1 -> "Now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes/60}h ago"
            else -> "${minutes/1440}d ago"
        }
    }
}
