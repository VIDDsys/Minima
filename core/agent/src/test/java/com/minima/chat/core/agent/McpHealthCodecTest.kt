package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.McpHealthCodec
import com.minima.chat.core.agent.mcp.McpServerHealth
import org.junit.Test

class McpHealthCodecTest {
    @Test
    fun roundTrip_preservesPerServerFields() {
        val list = listOf(
            McpServerHealth("a", "https://a", ok = true, toolCount = 3, message = "3 tools", checkedAtMs = 100L),
            McpServerHealth("b", "https://b", ok = false, toolCount = 0, message = "timeout", checkedAtMs = 200L),
        )
        val raw = McpHealthCodec.encode(list)
        val back = McpHealthCodec.decode(raw)
        assertThat(back).hasSize(2)
        assertThat(back[0].name).isEqualTo("a")
        assertThat(back[0].ok).isTrue()
        assertThat(back[0].toolCount).isEqualTo(3)
        assertThat(back[0].checkedAtMs).isEqualTo(100L)
        assertThat(back[1].ok).isFalse()
        assertThat(back[1].message).isEqualTo("timeout")
    }

    @Test
    fun decode_blankOrGarbage_returnsEmpty() {
        assertThat(McpHealthCodec.decode("")).isEmpty()
        assertThat(McpHealthCodec.decode("not-json")).isEmpty()
        assertThat(McpHealthCodec.decode("[]")).isEmpty()
    }
}
