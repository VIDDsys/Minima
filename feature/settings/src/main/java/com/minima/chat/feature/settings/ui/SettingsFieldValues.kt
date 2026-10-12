package com.minima.chat.feature.settings.ui

/**
 * How settings text fields choose what to show.
 * API key must keep showing the saved value (as dots) after re-entry / restart.
 */
object SettingsFieldValues {
    fun textOrSaved(draft: String?, saved: String): String = draft ?: saved

    /** Primary provider fields: blank draft means "show saved / default", never force-empty. */
    fun apiKeyValue(draft: String?, savedApiKey: String): String = textOrSaved(draft, savedApiKey)
}
