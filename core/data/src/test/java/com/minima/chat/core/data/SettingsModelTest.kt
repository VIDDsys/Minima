package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.AppSettings
import com.minima.chat.core.data.model.AppearanceSetting
import org.junit.Test

class SettingsModelTest {
    @Test
    fun hasApiKey_falseWhenBlank() {
        assertThat(AppSettings(apiKey = "").hasApiKey).isFalse()
        assertThat(AppSettings(apiKey = "  ").hasApiKey).isFalse()
    }

    @Test
    fun hasApiKey_trueWhenSet() {
        assertThat(AppSettings(apiKey = "sk-abc").hasApiKey).isTrue()
    }

    @Test
    fun defaults_areSensible() {
        val s = AppSettings()
        assertThat(s.baseUrl).contains("deepseek")
        assertThat(s.modelId).isNotEmpty()
        assertThat(s.appearance).isEqualTo(AppearanceSetting.System)
        assertThat(s.scheduledNoteEnabled).isFalse()
        assertThat(s.agentEnabled).isTrue()
        assertThat(s.sandboxOnly).isFalse()
        assertThat(s.maxAgentSteps).isEqualTo(10_000)
        assertThat(s.onboardingCompleted).isFalse()
        assertThat(s.systemPrompt).isEmpty()
    }
}

