package com.minima.chat.core.network.api

/**
 * Redact likely secrets from diagnostic / share / clipboard strings.
 * Single source of truth — SessionTranscript, reachability, UI previews all use this.
 */
object SecretRedact {
    /**
     * Patterns cover OpenAI-ish keys, Bearer headers, query tokens, GitHub/Slack/Google/AWS,
     * Anthropic, PEM private keys, and compact JWTs. Prefer over-redact for share paths.
     */
    private val SECRET_REGEX = Regex(
        buildString {
            append("""(?i)(""")
            // OpenAI / project / Anthropic-style
            append("""sk-ant-[A-Za-z0-9_\-]{8,}|""")
            append("""sk-proj-[A-Za-z0-9_\-]{8,}|""")
            append("""sk-[A-Za-z0-9_\-]{8,}|""")
            // Auth headers
            append("""Authorization:\s*Bearer\s+[A-Za-z0-9._\-+/=]{8,}|""")
            append("""Bearer\s+[A-Za-z0-9._\-+/=]{8,}|""")
            // Assignment / JSON-ish
            append("""api[_-]?key["\s:=]+[A-Za-z0-9._\-]{8,}|""")
            append("""(access[_-]?token|refresh[_-]?token|client[_-]?secret)["\s:=]+[A-Za-z0-9._\-]{8,}|""")
            // Query params
            append("""[?&](api[_-]?key|key|token|access_token|secret|password)=[^&\s"'<>]{8,}|""")
            // GitHub
            append("""gh[pousr]_[A-Za-z0-9_]{20,}|""")
            append("""github_pat_[A-Za-z0-9_]{20,}|""")
            // Slack
            append("""xox[baprs]-[A-Za-z0-9-]{10,}|""")
            // Google API keys
            append("""AIza[0-9A-Za-z\-_]{20,}|""")
            // AWS access key id
            append("""AKIA[0-9A-Z]{16}|""")
            // Compact JWT (three base64url segments)
            append("""eyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}|""")
            // PEM private key block (single-line / start marker)
            append("""-----BEGIN[A-Z ]*PRIVATE KEY-----""")
            append(""")""")
        },
    )

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return SECRET_REGEX.replace(text) { m ->
            val raw = m.value
            when {
                raw.length <= 8 -> "***"
                raw.startsWith("?") || raw.startsWith("&") ->
                    raw.substringBefore('=') + "=***"
                raw.startsWith("-----BEGIN", ignoreCase = true) ->
                    "-----BEGIN PRIVATE KEY-----***"
                else -> raw.take(3) + "…" + "***"
            }
        }
    }

    /** True when [text] matches a secret pattern (anywhere, not only whole-string). */
    fun containsSecret(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return SECRET_REGEX.containsMatchIn(text)
    }
}
