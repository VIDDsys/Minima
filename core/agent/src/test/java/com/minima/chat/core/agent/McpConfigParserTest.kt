package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.McpConfigParser
import org.junit.Test

class McpConfigParserTest {
    @Test
    fun parsesServers() {
        val cfg = McpConfigParser.parse(
            """{"servers":[{"name":"demo","url":"https://mcp.example/sse","enabled":true}]}""",
        )
        assertThat(cfg.servers).hasSize(1)
        assertThat(cfg.servers[0].name).isEqualTo("demo")
    }

    @Test
    fun badJsonReturnsEmpty() {
        assertThat(McpConfigParser.parse("not-json").servers).isEmpty()
    }

    @Test
    fun parsesBearerAndHeaders() {
        val cfg = McpConfigParser.parse(
            """{"servers":[{"name":"demo","url":"https://mcp.example/sse","bearerToken":"tok","headers":{"Authorization":"Bearer override"}}]}""",
        )
        assertThat(cfg.servers[0].bearerToken).isEqualTo("tok")
        assertThat(cfg.servers[0].headers["Authorization"]).isEqualTo("Bearer override")
    }

    @Test
    fun encodeRoundTrip() {
        val file = McpConfigParser.parse(
            """{"servers":[{"name":"a","url":"https://x","bearerToken":"b","headers":{"X":"1"}}]}""",
        )
        val encoded = McpConfigParser.encode(file)
        val again = McpConfigParser.parse(encoded)
        assertThat(again.servers[0].bearerToken).isEqualTo("b")
        assertThat(again.servers[0].headers["X"]).isEqualTo("1")
    }
}
