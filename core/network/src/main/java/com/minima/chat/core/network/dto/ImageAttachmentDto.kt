package com.minima.chat.core.network.dto

/**
 * In-memory image for OpenAI-compatible multimodal `image_url` parts.
 * Not persisted as base64 in Room — only used when building the HTTP body.
 */
data class ImageAttachmentDto(
    val mimeType: String,
    val base64: String,
) {
    val dataUrl: String
        get() = "data:${mimeType.ifBlank { "image/jpeg" }};base64,$base64"

    fun isSendable(): Boolean =
        base64.isNotBlank() && base64.length <= MAX_BASE64_CHARS

    companion object {
        /** ~1.1MB raw ≈ 1.5MB base64 — keeps request bodies phone-friendly. */
        const val MAX_BASE64_CHARS = 1_500_000
    }
}
