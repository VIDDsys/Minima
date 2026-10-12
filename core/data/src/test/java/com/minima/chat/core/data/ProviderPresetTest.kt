package com.minima.chat.core.data

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.data.model.ProviderPreset
import org.junit.Test

class ProviderPresetTest {
    @Test
    fun matchOpenAi() {
        assertThat(ProviderPreset.match("https://api.openai.com/v1")).isEqualTo(ProviderPreset.OpenAI)
        assertThat(ProviderPreset.match("https://api.openai.com/v1/")).isEqualTo(ProviderPreset.OpenAI)
    }

    @Test
    fun matchOpenRouterAndDeepSeek() {
        assertThat(ProviderPreset.match("https://openrouter.ai/api/v1")).isEqualTo(ProviderPreset.OpenRouter)
        assertThat(ProviderPreset.match("https://api.deepseek.com/v1")).isEqualTo(ProviderPreset.DeepSeek)
    }

    @Test
    fun matchOllamaLocal() {
        assertThat(ProviderPreset.match("http://127.0.0.1:11434/v1")).isEqualTo(ProviderPreset.Ollama)
        assertThat(ProviderPreset.match("http://127.0.0.1:11434/v1/")).isEqualTo(ProviderPreset.Ollama)
        assertThat(ProviderPreset.Ollama.fillsFields).isTrue()
        assertThat(ProviderPreset.Ollama.modelHint).isEqualTo("llama3.2")
    }

    @Test
    fun matchCustom() {
        assertThat(ProviderPreset.match("https://my.gateway.local/v1")).isEqualTo(ProviderPreset.Custom)
        assertThat(ProviderPreset.match("")).isEqualTo(ProviderPreset.Custom)
    }

    @Test
    fun customDoesNotFill() {
        assertThat(ProviderPreset.Custom.fillsFields).isFalse()
        assertThat(ProviderPreset.OpenAI.fillsFields).isTrue()
    }
}
