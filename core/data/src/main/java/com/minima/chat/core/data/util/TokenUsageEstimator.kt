package com.minima.chat.core.data.util

/**
 * Token display helpers: prefer provider-billed usage when persisted on messages;
 * otherwise a cheap ~chars/4 estimate (not billed).
 */
object TokenUsageEstimator {
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length + 3) / 4
    }

    fun estimateSession(texts: Iterable<String>): Int =
        texts.sumOf { estimateTokens(it) }

    fun formatEstimate(tokens: Int): String = when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
        else -> tokens.toString()
    }

    fun subtitle(tokens: Int): String = "~${formatEstimate(tokens)} est. tokens"

    fun subtitleBilled(promptTokens: Int, completionTokens: Int): String {
        val total = (promptTokens + completionTokens).coerceAtLeast(0)
        return "${formatEstimate(total)} tokens · ${formatEstimate(promptTokens)}↑ ${formatEstimate(completionTokens)}↓"
    }

    /**
     * If any message has persisted provider tokens, sum those; else fall back to chars/4.
     */
    fun sessionSubtitle(
        billedPairs: List<Pair<Int, Int>>,
        textsForEstimate: List<String>,
    ): String {
        val inSum = billedPairs.sumOf { it.first.coerceAtLeast(0) }
        val outSum = billedPairs.sumOf { it.second.coerceAtLeast(0) }
        if (inSum > 0 || outSum > 0) {
            return subtitleBilled(inSum, outSum)
        }
        return subtitle(estimateSession(textsForEstimate))
    }
}
