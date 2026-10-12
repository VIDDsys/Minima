package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.ChatRequestBuilder
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import org.junit.Test

class ChatRequestBuilderTest {
    @Test
    fun normalizeBaseUrl_addsHttpsAndTrimsSlash() {
        assertThat(ChatRequestBuilder.normalizeBaseUrl("api.openai.com/v1/"))
            .isEqualTo("https://api.openai.com/v1")
    }

    @Test
    fun chatCompletionsUrl_appendsPath() {
        assertThat(ChatRequestBuilder.chatCompletionsUrl("https://api.openai.com"))
            .isEqualTo("https://api.openai.com/v1/chat/completions")
        assertThat(ChatRequestBuilder.chatCompletionsUrl("https://api.openai.com/v1"))
            .isEqualTo("https://api.openai.com/v1/chat/completions")
    }

    @Test
    fun build_setsBearerAndJsonBody() {
        val req = ChatRequestBuilder.build(
            ChatProviderConfig("https://api.openai.com", "sk-test", "gpt-4o-mini"),
            listOf(ChatMessageDto("user", "hi")),
            stream = true,
        )
        assertThat(req.header("Authorization")).isEqualTo("Bearer sk-test")
        assertThat(req.url.toString()).endsWith("/v1/chat/completions")
        val body = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertThat(body).contains("\"model\":\"gpt-4o-mini\"")
        assertThat(body).contains("\"stream\":true")
        assertThat(body).contains("\"role\":\"user\"")
    }

    @Test(expected = IllegalArgumentException::class)
    fun build_rejectsEmptyApiKey() {
        ChatRequestBuilder.build(
            ChatProviderConfig("https://api.openai.com", "", "gpt-4o-mini"),
            listOf(ChatMessageDto("user", "hi")),
            stream = false,
        )
    }

    @Test
    fun build_stream_includes_stream_options_usage() {
        val req = ChatRequestBuilder.build(
            ChatProviderConfig("https://api.openai.com", "sk-test", "gpt-4o-mini"),
            listOf(ChatMessageDto("user", "hi")),
            stream = true,
        )
        val body = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertThat(body).contains("\"stream_options\"")
        assertThat(body).contains("\"include_usage\":true")
    }

    @Test
    fun build_non_stream_omits_stream_options() {
        val req = ChatRequestBuilder.build(
            ChatProviderConfig("https://api.openai.com", "sk-test", "gpt-4o-mini"),
            listOf(ChatMessageDto("user", "hi")),
            stream = false,
        )
        val body = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertThat(body).doesNotContain("stream_options")
    }
}
