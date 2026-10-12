package com.minima.chat

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deepen TCG soak: onboarding → sessions → settings → back → new chat.
 * Uses existing contentDescriptions; generous timeouts for software accel.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SessionSettingsSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingSkip_sessions_settings_newChat() {
        // Hydrate + first frame under TCG can be slow.
        composeRule.waitUntil(timeoutMillis = 120_000) {
            hasCd("Skip onboarding") || hasCd("Settings") || hasCd("Onboarding")
        }

        if (hasCd("Skip onboarding")) {
            composeRule.onNodeWithContentDescription("Skip onboarding").performClick()
        }

        composeRule.waitUntil(timeoutMillis = 120_000) {
            hasCd("Settings") && (hasCd("Search sessions") || hasCd("New chat") ||
                hasText("No conversations yet") || hasText("Minima"))
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.waitUntil(timeoutMillis = 120_000) {
            hasText("设置") && hasCd("Base URL") && hasCd("API key")
        }
        composeRule.onNodeWithContentDescription("Base URL").assertExists()
        composeRule.onNodeWithContentDescription("Model id").assertExists()
        composeRule.onNodeWithContentDescription("API key").assertExists()
        composeRule.onNodeWithContentDescription("Filter settings").assertExists()

        // Back may be duplicated (IconButton + Icon); click the first matched node.
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()

        composeRule.waitUntil(timeoutMillis = 120_000) {
            hasCd("New chat") || hasCd("Settings")
        }

        // FAB + empty-state secondary both expose "New chat" under TCG empty DB.
        composeRule.onAllNodesWithContentDescription("New chat")[0].performClick()

        composeRule.waitUntil(timeoutMillis = 120_000) {
            hasCd("Back") // chat top bar
        }
        // Still alive in chat; go back to sessions.
        composeRule.onAllNodesWithContentDescription("Back")[0].performClick()

        composeRule.waitUntil(timeoutMillis = 120_000) {
            hasCd("New chat") || hasCd("Search sessions")
        }
    }

    private fun hasCd(desc: String): Boolean =
        composeRule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty()

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
}
