package com.minima.chat.core.data.model

/**
 * Provider URL/model hints for Settings. User always pastes their own API key.
 * Custom leaves fields untouched when selected as a no-op chip.
 */
enum class ProviderPreset(
    val label: String,
    val baseUrl: String,
    val modelHint: String,
) {
    OpenAI(
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        modelHint = "gpt-4o-mini",
    ),
    OpenRouter(
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        modelHint = "openai/gpt-4o-mini",
    ),
    DeepSeek(
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        modelHint = "deepseek-flash",
    ),
    Ollama(
        label = "Ollama",
        baseUrl = "http://127.0.0.1:11434/v1",
        modelHint = "llama3.2",
    ),
    Custom(
        label = "自定义",
        baseUrl = "",
        modelHint = "",
    ),
    ;

    val fillsFields: Boolean get() = this != Custom

    companion object {
        fun match(baseUrl: String): ProviderPreset {
            val u = baseUrl.trim().lowercase().trimEnd('/')
            return entries.firstOrNull { it.fillsFields && u.startsWith(it.baseUrl.lowercase().trimEnd('/')) }
                ?: Custom
        }
    }
}
