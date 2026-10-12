package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.McpConfigParser
import org.junit.Test

class McpConfigEnabledTest {
    @Test
    fun respectsEnabledFalse() {
        val cfg = McpConfigParser.parse(
            """{"servers":[{"name":"a","url":"https://x","enabled":false},{"name":"b","url":"https://y"}]}""",
        )
        assertThat(cfg.servers).hasSize(2)
        assertThat(cfg.servers[0].enabled).isFalse()
        assertThat(cfg.servers[1].enabled).isTrue()
    }
}
