package com.minima.chat.core.network.api

import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.TokenUsage
import com.minima.chat.core.network.sse.SseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent()
    data class Completed(
        val fullText: String,
        /** Provider usage when the API returned it (stream include_usage or non-stream). */
        val usage: TokenUsage? = null,
    ) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
}

@Singleton
class ChatApiClient @Inject constructor(
    private val httpHolder: OkHttpClientHolder,
) {
    private val okHttpClient: OkHttpClient get() = httpHolder.client
    private fun sseFactory() = EventSources.createFactory(okHttpClient)

    fun streamChat(
        config: ChatProviderConfig,
        messages: List<ChatMessageDto>,
    ): Flow<ChatStreamEvent> = callbackFlow {
        if (config.apiKey.isBlank()) {
            trySend(ChatStreamEvent.Error("Add an API key in Settings to chat."))
            close()
            return@callbackFlow
        }
        if (config.baseUrl.isBlank()) {
            trySend(ChatStreamEvent.Error("Set a base URL in Settings."))
            close()
            return@callbackFlow
        }
        if (config.model.isBlank()) {
            trySend(ChatStreamEvent.Error("Set a model id in Settings."))
            close()
            return@callbackFlow
        }

        val request = try {
            ChatRequestBuilder.build(config, messages, stream = true)
        } catch (e: Exception) {
            trySend(ChatStreamEvent.Error(e.message ?: "Invalid request"))
            close()
            return@callbackFlow
        }

        val buffer = StringBuilder()
        var lastUsage: TokenUsage? = null
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data.trim() == "[DONE]") {
                    trySend(ChatStreamEvent.Completed(buffer.toString(), lastUsage))
                    eventSource.cancel()
                    close()
                    return
                }
                val chunk = SseParser.parseChunk(data)
                if (chunk != null) {
                    TokenUsage.from(chunk.usage)?.let { lastUsage = it }
                    val delta = chunk.content
                    if (!delta.isNullOrEmpty()) {
                        buffer.append(delta)
                        trySend(ChatStreamEvent.Delta(delta))
                    }
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val code = response?.code
                val errBody = try { response?.body?.string() } catch (_: Exception) { null }
                val msg = when {
                    t != null && code == null -> FriendlyHttp.fromThrowable(t)
                    code != null -> FriendlyHttp.message(code, errBody.orEmpty())
                    else -> "Stream failed"
                }
                trySend(ChatStreamEvent.Error(msg.trim()))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                if (isActive && buffer.isNotEmpty()) {
                    trySend(ChatStreamEvent.Completed(buffer.toString(), lastUsage))
                }
                close()
            }
        }

        val es = sseFactory().newEventSource(request, listener)
        awaitClose { es.cancel() }
    }

    /** Non-streaming with backoff on transient 429/5xx. */
    fun completeChat(
        config: ChatProviderConfig,
        messages: List<ChatMessageDto>,
    ): Flow<ChatStreamEvent> = flow {
        if (config.apiKey.isBlank()) {
            emit(ChatStreamEvent.Error("Add an API key in Settings to chat."))
            return@flow
        }
        val request = try {
            ChatRequestBuilder.build(config, messages, stream = false)
        } catch (e: Exception) {
            emit(ChatStreamEvent.Error(e.message ?: "Invalid request"))
            return@flow
        }
        try {
            val (text, usage) = HttpRetry.withBackoff(
                isRetryable = { t ->
                    t is TransientHttpException || HttpRetry.isTransientMessage(t.message.orEmpty())
                },
            ) {
                withContext(Dispatchers.IO) {
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val msg = FriendlyHttp.message(response.code, body)
                        if (HttpRetry.isTransientStatus(response.code)) {
                            throw TransientHttpException(msg)
                        }
                        throw PermanentHttpException(msg)
                    }
                    val parsed = ChatRequestBuilder.decodeResponse(body)
                    val content = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                    if (content.isEmpty() && parsed.error != null) {
                        throw PermanentHttpException(parsed.error!!.message ?: "API error")
                    }
                    content to TokenUsage.from(parsed.usage)
                }
            }
            emit(ChatStreamEvent.Delta(text))
            emit(ChatStreamEvent.Completed(text, usage))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransientHttpException) {
            emit(ChatStreamEvent.Error(e.message ?: "Transient error"))
        } catch (e: PermanentHttpException) {
            emit(ChatStreamEvent.Error(e.message ?: "Request failed"))
        } catch (e: IOException) {
            emit(ChatStreamEvent.Error(FriendlyHttp.fromThrowable(e)))
        } catch (e: Exception) {
            emit(ChatStreamEvent.Error(FriendlyHttp.fromThrowable(e)))
        }
    }.flowOn(Dispatchers.IO)


    private class TransientHttpException(message: String) : IOException(message)
    private class PermanentHttpException(message: String) : IOException(message)
}
