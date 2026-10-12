package com.minima.chat.core.network.api

import okhttp3.CertificatePinner

/**
 * Optional certificate pinning. **Off by default** (blank config).
 *
 * Format (one pin per line or comma-separated):
 * ```
 * api.openai.com|sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
 * ```
 * Host may include `*.` wildcards per OkHttp rules. Invalid lines are skipped.
 */
object CertificatePinConfig {
    data class Pin(val host: String, val pin: String)

    fun parse(raw: String?): List<Pin> {
        if (raw.isNullOrBlank()) return emptyList()
        val parts = raw.split('\n', ',', ';')
        val out = mutableListOf<Pin>()
        for (part in parts) {
            val line = part.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val sep = when {
                line.contains('|') -> '|'
                line.contains('=') && !line.contains("sha256/") -> '='
                else -> null
            }
            val (host, pinRaw) = if (sep != null) {
                val i = line.indexOf(sep)
                line.substring(0, i).trim() to line.substring(i + 1).trim()
            } else continue
            if (host.isBlank() || pinRaw.isBlank()) continue
            val pin = if (pinRaw.startsWith("sha256/", ignoreCase = true)) pinRaw else "sha256/$pinRaw"
            // OkHttp expects base64 after sha256/
            if (pin.length < 16) continue
            out += Pin(host, pin)
        }
        return out
    }

    /** null when pinning should stay off. */
    fun buildPinner(raw: String?): CertificatePinner? {
        val pins = parse(raw)
        if (pins.isEmpty()) return null
        val b = CertificatePinner.Builder()
        for (p in pins) {
            runCatching { b.add(p.host, p.pin) }
        }
        return runCatching { b.build() }.getOrNull()
    }

    fun isEnabled(raw: String?): Boolean = parse(raw).isNotEmpty()
}
