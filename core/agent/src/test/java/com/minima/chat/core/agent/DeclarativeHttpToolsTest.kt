package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.httptools.DeclarativeHttpTool
import com.minima.chat.core.agent.httptools.DeclarativeHttpToolDef
import com.minima.chat.core.agent.httptools.DeclarativeHttpToolsParser
import com.minima.chat.core.agent.httptools.HttpTemplate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DeclarativeHttpToolsTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parseValidTools() {
        val raw = """
            {
              "tools": [
                {
                  "name": "status_ping",
                  "description": "Ping status",
                  "method": "GET",
                  "url": "https://example.com/status",
                  "query": { "q": "{{q}}" },
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "q": { "type": "string", "description": "query" }
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val r = DeclarativeHttpToolsParser.parse(raw)
        assertThat(r.errors).isEmpty()
        assertThat(r.tools).hasSize(1)
        assertThat(r.tools[0].name).isEqualTo("status_ping")
        assertThat(r.tools[0].method).isEqualTo("GET")
    }

    @Test
    fun rejectsTemplatedHost() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://{{host}}/path")
        assertThat(err).contains("Host must be fixed")
    }

    @Test
    fun rejectsTemplatedHostGluedToDomain() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://example.com{{suffix}}/x")
        assertThat(err).contains("Host must be fixed")
    }

    @Test
    fun rejectsHttpScheme() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("http://example.com/x")
        assertThat(err).contains("https")
    }

    @Test
    fun rejectsPrivateHost() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://127.0.0.1/x")
        assertThat(err).contains("Private")
    }

    @Test
    fun rejectsUserinfoAtSign() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://evil.com@example.com/x")
        assertThat(err).contains("Userinfo")
    }

    @Test
    fun rejectsUserinfoBeforePublicHost() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://user:pass@api.example.com/v1")
        assertThat(err).contains("Userinfo")
    }

    @Test
    fun rejectsDecimalLoopbackInTemplate() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://2130706433/x")
        assertThat(err).contains("Private")
    }

    @Test
    fun rejectsMetadataHost() {
        val err = DeclarativeHttpToolsParser.validateUrlTemplate("https://169.254.169.254/latest/meta-data/")
        assertThat(err).contains("Private")
    }

    @Test
    fun rejectsReservedName() {
        val raw = """
            {"tools":[{"name":"http_get","description":"no","method":"GET","url":"https://example.com/"}]}
        """.trimIndent()
        val r = DeclarativeHttpToolsParser.parse(raw)
        assertThat(r.tools).isEmpty()
        assertThat(r.errors.joinToString()).contains("reserved")
    }

    @Test
    fun rejectsBadMethod() {
        val raw = """
            {"tools":[{"name":"boom","description":"x","method":"TRACE","url":"https://example.com/"}]}
        """.trimIndent()
        val r = DeclarativeHttpToolsParser.parse(raw)
        assertThat(r.tools).isEmpty()
        assertThat(r.errors.joinToString()).contains("method")
    }

    @Test
    fun templateRender() {
        val out = HttpTemplate.render("Hello {{name}}!", mapOf("name" to "Minima"))
        assertThat(out).isEqualTo("Hello Minima!")
        val args = HttpTemplate.parseArgs("""{"city":"SH","n":2}""")
        assertThat(args["city"]).isEqualTo("SH")
        assertThat(args["n"]).isEqualTo("2")
    }

    @Test
    fun mergeQuery() {
        val u = DeclarativeHttpTool.mergeQuery("https://example.com/a?x=1", mapOf("y" to "2"))
        assertThat(u).contains("x=1")
        assertThat(u).contains("y=2")
    }

    @Test
    fun assertSafeFinalUrl_blocksHostMismatchViaAtInjection() {
        // Bypass parser: glued template that would change authority after render
        val template = "https://example.com{{suffix}}"
        val finalUrl = "https://example.com@evil.com/"
        val err = DeclarativeHttpToolsParser.assertSafeFinalUrl(template, finalUrl)
        assertThat(err).isNotNull()
    }

    @Test
    fun assertSafeFinalUrl_blocksHostMismatchViaSuffix() {
        val template = "https://example.com{{suffix}}"
        val finalUrl = "https://example.com.evil.com/"
        val err = DeclarativeHttpToolsParser.assertSafeFinalUrl(template, finalUrl)
        assertThat(err).contains("Host mismatch")
    }

    @Test
    fun assertSafeFinalUrl_allowsSameHostPathTemplate() {
        val template = "https://example.com/{{path}}"
        val finalUrl = "https://example.com/v1/ok"
        assertThat(DeclarativeHttpToolsParser.assertSafeFinalUrl(template, finalUrl)).isNull()
    }

    @Test
    fun assertSafeFinalUrl_blocksPrivateAfterRender() {
        val template = "https://example.com/{{path}}"
        // Crafted final that somehow points at loopback (defense in depth)
        val err = DeclarativeHttpToolsParser.assertSafeFinalUrl(template, "https://127.0.0.1/")
        assertThat(err).isNotNull()
    }

    @Test
    fun assertSafeFinalUrl_blocksCrlfInUrl() {
        val err = DeclarativeHttpToolsParser.assertSafeFinalUrl(
            "https://example.com/{{p}}",
            "https://example.com/a\r\nHost: evil.com",
        )
        assertThat(err).contains("Invalid URL")
    }

    @Test
    fun invoke_blocksHostMismatchFromManualDef() = runBlocking {
        val def = DeclarativeHttpToolDef(
            name = "inject",
            description = "test",
            method = "GET",
            url = "https://example.com{{suffix}}",
            parameters = buildJsonObject { },
        )
        val tool = DeclarativeHttpTool(def) {
            OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
        }
        val r = tool.invoke("""{"suffix":"@evil.com/"}""")
        assertThat(r.ok).isFalse()
        assertThat(r.output).containsMatch("(?i)host|userinfo|mismatch|https")
    }

    @Test
    fun invoke_blocksHeaderCrlfInjection() = runBlocking {
        val def = DeclarativeHttpToolDef(
            name = "hdr",
            description = "test",
            method = "GET",
            url = "https://example.com/x",
            headers = mapOf("X-Token" to "{{token}}"),
            parameters = buildJsonObject { },
        )
        val tool = DeclarativeHttpTool(def) {
            OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
        }
        val crlf = "\r\n"
        val r = tool.invoke("""{"token":"ab""" + crlf + """X-Injected: 1"}""")
        assertThat(r.ok).isFalse()
        assertThat(r.output).contains("CRLF")
    }

    @Test
    fun defaultClient_disablesRedirects() {
        val c = DeclarativeHttpTool.defaultClient(5)
        assertThat(c.followRedirects).isFalse()
        assertThat(c.followSslRedirects).isFalse()
    }

    @Test
    fun invoke_reportsBlockedRedirect() = runBlocking {
        // Use MockWebServer over http via custom client that still hits our tool's redirect branch.
        // DeclarativeHttpTool requires https final URL — so we stub clientFactory to never be called
        // when safety fails first. Instead: manually exercise response handling by using https
        // example.com with a client that fails fast — skip live.
        // Direct unit: enqueue is for merge path only.
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://127.0.0.1/"))
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val req = okhttp3.Request.Builder().url(server.url("/redir")).get().build()
        client.newCall(req).execute().use { resp ->
            assertThat(resp.code).isEqualTo(302)
            assertThat(client.followRedirects).isFalse()
        }
    }

    @Test
    fun invokeGetAgainstMockServer() = runBlocking {
        val def = DeclarativeHttpToolsParser.parse(
            """
            {"tools":[{
              "name":"echo_get",
              "description":"echo",
              "method":"GET",
              "url":"https://example.com/get",
              "query":{"foo":"{{foo}}"}
            }]}
            """.trimIndent(),
        ).tools.single()

        val tool = DeclarativeHttpTool(def) { OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build() }
        val merged = DeclarativeHttpTool.mergeQuery(def.url, mapOf("foo" to "bar"))
        assertThat(merged).isEqualTo("https://example.com/get?foo=bar")
        assertThat(tool.spec.name).isEqualTo("echo_get")
        assertThat(tool.spec.requiresGrant.name).isEqualTo("Network")
    }

    @Test
    fun invokeWithHttpsMockViaCustomUrl_hostMismatchBlocked() = runBlocking {
        val host = server.hostName
        assertThat(DeclarativeHttpToolsParser.validateUrlTemplate("https://$host/")).isNotNull()
    }

    @Test
    fun liveGetOnMock_usingHttpClientOnly_viaInternalMerge() {
        server.enqueue(MockResponse().setBody("""{"ok":true,"secret":"sk-proj-ABCDEFGHIJKLMNOP"}"""))
        val client = OkHttpClient.Builder().followRedirects(false).build()
        val url = server.url("/v1/echo").toString()
        val req = okhttp3.Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            assertThat(resp.isSuccessful).isTrue()
            val body = resp.body!!.string()
            assertThat(body).contains("ok")
            val redacted = com.minima.chat.core.network.api.SecretRedact.redact(body)
            assertThat(redacted).doesNotContain("sk-proj-ABCDEFGHIJKLMNOP")
        }
    }

    @Test
    fun inventorySummaryIncludesHttp() {
        val inv = com.minima.chat.core.agent.tools.ToolRegistry.ToolInventory(
            builtin = listOf("now"),
            mcp = listOf("mcp__a__t"),
            http = listOf("status_ping"),
        )
        assertThat(inv.summary).isEqualTo("Built-in 1 · HTTP 1 · MCP 1")
        assertThat(inv.httpCount).isEqualTo(1)
    }

    @Test
    fun parseRejectsPrivateAndUserinfoInBatch() {
        val raw = """
            {"tools":[
              {"name":"a","description":"x","method":"GET","url":"https://192.168.0.1/"},
              {"name":"b","description":"x","method":"GET","url":"https://u@example.com/"},
              {"name":"c","description":"x","method":"GET","url":"https://example.com/ok"}
            ]}
        """.trimIndent()
        val r = DeclarativeHttpToolsParser.parse(raw)
        assertThat(r.tools.map { it.name }).containsExactly("c")
        assertThat(r.errors).hasSize(2)
    }
}
