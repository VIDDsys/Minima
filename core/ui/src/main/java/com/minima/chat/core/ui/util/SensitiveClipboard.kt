package com.minima.chat.core.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.minima.chat.core.network.api.SecretRedact

/**
 * Clipboard helpers that avoid leaking secrets into logs / plain ClipData labels.
 * Redaction delegates to [SecretRedact] (single source of truth).
 */
object SensitiveClipboard {
    /** Redact likely secret tokens for any diagnostic string. */
    fun redact(text: String?): String = SecretRedact.redact(text)

    /**
     * True when [text] embeds a secret pattern anywhere (share / transcript gates).
     * Prefer this over [looksLikeApiKey] for multi-line transcripts.
     */
    fun containsSecret(text: String?): Boolean = SecretRedact.containsSecret(text)

    /**
     * Whole-string API-key shape (composer "don't copy key" UX).
     * Not sufficient for transcripts — use [containsSecret].
     */
    fun looksLikeApiKey(text: String): Boolean {
        val t = text.trim()
        if (t.length < 12) return false
        return t.startsWith("sk-", ignoreCase = true) ||
            t.startsWith("sk-proj-", ignoreCase = true) ||
            t.startsWith("sk-ant-", ignoreCase = true) ||
            t.startsWith("key-") ||
            t.startsWith("ghp_") ||
            t.startsWith("github_pat_") ||
            (t.length >= 32 && t.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    /**
     * Copy text marked sensitive on API 33+ so the system avoids surfacing it in overlays.
     * Prefer not copying API keys at all; this is for code blocks / non-secret text.
     */
    fun copyPlain(context: Context, label: String, text: String, sensitive: Boolean = false) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
    }

    /** Never copy API keys — no-op with false. */
    fun copyApiKeyBlocked(): Boolean = false
}
