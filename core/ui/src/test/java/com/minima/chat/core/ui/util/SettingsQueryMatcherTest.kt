package com.minima.chat.core.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsQueryMatcherTest {

    private val provider = arrayOf(
        "provider", "url", "base", "model", "api", "key", "openai", "openrouter",
        "deepseek", "preset", "check", "endpoint", "reach", "refresh", "models", "list",
    )
    private val agent = arrayOf(
        "agent", "tool", "sandbox", "steps", "schedule", "daily", "prompt", "system",
        "agent.md", "builtin", "inventory", "mcp",
    )
    private val appearance = arrayOf(
        "appearance", "theme", "dark", "light", "system", "usage", "token", "estimate", "display",
    )

    @Test
    fun emptyQuery_matchesEverything() {
        assertThat(SettingsQueryMatcher.matches("", *provider)).isTrue()
        assertThat(SettingsQueryMatcher.matches("   ", *agent)).isTrue()
    }

    @Test
    fun fragmentOfKeyword_matches() {
        assertThat(SettingsQueryMatcher.matches("sand", *agent)).isTrue()
        assertThat(SettingsQueryMatcher.matches("url", *provider)).isTrue()
        assertThat(SettingsQueryMatcher.matches("base", *provider)).isTrue()
        assertThat(SettingsQueryMatcher.matches("api", *provider)).isTrue()
    }

    @Test
    fun queryContainingKeyword_matchesWhenLongEnough() {
        assertThat(SettingsQueryMatcher.matches("my sandbox folder", *agent)).isTrue()
        assertThat(SettingsQueryMatcher.matches("endpoint check", *provider)).isTrue()
    }

    @Test
    fun unrelated_doesNotMatch() {
        assertThat(SettingsQueryMatcher.matches("zzzz", *provider)).isFalse()
        assertThat(SettingsQueryMatcher.matches("billing", *appearance)).isFalse()
    }

    @Test
    fun caseInsensitive() {
        assertThat(SettingsQueryMatcher.matches("SANDBOX", *agent)).isTrue()
        assertThat(SettingsQueryMatcher.matches("Theme", *appearance)).isTrue()
    }

    @Test
    fun anyGroupVisible_falseWhenNothingMatches() {
        val groups = listOf(provider, agent, appearance)
        assertThat(SettingsQueryMatcher.anyGroupVisible("zzzz-nope", groups)).isFalse()
        assertThat(SettingsQueryMatcher.anyGroupVisible("sandbox", groups)).isTrue()
        assertThat(SettingsQueryMatcher.anyGroupVisible("", groups)).isTrue()
    }
}
