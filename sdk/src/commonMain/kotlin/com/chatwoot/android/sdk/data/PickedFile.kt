package com.chatwoot.android.sdk.data

/**
 * A file chosen by the contact, decoded into bytes in `commonMain`. The platform picker
 * (FileKit) produces these; [com.chatwoot.android.sdk.net.WidgetApi] uploads them as
 * `multipart/form-data`.
 */
internal data class PickedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val attachmentType: AttachmentType
        get() = when {
            mimeType.startsWith("image/") -> AttachmentType.Image
            mimeType.startsWith("video/") -> AttachmentType.Video
            mimeType.startsWith("audio/") -> AttachmentType.Audio
            else -> AttachmentType.File
        }

    // Data classes with array members need explicit equals/hashCode for value semantics.
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = (name.hashCode() * 31 + mimeType.hashCode()) * 31 + bytes.contentHashCode()
}

/** Best-effort MIME from a file extension; the server re-derives its own from the bytes. */
internal fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "webm" -> "video/webm"
    "mp3" -> "audio/mpeg"
    "m4a", "aac" -> "audio/mp4"
    "wav" -> "audio/wav"
    "ogg", "oga" -> "audio/ogg"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
}
