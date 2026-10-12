package com.minima.chat.core.network.api

/**
 * Pure clamps / parsers for OpenAI-compatible sampling knobs.
 * Blank / unset → omit from the request (provider default).
 *
 * Lives in :core:network so [MultimodalContent] can sanitize at the encode
 * boundary without depending on :core:data.
 */
object SamplingParams {
    const val TEMP_MIN = 0.0
    const val TEMP_MAX = 2.0
    const val TOP_P_MIN = 0.0
    const val TOP_P_MAX = 1.0
    const val MAX_TOKENS_MIN = 1
    const val MAX_TOKENS_MAX = 200_000

    /**
     * Empty / blank / non-numeric / NaN / Inf → null (omit).
     * Accepts a single comma as decimal separator when no period is present
     * (IME [KeyboardType.Decimal] on some locales inserts `,`).
     * Otherwise clamp to [TEMP_MIN, TEMP_MAX].
     */
    fun parseTemperature(raw: String?): Double? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val normalized = normalizeDecimal(t) ?: return null
        val v = normalized.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return v.coerceIn(TEMP_MIN, TEMP_MAX)
    }

    /**
     * nucleus sampling top_p. Empty / blank / non-numeric / NaN / Inf → null (omit).
     * Same comma-decimal rules as temperature. Clamp to [TOP_P_MIN, TOP_P_MAX].
     */
    fun parseTopP(raw: String?): Double? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val normalized = normalizeDecimal(t) ?: return null
        val v = normalized.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return v.coerceIn(TOP_P_MIN, TOP_P_MAX)
    }

    /**
     * Empty / blank / "0" / non-positive → null (omit).
     * Accepts plain ints and decimal/scientific forms ("4096.0", "2e3") via truncating
     * toward zero, then clamp to [MAX_TOKENS_MIN, MAX_TOKENS_MAX].
     */
    fun parseMaxTokens(raw: String?): Int? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        t.toIntOrNull()?.let { v ->
            if (v <= 0) return null
            return v.coerceIn(MAX_TOKENS_MIN, MAX_TOKENS_MAX)
        }
        val normalized = normalizeDecimal(t) ?: return null
        val d = normalized.toDoubleOrNull() ?: return null
        if (d.isNaN() || d.isInfinite() || d <= 0.0) return null
        // Truncate toward zero; reject values that collapse below MIN after trunc.
        val asLong = d.toLong()
        if (asLong < MAX_TOKENS_MIN.toLong()) return null
        val capped = asLong.coerceAtMost(MAX_TOKENS_MAX.toLong())
        return capped.toInt()
    }

    fun clampTemperature(v: Double?): Double? {
        if (v == null) return null
        if (v.isNaN() || v.isInfinite()) return null
        return v.coerceIn(TEMP_MIN, TEMP_MAX)
    }

    fun clampMaxTokens(v: Int?): Int? {
        if (v == null || v <= 0) return null
        return v.coerceIn(MAX_TOKENS_MIN, MAX_TOKENS_MAX)
    }

    fun clampTopP(v: Double?): Double? {
        if (v == null) return null
        if (v.isNaN() || v.isInfinite()) return null
        return v.coerceIn(TOP_P_MIN, TOP_P_MAX)
    }

    fun formatTemperature(v: Double?): String =
        v?.let { trimTrailingZeros(it) }.orEmpty()

    fun formatTopP(v: Double?): String =
        v?.let { trimTrailingZeros(it) }.orEmpty()

    fun formatMaxTokens(v: Int?): String =
        v?.takeIf { it > 0 }?.toString().orEmpty()

    /**
     * Live draft filter for Temperature / Top-p fields.
     * Keeps digits and at most one decimal separator (`.` or `,` for locale IMEs).
     */
    fun filterDecimalDraft(raw: String, maxLen: Int = 6): String {
        val filtered = buildString {
            var sep = false
            for (c in raw) {
                when {
                    c.isDigit() -> append(c)
                    (c == '.' || c == ',') && !sep -> {
                        append(c)
                        sep = true
                    }
                }
            }
        }
        return filtered.take(maxLen)
    }

    /**
     * Single comma and no period → treat comma as decimal point.
     * Multiple commas / mixed junk left for toDoubleOrNull to reject.
     */
    internal fun normalizeDecimal(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.count { it == ',' } == 1 && !t.contains('.')) {
            return t.replace(',', '.')
        }
        return t
    }

    private fun trimTrailingZeros(d: Double): String {
        // Range is [0,2] so toString stays non-scientific for normal values.
        return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }
}
