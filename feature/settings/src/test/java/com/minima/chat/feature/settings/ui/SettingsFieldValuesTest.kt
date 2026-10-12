package com.minima.chat.feature.settings.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsFieldValuesTest {
    @Test
    fun apiKey_showsSavedWhenDraftNull() {
        assertThat(SettingsFieldValues.apiKeyValue(null, "sk-secret")).isEqualTo("sk-secret")
    }

    @Test
    fun apiKey_prefersDraftWhileEditing() {
        assertThat(SettingsFieldValues.apiKeyValue("sk-new", "sk-old")).isEqualTo("sk-new")
    }

    @Test
    fun apiKey_emptySavedShowsEmpty() {
        assertThat(SettingsFieldValues.apiKeyValue(null, "")).isEqualTo("")
    }

    @Test
    fun baseUrl_keepsSaved() {
        assertThat(
            SettingsFieldValues.textOrSaved(null, "https://api.deepseek.com/v1"),
        ).isEqualTo("https://api.deepseek.com/v1")
    }
}
