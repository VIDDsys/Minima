package com.minima.chat.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Documents v2.8 navigation contract: open chat pops up to Sessions
 * so only one Chat VM / AgentRunController bind is live.
 */
class ChatNavContractTest {
    @Test
    fun chatRouteIncludesSessionId() {
        assertThat(Routes.chat("abc-123")).isEqualTo("chat/abc-123")
        assertThat(Routes.Chat).isEqualTo("chat/{sessionId}")
    }

    @Test
    fun sessionsIsStablePopTarget() {
        assertThat(Routes.Sessions).isEqualTo("sessions")
    }
}
