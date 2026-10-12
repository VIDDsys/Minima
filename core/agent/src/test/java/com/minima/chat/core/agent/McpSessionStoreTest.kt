package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.McpServerConfig
import com.minima.chat.core.agent.mcp.McpSessionStore
import okhttp3.Headers
import org.junit.Test

class McpSessionStoreTest {

    private val server = McpServerConfig(
        name = "Demo",
        url = "https://mcp.example.com/mcp/",
        enabled = true,
    )

    @Test
    fun sessionKey_normalizesNameAndTrailingSlash() {
        val a = McpServerConfig("Demo", "https://mcp.example.com/mcp/")
        val b = McpServerConfig("demo", "https://mcp.example.com/mcp")
        assertThat(McpSessionStore.companionKey(a)).isEqualTo(McpSessionStore.companionKey(b))
    }

    @Test
    fun putGetClear_roundTrip() {
        val store = McpSessionStore()
        store.put(server, "sess-abc")
        assertThat(store.get(server)).isEqualTo("sess-abc")
        store.clear(server)
        assertThat(store.get(server)).isNull()
    }

    @Test
    fun captureFrom_caseInsensitiveHeader() {
        val store = McpSessionStore()
        val headers = Headers.Builder()
            .add("mcp-session-id", "  id-from-lower  ")
            .build()
        store.captureFrom(server, headers)
        assertThat(store.get(server)).isEqualTo("id-from-lower")
    }

    @Test
    fun captureFrom_emptyHeader_noop() {
        val store = McpSessionStore()
        store.put(server, "keep-me")
        store.captureFrom(server, Headers.Builder().add("Mcp-Session-Id", "   ").build())
        assertThat(store.get(server)).isEqualTo("keep-me")
    }

    @Test
    fun mergeRequestHeaders_appliesStoredWhenUserOmits() {
        val merged = McpSessionStore.mergeRequestHeaders(
            storedSessionId = "auto-1",
            userHeaders = mapOf("Authorization" to "Bearer t"),
        )
        assertThat(merged[McpSessionStore.SESSION_HEADER]).isEqualTo("auto-1")
        assertThat(merged["Authorization"]).isEqualTo("Bearer t")
    }

    @Test
    fun mergeRequestHeaders_userStaticWins() {
        val merged = McpSessionStore.mergeRequestHeaders(
            storedSessionId = "auto-1",
            userHeaders = mapOf(
                "mcp-session-id" to "user-fixed",
                "Authorization" to "Bearer t",
            ),
        )
        assertThat(merged["mcp-session-id"]).isEqualTo("user-fixed")
        assertThat(merged.containsKey(McpSessionStore.SESSION_HEADER)).isFalse()
    }

    @Test
    fun mergeRequestHeaders_noStored_noSessionHeader() {
        val merged = McpSessionStore.mergeRequestHeaders(
            storedSessionId = null,
            userHeaders = mapOf("X-Custom" to "1"),
        )
        assertThat(merged.containsKey(McpSessionStore.SESSION_HEADER)).isFalse()
        assertThat(merged["X-Custom"]).isEqualTo("1")
    }

    @Test
    fun extractSessionId_fromHeaders() {
        val h = Headers.Builder().add("Mcp-Session-Id", "xyz").build()
        assertThat(McpSessionStore.extractSessionId(h)).isEqualTo("xyz")
        assertThat(McpSessionStore.extractSessionId(Headers.Builder().build())).isNull()
    }
}
