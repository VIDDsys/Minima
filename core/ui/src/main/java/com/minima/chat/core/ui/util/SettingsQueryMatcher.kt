package com.minima.chat.core.ui.util

/**
 * Settings screen filter: show a group when the query is empty or matches any keyword.
 *
 * Matching is bidirectional for short tokens so "base" hits keyword "url" families when
 * "base" is listed, and typing a fragment of a keyword (e.g. "sand") still hits "sandbox".
 */
object SettingsQueryMatcher {
    fun matches(query: String, vararg keywords: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return keywords.any { raw ->
            val k = raw.lowercase()
            if (k.isEmpty()) return@any false
            k.contains(q) || (q.length >= 3 && q.contains(k))
        }
    }

    /** True when at least one group would render for [query]. */
    fun anyGroupVisible(query: String, groups: List<Array<out String>>): Boolean {
        if (query.trim().isEmpty()) return true
        return groups.any { matches(query, *it) }
    }
}
