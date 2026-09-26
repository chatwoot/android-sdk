package com.chatwoot.sdk

import android.util.Patterns
import java.time.LocalDate
import org.json.JSONObject

internal object PreChat {
    fun payload(fields: List<JSONObject>, text: String, values: Map<String, String>): JSONObject {
        require(text.isNotBlank()) { "Enter a message" }
        val contact = JSONObject()
        val contactAttributes = JSONObject()
        val conversationAttributes = JSONObject()
        for (field in fields) {
            val name = field.getString("name")
            val type = field.getString("type")
            val value = values[name].orEmpty().trim()
            val label = field.getString("label")
            require(
                type in
                    listOf(
                        "text",
                        "textarea",
                        "email",
                        "number",
                        "date",
                        "checkbox",
                        "list",
                        "select",
                        "link",
                        "url",
                    )
            ) {
                "$label: this field is not supported yet"
            }
            require(
                !field.optBoolean("required") ||
                    (value.isNotBlank() && (type != "checkbox" || value == "true"))
            ) {
                "$label is required"
            }
            if (value.isNotEmpty()) {
                if (type == "email" || name == "emailAddress")
                    require(Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                        "$label: enter a valid email address"
                    }
                if (name == "phoneNumber")
                    require(Regex("^\\+[1-9][0-9]{6,14}$").matches(value)) {
                        "$label: include the country code"
                    }
                if (type == "number")
                    require(value.toDoubleOrNull()?.isFinite() == true) {
                        "$label: enter a valid number"
                    }
                if (type == "date")
                    require(runCatching { LocalDate.parse(value) }.isSuccess) {
                        "$label: use YYYY-MM-DD"
                    }
                if (type in listOf("link", "url"))
                    require(
                        runCatching {
                                java.net.URI(value).let {
                                    it.scheme in listOf("http", "https") && !it.host.isNullOrBlank()
                                }
                            }
                            .getOrDefault(false)
                    ) {
                        "$label: enter a complete URL"
                    }
                if (type in listOf("list", "select")) {
                    val options = field.optJSONArray("values")
                    require(
                        options != null &&
                            (0 until options.length()).any { options.getString(it) == value }
                    ) {
                        "$label: choose an option"
                    }
                }
                val pattern =
                    field.optString("regex_pattern").takeUnless { it.isBlank() || it == "null" }
                if (pattern != null) {
                    var source = pattern
                    var options = emptySet<RegexOption>()
                    if (pattern.startsWith("/") && pattern.lastIndexOf('/') > 0) {
                        val end = pattern.lastIndexOf('/')
                        source = pattern.substring(1, end)
                        options = buildSet {
                            if ('i' in pattern.substring(end + 1)) add(RegexOption.IGNORE_CASE)
                            if ('m' in pattern.substring(end + 1)) add(RegexOption.MULTILINE)
                        }
                    }
                    require(Regex(source, options).containsMatchIn(value)) {
                        "$label: ${field.optString("regex_cue", "check the format")}"
                    }
                }
            }
            val typed: Any =
                when {
                    type == "checkbox" -> value == "true"
                    value.isBlank() -> JSONObject.NULL
                    type == "number" -> value.toDouble()
                    else -> value
                }
            when (field.optString("field_type")) {
                "contact_attribute" -> contactAttributes.put(name, typed)
                "conversation_attribute" -> conversationAttributes.put(name, typed)
                else ->
                    mapOf(
                            "fullName" to "name",
                            "emailAddress" to "email",
                            "phoneNumber" to "phone_number",
                        )[name]
                        ?.let { if (value.isNotBlank()) contact.put(it, value) }
            }
        }
        return JSONObject()
            .put("contact", contact.put("custom_attributes", contactAttributes))
            .put("custom_attributes", conversationAttributes)
            .put(
                "message",
                JSONObject()
                    .put("content", text.trim())
                    .put("timestamp", System.currentTimeMillis() / 1000),
            )
    }
}
