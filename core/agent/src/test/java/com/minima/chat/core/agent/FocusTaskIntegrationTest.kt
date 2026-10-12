package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.loop.AgentBudget
import com.minima.chat.core.agent.loop.AgentLoop
import com.minima.chat.core.agent.loop.FocusRunContext
import com.minima.chat.core.agent.tools.FocusTaskLogic
import com.minima.chat.core.agent.tools.FocusTaskTool
import com.minima.chat.core.agent.tools.ToolResult
import com.minima.chat.core.agent.tools.ToolRuntime
import com.minima.chat.core.network.api.ChatApiClient
import com.minima.chat.core.network.api.OkHttpClientHolder
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.ToolDefinitionDto
import com.minima.chat.core.network.dto.ToolFunctionDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

/**
 * focus_task end-to-end against MockWebServer (no device).
 */
class FocusTaskIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var holder: OkHttpClientHolder
    private lateinit var api: ChatApiClient
    private lateinit var focusCtx: FocusRunContext

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        holder = OkHttpClientHolder()
        api = ChatApiClient(holder)
        focusCtx = FocusRunContext()
    }

    @After
    fun tearDown() {
        server.shutdown()
        focusCtx.leaveNested()
        focusCtx.restore(null)
    }

    private fun config() = ChatProviderConfig(
        baseUrl = server.url("/v1").toString().trimEnd('/'),
        apiKey = "test-key",
        model = "test-model",
    )

    private fun echoTools(): ToolRuntime = object : ToolRuntime {
        override fun definitions() = listOf(
            ToolDefinitionDto(
                function = ToolFunctionDto("echo", "Echo", buildJsonObject {}),
            ),
            ToolDefinitionDto(
                function = ToolFunctionDto(FocusTaskLogic.TOOL_NAME, "Focus", buildJsonObject {}),
            ),
        )
        override suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
            return when (name) {
                "echo" -> ToolResult(true, "echoed:$argumentsJson")
                FocusTaskLogic.TOOL_NAME -> ToolResult(false, "should be overridden")
                else -> ToolResult(false, "unknown")
            }
        }
    }

    @Test
    fun focusTask_runsNestedLoop_andReturnsSummary() = runBlocking {
        // Nested focus_task LLM call (preferStreaming=false)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"id":"n1","choices":[{"index":0,"message":{"role":"assistant","content":"Focused answer"},"finish_reason":"stop"}]}""",
                )
                .addHeader("Content-Type", "application/json"),
        )

        val loop = AgentLoop(api, echoTools(), holder, focusCtx)
        val tool = FocusTaskTool(Provider { loop }, focusCtx)

        val nested = FocusTaskLogic.withoutFocusTask(echoTools())
        focusCtx.install(
            config = config(),
            budget = AgentBudget(maxIterations = 8, wallClockMs = 120_000, toolTimeoutMs = 60_000),
            nestedTools = nested,
        )

        val result = tool.invoke("""{"task":"Summarize X"}""")
        assertThat(result.ok).isTrue()
        assertThat(result.output).contains("Focused answer")
        assertThat(result.output).contains("focus_task")
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(focusCtx.nestDepth()).isEqualTo(0)
    }

    @Test
    fun focusTask_rejectsWithoutParentContext() = runBlocking {
        val loop = AgentLoop(api, echoTools(), holder, focusCtx)
        val tool = FocusTaskTool(Provider { loop }, focusCtx)
        val result = tool.invoke("""{"task":"x"}""")
        assertThat(result.ok).isFalse()
        assertThat(result.output).contains("only available during")
    }

    @Test
    fun focusTask_rejectsConcurrentNest() = runBlocking {
        val loop = AgentLoop(api, echoTools(), holder, focusCtx)
        val tool = FocusTaskTool(Provider { loop }, focusCtx)
        focusCtx.install(
            config = config(),
            budget = AgentBudget(maxIterations = 8, wallClockMs = 120_000, toolTimeoutMs = 60_000),
            nestedTools = FocusTaskLogic.withoutFocusTask(echoTools()),
        )
        assertThat(focusCtx.tryEnterNested()).isTrue()
        val result = tool.invoke("""{"task":"x"}""")
        assertThat(result.ok).isFalse()
        assertThat(result.output).contains("not allowed")
        focusCtx.leaveNested()
    }

    @Test
    fun focusTask_rejectsBlankTask() = runBlocking {
        val loop = AgentLoop(api, echoTools(), holder, focusCtx)
        val tool = FocusTaskTool(Provider { loop }, focusCtx)
        focusCtx.install(
            config = config(),
            budget = AgentBudget(maxIterations = 8, wallClockMs = 120_000, toolTimeoutMs = 60_000),
            nestedTools = FocusTaskLogic.withoutFocusTask(echoTools()),
        )
        val result = tool.invoke("""{"task":"  "}""")
        assertThat(result.ok).isFalse()
        assertThat(result.output).contains("task required")
    }
}
