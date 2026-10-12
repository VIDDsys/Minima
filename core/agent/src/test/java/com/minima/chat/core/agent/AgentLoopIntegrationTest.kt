package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.loop.AgentBudget
import com.minima.chat.core.agent.loop.AgentEvent
import com.minima.chat.core.agent.loop.AgentLoop
import com.minima.chat.core.agent.tools.ToolResult
import com.minima.chat.core.agent.tools.ToolRuntime
import com.minima.chat.core.network.api.ChatApiClient
import com.minima.chat.core.network.api.OkHttpClientHolder
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.ToolDefinitionDto
import com.minima.chat.core.network.dto.ToolFunctionDto
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * JVM integration: AgentLoop + real OkHttp against MockWebServer (no Android / no KVM).
 */
class AgentLoopIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var holder: OkHttpClientHolder
    private lateinit var api: ChatApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        holder = OkHttpClientHolder()
        api = ChatApiClient(holder)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = ChatProviderConfig(
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        apiKey = "test-key",
        model = "test-model",
    )

    private fun loop(tools: ToolRuntime) = AgentLoop(api, tools, holder)

    private fun emptyTools() = object : ToolRuntime {
        override fun definitions() = emptyList<ToolDefinitionDto>()
        override suspend fun dispatch(name: String, argumentsJson: String) =
            ToolResult(false, "none")
    }

    private fun echoToolRuntime() = object : ToolRuntime {
        override fun definitions() = listOf(
            ToolDefinitionDto(
                function = ToolFunctionDto(
                    name = "echo",
                    description = "Echo args",
                    parameters = buildJsonObject {},
                ),
            ),
        )
        override suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
            if (name != "echo") return ToolResult(false, "unknown")
            return ToolResult(true, "echoed:$argumentsJson")
        }
    }

    @Test
    fun blankApiKey_emitsError() = runBlocking {
        val events = loop(emptyTools()).run(
            ChatProviderConfig(server.url("/v1").toString(), "", "m"),
            listOf(ChatMessageDto(role = "user", content = "hi")),
        ).toList()
        assertThat(events.filterIsInstance<AgentEvent.Error>()).isNotEmpty()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun nonStreaming_withTools_plainReply() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":"1","choices":[{"index":0,"message":{"role":"assistant","content":"Plain reply"},"finish_reason":"stop"}]}""",
                )
                .addHeader("Content-Type", "application/json"),
        )
        val events = loop(echoToolRuntime()).run(
            config(),
            listOf(ChatMessageDto(role = "user", content = "hi")),
            budget = AgentBudget(maxIterations = 4),
            preferStreaming = false,
        ).toList()
        assertThat(events.filterIsInstance<AgentEvent.AssistantCompleted>().map { it.text })
            .contains("Plain reply")
        assertThat(events.filterIsInstance<AgentEvent.Done>()).isNotEmpty()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun nonStreaming_toolCallThenFinal() = runBlocking {
        // Regular string so \" becomes a real JSON escape (raw """ does not).
        val toolBody =
            "{\"id\":\"1\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," +
                "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\"," +
                "\"function\":{\"name\":\"echo\",\"arguments\":\"{\\\"x\\\":1}\"}}]}," +
                "\"finish_reason\":\"tool_calls\"}]}"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(toolBody)
                .addHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":"2","choices":[{"index":0,"message":{"role":"assistant","content":"Done after tool"},"finish_reason":"stop"}]}""",
                )
                .addHeader("Content-Type", "application/json"),
        )
        val events = loop(echoToolRuntime()).run(
            config(),
            listOf(ChatMessageDto(role = "user", content = "use echo")),
            budget = AgentBudget(maxIterations = 6, toolTimeoutMs = 5_000),
            preferStreaming = false,
        ).toList()
        val started = events.filterIsInstance<AgentEvent.ToolStarted>()
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>()
        assertThat(events.map { it::class.simpleName }).isNotEmpty()
        assertThat(started).hasSize(1)
        assertThat(finished).hasSize(1)
        assertThat(finished[0].ok).isTrue()
        assertThat(finished[0].result).contains("echoed:")
        assertThat(events.filterIsInstance<AgentEvent.AssistantCompleted>().last().text)
            .isEqualTo("Done after tool")
        assertThat(events.filterIsInstance<AgentEvent.Done>()).isNotEmpty()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun nonStreaming_http401_emitsError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val events = loop(echoToolRuntime()).run(
            config(),
            listOf(ChatMessageDto(role = "user", content = "hi")),
            preferStreaming = false,
        ).toList()
        val errors = events.filterIsInstance<AgentEvent.Error>()
        assertThat(errors).isNotEmpty()
        assertThat(errors.any { it.message.contains("401") }).isTrue()
    }

    @Test
    fun hugeToolResult_isTruncated() = runBlocking {
        val toolBody =
            "{\"id\":\"1\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," +
                "\"tool_calls\":[{\"id\":\"call_big\",\"type\":\"function\"," +
                "\"function\":{\"name\":\"echo\",\"arguments\":\"{}\"}}]}," +
                "\"finish_reason\":\"tool_calls\"}]}"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(toolBody)
                .addHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":"2","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}""",
                )
                .addHeader("Content-Type", "application/json"),
        )
        val huge = "Z".repeat(8_000)
        val tools = object : ToolRuntime {
            override fun definitions() = echoToolRuntime().definitions()
            override suspend fun dispatch(name: String, argumentsJson: String) =
                ToolResult(true, huge)
        }
        val events = loop(tools).run(
            config(),
            listOf(ChatMessageDto(role = "user", content = "dump")),
            budget = AgentBudget(maxIterations = 4, toolTimeoutMs = 5_000, maxToolResultChars = 500),
            preferStreaming = false,
        ).toList()
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>()
        assertThat(finished).hasSize(1)
        assertThat(finished[0].result.length).isAtMost(500)
        assertThat(finished[0].result).contains("truncated tool result")
        assertThat(finished[0].ok).isTrue()
        assertThat(events.filterIsInstance<AgentEvent.Done>()).isNotEmpty()
    }

    @Test
    fun http401_includesProviderDetail() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )
        val events = loop(echoToolRuntime()).run(
            config(),
            listOf(ChatMessageDto(role = "user", content = "hi")),
            preferStreaming = false,
        ).toList()
        val errors = events.filterIsInstance<AgentEvent.Error>()
        assertThat(errors).isNotEmpty()
        assertThat(errors.any { it.message.contains("401") && it.message.contains("Incorrect API key") }).isTrue()
    }

}
