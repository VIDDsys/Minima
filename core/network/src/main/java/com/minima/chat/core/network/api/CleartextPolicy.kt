package com.minima.chat.core.network.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * App-level cleartext gate aligned with `res/xml/network_security_config.xml`:
 * cleartext off by default; only loopback / emulator host aliases.
 *
 * NSC alone fails obscurely at the socket layer — this returns a clear error
 * before OkHttp dials `http://evil.example`.
 */
object CleartextPolicy {
    /** Hosts permitted for `http://` (must match network_security_config domain-config). */
    val ALLOWED_CLEARTEXT_HOSTS: Set<String> = setOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2",
        "::1",
    )

    const val REJECT_MESSAGE =
        "Cleartext HTTP only allowed for localhost, 127.0.0.1, 10.0.2.2, or ::1 — use HTTPS"

    /**
     * Normalize like [ChatRequestBuilder.normalizeBaseUrl] (bare hosts → https),
     * then reject non-loopback `http://`.
     *
     * @return null if OK (empty, https, or allowed cleartext host); else error message.
     */
    fun rejectDisallowedCleartext(rawUrl: String): String? {
        var u = rawUrl.trim()
        if (u.isEmpty()) return null
        if (!u.startsWith("http://", ignoreCase = true) &&
            !u.startsWith("https://", ignoreCase = true)
        ) {
            u = "https://$u"
        }
        val httpUrl = u.toHttpUrlOrNull() ?: return "Invalid URL"
        if (httpUrl.isHttps) return null
        val host = httpUrl.host.lowercase()
        if (host in ALLOWED_CLEARTEXT_HOSTS) return null
        return REJECT_MESSAGE
    }

    fun isAllowedCleartextHost(host: String): Boolean =
        host.trim().lowercase().removePrefix("[").removeSuffix("]") in ALLOWED_CLEARTEXT_HOSTS
}
