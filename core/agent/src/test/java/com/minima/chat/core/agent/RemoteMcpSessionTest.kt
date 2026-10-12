package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.McpServerConfig
import com.minima.chat.core.agent.mcp.McpSessionStore
import com.minima.chat.core.agent.mcp.RemoteMcpClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * JVM: RemoteMcpClient captures Mcp-Session-Id and reuses it on the next request.
 */
class RemoteMcpSessionTest {
    private lateinit var server: MockWebServer
    private lateinit var client: RemoteMcpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        client = RemoteMcpClient(okHttpClient = http)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mcpServer(headers: Map<String, String> = emptyMap()) = McpServerConfig(
        name = "mock",
        url = server.url("/mcp").toString(),
        enabled = true,
        headers = headers,
    )

    @Test
    fun captureSession_thenAppliedOnNextRpc() {
        // initialize
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Mcp-Session-Id", "sess-42")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}"""),
        )
        // notifications/initialized
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        // tools/list — must receive Mcp-Session-Id
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Mcp-Session-Id", "sess-42")
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"ping","description":"p","inputSchema":{"type":"object"}}]}}"""),
        )

        val cfg = mcpServer()
        val result = client.listTools(cfg)
        assertThat(result.isSuccess).isTrue()
        assertThat(client.peekSession(cfg)).isEqualTo("sess-42")

        // Request 0 = initialize (no prior session)
        val initReq = server.takeRequest()
        assertThat(initReq.getHeader(McpSessionStore.SESSION_HEADER)).isNull()

        // Request 1 = notify (should have session from initialize)
        val notifyReq = server.takeRequest()
        assertThat(notifyReq.getHeader(McpSessionStore.SESSION_HEADER)).isEqualTo("sess-42")

        // Request 2 = tools/list
        val listReq = server.takeRequest()
        assertThat(listReq.getHeader(McpSessionStore.SESSION_HEADER)).isEqualTo("sess-42")
    }

    @Test
    fun userStaticSessionHeader_winsOverStored() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Mcp-Session-Id", "from-server")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}"""),
        )

        val cfg = mcpServer(headers = mapOf("Mcp-Session-Id" to "user-fixed"))
        assertThat(client.listTools(cfg).isSuccess).isTrue()

        server.takeRequest() // init — user header present
        val notifyReq = server.takeRequest()
        assertThat(notifyReq.getHeader(McpSessionStore.SESSION_HEADER)).isEqualTo("user-fixed")
        val listReq = server.takeRequest()
        assertThat(listReq.getHeader(McpSessionStore.SESSION_HEADER)).isEqualTo("user-fixed")
    }

    @Test
    fun clearSession_dropsStoredId() {
        val store = McpSessionStore()
        val http = OkHttpClient.Builder().build()
        val c = RemoteMcpClient(okHttpClient = http, sessions = store)
        val cfg = McpServerConfig("x", "https://example.com/mcp")
        store.put(cfg, "gone-soon")
        assertThat(c.peekSession(cfg)).isEqualTo("gone-soon")
        c.clearSession(cfg)
        assertThat(c.peekSession(cfg)).isNull()
    }

    @Test
    fun http404_clearsStoredSession() {
        // Seed a session via a prior successful initialize+list, then callTool gets 404.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Mcp-Session-Id", "stale")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Mcp-Session-Id", "stale")
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"ping","description":"","inputSchema":{"type":"object"}}]}}"""),
        )
        val cfg = mcpServer()
        assertThat(client.listTools(cfg).isSuccess).isTrue()
        assertThat(client.peekSession(cfg)).isEqualTo("stale")

        // Drain listTools requests
        repeat(3) { server.takeRequest() }

        server.enqueue(MockResponse().setResponseCode(404).setBody("session expired"))
        // withRetry will not retry 404 (not transient); session should clear
        val call = client.callTool(cfg, "ping", "{}")
        assertThat(call.isFailure).isTrue()
        assertThat(client.peekSession(cfg)).isNull()
    }

    @Test
    fun bearerToken_sentAsAuthorization() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}"""),
        )
        val cfg = McpServerConfig(
            name = "mock",
            url = server.url("/mcp").toString(),
            enabled = true,
            bearerToken = "mcp-secret-token-99",
            headers = mapOf("X-Extra" to "1"),
        )
        assertThat(client.listTools(cfg).isSuccess).isTrue()
        val initReq = server.takeRequest()
        assertThat(initReq.getHeader("Authorization")).isEqualTo("Bearer mcp-secret-token-99")
        assertThat(initReq.getHeader("X-Extra")).isEqualTo("1")
    }

    @Test
    fun customAuthorizationHeader_winsOverBearerField() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{},"serverInfo":{"name":"t","version":"1"}}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"jsonrpc":"2.0","id":2,"result":{"tools":[]}}"""),
        )
        val cfg = McpServerConfig(
            name = "mock",
            url = server.url("/mcp").toString(),
            bearerToken = "from-field-token",
            headers = mapOf("Authorization" to "Bearer from-headers-token"),
        )
        assertThat(client.listTools(cfg).isSuccess).isTrue()
        val initReq = server.takeRequest()
        assertThat(initReq.getHeader("Authorization")).isEqualTo("Bearer from-headers-token")
    }
}
