package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.McpAuthHeaders
import com.minima.chat.core.agent.mcp.McpConfigParser
import com.minima.chat.core.agent.mcp.McpServerConfig
import com.minima.chat.core.agent.mcp.McpSessionStore
import org.junit.Test

class McpAuthHeadersTest {
    @Test
    fun merge_bearerOnly_setsAuthorization() {
        val h = McpAuthHeaders.merge(bearerToken = "tok-abc12345", headers = emptyMap())
        assertThat(h["Authorization"]).isEqualTo("Bearer tok-abc12345")
    }

    @Test
    fun merge_bearerAlreadyPrefixed_notDoubled() {
        val h = McpAuthHeaders.merge(bearerToken = "Bearer already-set-token", headers = emptyMap())
        assertThat(h["Authorization"]).isEqualTo("Bearer already-set-token")
    }

    @Test
    fun merge_blankBearer_omitted() {
        val h = McpAuthHeaders.merge(bearerToken = "  ", headers = mapOf("X-A" to "1"))
        assertThat(h.containsKey("Authorization")).isFalse()
        assertThat(h["X-A"]).isEqualTo("1")
    }

    @Test
    fun merge_customAuthorization_winsOverBearer() {
        val h = McpAuthHeaders.merge(
            bearerToken = "from-field",
            headers = mapOf("Authorization" to "Bearer from-headers"),
        )
        assertThat(h["Authorization"]).isEqualTo("Bearer from-headers")
    }

    @Test
    fun merge_customHeaders_preservedWithBearer() {
        val h = McpAuthHeaders.merge(
            bearerToken = "secret-token-xx",
            headers = mapOf("X-Api-Key" to "k", "X-Trace" to "t"),
        )
        assertThat(h["Authorization"]).isEqualTo("Bearer secret-token-xx")
        assertThat(h["X-Api-Key"]).isEqualTo("k")
        assertThat(h["X-Trace"]).isEqualTo("t")
    }

    @Test
    fun effectiveUserHeaders_fromServer() {
        val server = McpServerConfig(
            name = "demo",
            url = "https://mcp.example/mcp",
            bearerToken = "btoken12345678",
            headers = mapOf("X-Custom" to "yes"),
        )
        val h = McpAuthHeaders.effectiveUserHeaders(server)
        assertThat(h["Authorization"]).isEqualTo("Bearer btoken12345678")
        assertThat(h["X-Custom"]).isEqualTo("yes")
    }

    @Test
    fun mergeForServer_sessionPlusBearer_userSessionWins() {
        val server = McpServerConfig(
            name = "s",
            url = "https://x/mcp",
            bearerToken = "tok-zzzzzzzz",
            headers = mapOf("Mcp-Session-Id" to "user-sess"),
        )
        val merged = McpSessionStore.mergeForServer(server, storedSessionId = "auto-sess")
        assertThat(merged["Mcp-Session-Id"]).isEqualTo("user-sess")
        assertThat(merged["Authorization"]).isEqualTo("Bearer tok-zzzzzzzz")
        // auto session must not appear under canonical key when user set session header
        assertThat(merged.values).doesNotContain("auto-sess")
    }

    @Test
    fun mergeForServer_appliesStoredSessionWhenUserOmits() {
        val server = McpServerConfig(
            name = "s",
            url = "https://x/mcp",
            bearerToken = "tok-yyyyyyyy",
        )
        val merged = McpSessionStore.mergeForServer(server, storedSessionId = "auto-9")
        assertThat(merged[McpSessionStore.SESSION_HEADER]).isEqualTo("auto-9")
        assertThat(merged["Authorization"]).isEqualTo("Bearer tok-yyyyyyyy")
    }

    @Test
    fun linesToHeaders_and_back() {
        val lines = "Authorization: Bearer hdr-token\nX-Foo: bar\n# comment\n\nBadLine\n:empty"
        val map = McpAuthHeaders.linesToHeaders(lines)
        assertThat(map["Authorization"]).isEqualTo("Bearer hdr-token")
        assertThat(map["X-Foo"]).isEqualTo("bar")
        assertThat(map.containsKey("")).isFalse()
        val round = McpAuthHeaders.linesToHeaders(McpAuthHeaders.headersToLines(map))
        assertThat(round).isEqualTo(map)
    }

    @Test
    fun redact_hidesBearerToken() {
        val raw = "MCP «demo»: HTTP 401: Authorization: Bearer sk-abcdefghijklmnopqrstuv leaked"
        val out = McpAuthHeaders.redact(raw)
        assertThat(out).doesNotContain("sk-abcdefghijklmnopqrstuv")
        assertThat(out).contains("***")
        assertThat(out).contains("HTTP 401")
    }

    @Test
    fun parse_bearerToken_fromJson() {
        val cfg = McpConfigParser.parse(
            """{"servers":[{"name":"a","url":"https://x","bearerToken":"my-secret-token","headers":{"X-A":"1"}}]}""",
        )
        assertThat(cfg.servers).hasSize(1)
        assertThat(cfg.servers[0].bearerToken).isEqualTo("my-secret-token")
        assertThat(cfg.servers[0].headers["X-A"]).isEqualTo("1")
    }

    @Test
    fun patchServerAuth_updatesBearerAndHeaders() {
        val raw = """{"servers":[{"name":"demo","url":"https://mcp.example/mcp","enabled":true}]}"""
        val patched = McpAuthHeaders.patchServerAuth(
            rawJson = raw,
            serverName = "demo",
            bearerToken = "new-token-abcdef",
            headers = mapOf("X-Env" to "prod"),
        )
        val cfg = McpConfigParser.parse(patched)
        assertThat(cfg.servers[0].bearerToken).isEqualTo("new-token-abcdef")
        assertThat(cfg.servers[0].headers["X-Env"]).isEqualTo("prod")
        assertThat(cfg.servers[0].url).isEqualTo("https://mcp.example/mcp")
    }

    @Test
    fun patchServerAuth_unknownServer_noop() {
        val raw = """{"servers":[{"name":"demo","url":"https://x"}]}"""
        val patched = McpAuthHeaders.patchServerAuth(raw, "other", bearerToken = "x")
        assertThat(patched).isEqualTo(raw)
    }
}
