package com.minima.chat.core.agent.loop

import com.minima.chat.core.agent.tools.FocusTaskLogic
import com.minima.chat.core.agent.tools.ToolResult
import com.minima.chat.core.data.util.ToolResultLimiter
import com.minima.chat.core.network.api.FriendlyHttp
import com.minima.chat.core.network.api.SecretRedact
import com.minima.chat.core.agent.tools.ToolRuntime
import com.minima.chat.core.network.api.ChatApiClient
import com.minima.chat.core.network.api.ChatRequestBuilder
import com.minima.chat.core.network.api.ChatStreamEvent
import com.minima.chat.core.network.api.HttpRetry
import com.minima.chat.core.network.api.OkHttpClientHolder
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.TokenUsage
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolDefinitionDto
import com.minima.chat.core.network.sse.SseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AgentEvent {
    data class AssistantDelta(val text: String) : AgentEvent()
    data class AssistantCompleted(val text: String) : AgentEvent()
    data class ToolStarted(val call: ToolCallDto) : AgentEvent()
    data class ToolFinished(val call: ToolCallDto, val result: String, val ok: Boolean) : AgentEvent()
    data class Error(val message: String, val recoverable: Boolean = false) : AgentEvent()
    /** Emitted at the start of each agent iteration (1-based). */
    data class Step(val index: Int, val max: Int) : AgentEvent()
    data object BudgetExhausted : AgentEvent()
    /** End of agent turn; [usage] sums provider tokens across LLM rounds when available. */
    data class Done(val usage: TokenUsage? = null) : AgentEvent()
}

data class AgentBudget(
    val maxIterations: Int = 12,
    /** Overall wall-clock deadline for the whole agent turn. */
    val wallClockMs: Long = 180_000L,
    /** Per-tool-call timeout. */
    val toolTimeoutMs: Long = 60_000L,
    /** Cap tool output fed back into the LLM (and shown on ToolFinished). */
    val maxToolResultChars: Int = ToolResultLimiter.MAX_CHARS,
)

@Singleton
class AgentLoop(
    private val chatApiClient: ChatApiClient,
    private val tools: ToolRuntime,
    private val httpHolder: OkHttpClientHolder,
    private val focusRunContext: FocusRunContext? = null,
) {
    @Inject
    constructor(
        chatApiClient: ChatApiClient,
        toolRegistry: com.minima.chat.core.agent.tools.ToolRegistry,
        httpHolder: OkHttpClientHolder,
        focusRunContext: FocusRunContext,
    ) : this(chatApiClient, toolRegistry as ToolRuntime, httpHolder, focusRunContext)

    private val okHttpClient: OkHttpClient get() = httpHolder.client
    private fun sseFactory() = EventSources.createFactory(okHttpClient)

    fun run(
        config: ChatProviderConfig,
        history: List<ChatMessageDto>,
        budget: AgentBudget = AgentBudget(),
        preferStreaming: Boolean = true,
        toolsOverride: ToolRuntime? = null,
    ): Flow<AgentEvent> = flow {
        if (config.apiKey.isBlank()) {
            emit(AgentEvent.Error("Add an API key in Settings to chat."))
            return@flow
        }
        if (config.baseUrl.isBlank()) {
            emit(AgentEvent.Error("Set a base URL in Settings."))
            return@flow
        }
        if (config.model.isBlank()) {
            emit(AgentEvent.Error("Set a model id in Settings."))
            return@flow
        }

        val activeTools = toolsOverride ?: tools
        val messages = history.toMutableList()
        val toolDefs = activeTools.definitions()
        val started = System.currentTimeMillis()
        var iterations = 0
        var consecutiveErrors = 0
        var accumulatedUsage = TokenUsage()
        val nestedTools = FocusTaskLogic.withoutFocusTask(activeTools)
        val previousFrame = focusRunContext?.install(config, budget, nestedTools, started)
        try {

        while (iterations < budget.maxIterations) {
            currentCoroutineContext().ensureActive()
            if (System.currentTimeMillis() - started > budget.wallClockMs) {
                emit(AgentEvent.BudgetExhausted)
                return@flow
            }
            iterations++
            focusRunContext?.updateIterations(iterations)
            emit(AgentEvent.Step(iterations, budget.maxIterations))

            if (toolDefs.isEmpty()) {
                var full = StringBuilder()
                var failed = false
                chatApiClient.streamChat(config, messages).collect { ev ->
                    when (ev) {
                        is ChatStreamEvent.Delta -> {
                            full.append(ev.text)
                            emit(AgentEvent.AssistantDelta(ev.text))
                        }
                        is ChatStreamEvent.Completed -> {
                            ev.usage?.let { accumulatedUsage = accumulatedUsage + it }
                            emit(AgentEvent.AssistantCompleted(ev.fullText.ifEmpty { full.toString() }))
                            emit(AgentEvent.Done(accumulatedUsage.takeIf { it.isPresent() }))
                        }
                        is ChatStreamEvent.Error -> {
                            failed = true
                            emit(AgentEvent.Error(ev.message))
                        }
                    }
                }
                if (!failed) return@flow
                // Partial stream already painted — do not duplicate via completeChat.
                if (full.isNotEmpty()) {
                    emit(AgentEvent.AssistantCompleted(full.toString()))
                    emit(AgentEvent.Done(accumulatedUsage.takeIf { it.isPresent() }))
                    return@flow
                }
                chatApiClient.completeChat(config, messages).collect { ev ->
                    when (ev) {
                        is ChatStreamEvent.Delta -> emit(AgentEvent.AssistantDelta(ev.text))
                        is ChatStreamEvent.Completed -> {
                            ev.usage?.let { accumulatedUsage = accumulatedUsage + it }
                            emit(AgentEvent.AssistantCompleted(ev.fullText))
                            emit(AgentEvent.Done(accumulatedUsage.takeIf { it.isPresent() }))
                        }
                        is ChatStreamEvent.Error -> emit(AgentEvent.Error(ev.message))
                    }
                }
                return@flow
            }

            val turn: StreamTurn = try {
                HttpRetry.withBackoff(
                    isRetryable = { t -> HttpRetry.isTransientMessage(t.message.orEmpty()) },
                ) {
                    if (preferStreaming) {
                        streamWithTools(config, messages, toolDefs)
                    } else {
                        completeWithTools(config, messages, toolDefs)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveErrors++
                val msg = FriendlyHttp.fromThrowable(e)
                if (consecutiveErrors >= 2) {
                    emit(AgentEvent.Error(msg))
                    return@flow
                }
                emit(AgentEvent.Error(msg, recoverable = true))
                try {
                    completeWithTools(config, messages, toolDefs)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    emit(AgentEvent.Error(FriendlyHttp.fromThrowable(e2)))
                    return@flow
                }
            }

            consecutiveErrors = 0
            turn.usage?.let { accumulatedUsage = accumulatedUsage + it }
            val content = turn.content
            val toolCalls = turn.toolCalls

            if (toolCalls.isNotEmpty()) {
                messages += ChatMessageDto(
                    role = "assistant",
                    content = content.ifEmpty { null },
                    toolCalls = toolCalls,
                )
                if (content.isNotEmpty()) {
                    emit(AgentEvent.AssistantDelta(content))
                    emit(AgentEvent.AssistantCompleted(content))
                }
                // Emit ToolStarted in API order, run tools in parallel, then emit Finished in order.
                for (call in toolCalls) {
                    currentCoroutineContext().ensureActive()
                    emit(AgentEvent.ToolStarted(call))
                }
                val results = runToolsParallel(toolCalls, budget, activeTools)
                for ((call, result) in results) {
                    currentCoroutineContext().ensureActive()
                    // Cap huge tool dumps so the next LLM round (and UI) stay bounded.
                    val toolBody = ToolResultLimiter.forLlm(result.output, budget.maxToolResultChars)
                    emit(AgentEvent.ToolFinished(call, toolBody, result.ok))
                    messages += ChatMessageDto(
                        role = "tool",
                        content = toolBody,
                        toolCallId = call.id,
                        name = call.function.name,
                    )
                }
                continue
            }

            if (content.isNotEmpty()) {
                emit(AgentEvent.AssistantDelta(content))
                emit(AgentEvent.AssistantCompleted(content))
            } else if (turn.error != null) {
                emit(AgentEvent.Error(turn.error))
            }
            emit(AgentEvent.Done(accumulatedUsage.takeIf { it.isPresent() }))
            return@flow
        }
        emit(AgentEvent.BudgetExhausted)
        } finally {
            focusRunContext?.restore(previousFrame)
        }
    }.flowOn(Dispatchers.IO)

    private data class StreamTurn(
        val content: String,
        val toolCalls: List<ToolCallDto>,
        val error: String? = null,
        val usage: TokenUsage? = null,
    )

    /**
     * Dispatch multiple tool_calls concurrently; preserve call order in the returned list.
     */
    private suspend fun runToolsParallel(
        toolCalls: List<ToolCallDto>,
        budget: AgentBudget,
        activeTools: ToolRuntime = tools,
    ): List<Pair<ToolCallDto, ToolResult>> = coroutineScope {
        toolCalls.map { call ->
            async {
                val result = try {
                    withTimeout(budget.toolTimeoutMs) {
                        activeTools.dispatch(call.function.name, call.function.arguments)
                    }
                } catch (e: TimeoutCancellationException) {
                    ToolResult(
                        false,
                        "Tool timed out after ${budget.toolTimeoutMs / 1000}s",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ToolResult(false, FriendlyHttp.fromThrowable(e))
                }
                call to result
            }
        }.awaitAll()
    }

    private suspend fun completeWithTools(
        config: ChatProviderConfig,
        messages: List<ChatMessageDto>,
        toolDefs: List<ToolDefinitionDto>,
    ): StreamTurn {
        val request = ChatRequestBuilder.build(config, messages, stream = false, tools = toolDefs)
        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException(FriendlyHttp.message(response.code, body))
        }
        val parsed = ChatRequestBuilder.decodeResponse(body)
        if (parsed.error != null) {
            return StreamTurn("", emptyList(), SecretRedact.redact(parsed.error!!.message ?: "API error").take(200))
        }
        val msg = parsed.choices.firstOrNull()?.message
        return StreamTurn(
            content = msg?.content.orEmpty(),
            toolCalls = msg?.toolCalls.orEmpty(),
            usage = TokenUsage.from(parsed.usage),
        )
    }

    /**
     * SSE with tools + idle watchdog. If the provider sends no events for
     * [SSE_IDLE_TIMEOUT_MS], cancel the EventSource so the turn cannot hang forever.
     */
    private suspend fun streamWithTools(
        config: ChatProviderConfig,
        messages: List<ChatMessageDto>,
        toolDefs: List<ToolDefinitionDto>,
    ): StreamTurn = coroutineScope {
        val request = ChatRequestBuilder.build(config, messages, stream = true, tools = toolDefs)
        val contentBuf = StringBuilder()
        val aggregator = ToolCallAggregator()
        val finishReason = AtomicReference<String?>(null)
        val usageRef = AtomicReference<TokenUsage?>(null)
        val lastEventMs = AtomicLong(System.currentTimeMillis())
        val esRef = AtomicReference<EventSource?>(null)
        val turn = async {
            suspendCancellableCoroutine { cont ->
                val listener = object : EventSourceListener() {
                    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                        lastEventMs.set(System.currentTimeMillis())
                        if (data.trim() == "[DONE]") {
                            eventSource.cancel()
                            if (cont.isActive) {
                                cont.resume(
                                    StreamTurn(contentBuf.toString(), aggregator.build(), usage = usageRef.get()),
                                )
                            }
                            return
                        }
                        val parsed = SseParser.parseChunk(data) ?: return
                        parsed.content?.let { contentBuf.append(it) }
                        parsed.toolCalls?.let { aggregator.accept(it) }
                        parsed.finishReason?.let { finishReason.set(it) }
                        TokenUsage.from(parsed.usage)?.let { usageRef.set(it) }
                    }

                    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                        if (!cont.isActive) return
                        if (contentBuf.isNotEmpty() || !aggregator.isEmpty()) {
                            cont.resume(StreamTurn(contentBuf.toString(), aggregator.build(), usage = usageRef.get()))
                            return
                        }
                        val code = response?.code
                        val errBody = try { response?.body?.string() } catch (_: Exception) { null }
                        val msg = when {
                            t != null && code == null -> FriendlyHttp.fromThrowable(t)
                            code != null -> FriendlyHttp.message(code, errBody.orEmpty())
                            else -> "Stream failed"
                        }
                        cont.resumeWithException(IllegalStateException(msg))
                    }

                    override fun onClosed(eventSource: EventSource) {
                        if (cont.isActive) {
                            cont.resume(StreamTurn(contentBuf.toString(), aggregator.build(), usage = usageRef.get()))
                        }
                    }
                }
                val es = sseFactory().newEventSource(request, listener)
                esRef.set(es)
                cont.invokeOnCancellation { es.cancel() }
            }
        }
        val watchdog = launch {
            while (true) {
                delay(5_000L)
                val idle = System.currentTimeMillis() - lastEventMs.get()
                if (idle >= SSE_IDLE_TIMEOUT_MS) {
                    esRef.get()?.cancel()
                    break
                }
            }
        }
        try {
            turn.await()
        } finally {
            watchdog.cancel()
        }
    }

    companion object {
        /** No SSE event for this long → cancel (prevents infinite "fetching"). */
        const val SSE_IDLE_TIMEOUT_MS: Long = 45_000L
    }

}
