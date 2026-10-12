package com.minima.chat.core.network.api

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Human-readable HTTP / transport errors for chat + agent surfaces.
 * Always redacts likely secrets from provider error bodies.
 */
object FriendlyHttp {

    fun message(code: Int, body: String): String {
        val detail = extractApiMessage(body)
            ?.let { SecretRedact.redact(it).trim() }
            ?.take(160)
            ?.takeIf { it.isNotBlank() }
        return when (code) {
            401 -> withDetail("Unauthorized (401) — check API key", detail)
            403 -> withDetail("Forbidden (403) — access denied", detail)
            404 -> withDetail("Not found (404) — check base URL / path", detail)
            408 -> withDetail("Request timeout (408)", detail)
            413 -> withDetail("Payload too large (413)", detail)
            429 -> withDetail("Rate limited (429) — wait and retry", detail)
            in 500..599 -> withDetail("Server error ($code)", detail)
            else -> {
                val snippet = detail
                    ?: SecretRedact.redact(body).trim().take(200).takeIf { it.isNotBlank() }
                if (snippet != null) "HTTP $code: $snippet" else "HTTP $code"
            }
        }
    }

    fun fromThrowable(t: Throwable): String {
        val chain = generateSequence(t) { it.cause }.toList()
        for (c in chain) {
            when (c) {
                is UnknownHostException ->
                    return "Cannot resolve host — check base URL / network"
                is SocketTimeoutException ->
                    return "Connection timed out — try again"
                is SSLHandshakeException, is CertificateException ->
                    return "TLS/SSL failed — check HTTPS base URL"
                is SSLException ->
                    return "TLS/SSL error — check HTTPS base URL"
                is ConnectException ->
                    return "Cannot connect — is the server up?"
            }
        }
        val raw = t.message?.trim().orEmpty()
        val redacted = SecretRedact.redact(raw).take(200).trim()
        return redacted.ifBlank { "Network error" }
    }

    /**
     * Pulls OpenAI-style `error.message`, string `error`, or top-level `message`
     * from a JSON (or JSON-ish) error body without requiring a full decode.
     */
    fun extractApiMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        // Prefer nested error.message
        nestedMessageRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { raw ->
            unescapeJsonString(raw).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        // error: "string"
        errorStringRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { raw ->
            unescapeJsonString(raw).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        // top-level message (avoid matching nested again — same pattern is fine)
        topMessageRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { raw ->
            unescapeJsonString(raw).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun withDetail(base: String, detail: String?): String {
        if (detail.isNullOrBlank()) return base
        // Skip if detail is redundant with the base phrase
        val head = detail.take(48)
        if (base.contains(head, ignoreCase = true)) return base
        return "$base — $detail"
    }

    private fun unescapeJsonString(raw: String): String =
        raw.replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
            .replace("\\/", "/")

    private val nestedMessageRegex =
        Regex(""""error"\s*:\s*\{[^{}]*?"message"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val errorStringRegex =
        Regex(""""error"\s*:\s*"((?:\\.|[^"\\])*)"""")
    private val topMessageRegex =
        Regex(""""message"\s*:\s*"((?:\\.|[^"\\])*)"""")
}
