package com.minima.chat.core.agent

import com.minima.chat.core.agent.tools.ToolRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolInventoryTest {
    @Test
    fun summaryCounts() {
        val inv = ToolRegistry.ToolInventory(
            builtin = listOf("now", "http_get"),
            mcp = listOf("mcp__demo__search"),
        )
        assertThat(inv.builtinCount).isEqualTo(2)
        assertThat(inv.mcpCount).isEqualTo(1)
        assertThat(inv.summary).isEqualTo("Built-in 2 · MCP 1")
    }

    @Test
    fun emptyMcp() {
        val inv = ToolRegistry.ToolInventory(builtin = listOf("now"), mcp = emptyList())
        assertThat(inv.summary).isEqualTo("Built-in 1 · MCP 0")
    }

    @Test
    fun withHttp() {
        val inv = ToolRegistry.ToolInventory(
            builtin = listOf("now"),
            mcp = emptyList(),
            http = listOf("httpbin_get"),
        )
        assertThat(inv.summary).isEqualTo("Built-in 1 · HTTP 1 · MCP 0")
    }
}
