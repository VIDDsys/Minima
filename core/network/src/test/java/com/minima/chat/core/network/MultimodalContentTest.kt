package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.api.ChatRequestBuilder
import com.minima.chat.core.network.api.MultimodalContent
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import com.minima.chat.core.network.dto.ImageAttachmentDto
import org.junit.Test

class MultimodalContentTest {
    @Test
    fun encodeMessage_textOnly_staysString() {
        val json = MultimodalContent.encodeMessage(
            ChatMessageDto(role = "user", content = "hello"),
        ).toString()
        assertThat(json).contains("\"content\":\"hello\"")
        assertThat(json).doesNotContain("image_url")
    }

    @Test
    fun encodeMessage_withImage_usesContentArray() {
        val img = ImageAttachmentDto("image/jpeg", "abc123")
        val json = MultimodalContent.encodeMessage(
            MultimodalContent.userWithImages("what is this?", listOf(img)),
        ).toString()
        assertThat(json).contains("\"type\":\"text\"")
        assertThat(json).contains("\"text\":\"what is this?\"")
        assertThat(json).contains("\"type\":\"image_url\"")
        assertThat(json).contains("data:image/jpeg;base64,abc123")
    }

    @Test
    fun encodeMessage_imageOnly_omitsEmptyText() {
        val img = ImageAttachmentDto("image/png", "xyz")
        val json = MultimodalContent.encodeMessage(
            MultimodalContent.userWithImages(null, listOf(img)),
        ).toString()
        assertThat(json).contains("image_url")
        assertThat(json).doesNotContain("\"type\":\"text\"")
    }

    @Test
    fun build_request_includes_multimodal_parts() {
        val img = ImageAttachmentDto("image/jpeg", "QQ==")
        val req = ChatRequestBuilder.build(
            ChatProviderConfig("https://api.openai.com", "sk-test", "gpt-4o"),
            listOf(MultimodalContent.userWithImages("describe", listOf(img))),
            stream = true,
        )
        val body = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertThat(body).contains("image_url")
        assertThat(body).contains("include_usage")
        assertThat(body).contains("\"model\":\"gpt-4o\"")
    }

    @Test
    fun dataUrl_and_sendable_gate() {
        val ok = ImageAttachmentDto("image/jpeg", "a")
        assertThat(ok.isSendable()).isTrue()
        assertThat(ok.dataUrl).startsWith("data:image/jpeg;base64,")
        val huge = ImageAttachmentDto("image/jpeg", "x".repeat(ImageAttachmentDto.MAX_BASE64_CHARS + 1))
        assertThat(huge.isSendable()).isFalse()
        val filtered = MultimodalContent.userWithImages("hi", listOf(huge))
        assertThat(filtered.images).isEmpty()
    }

    @Test
    fun encodeMessages_preserves_image_across_history() {
        val img = ImageAttachmentDto("image/jpeg", "abc")
        val messages = listOf(
            MultimodalContent.userWithImages("first", listOf(img)),
            ChatMessageDto(role = "assistant", content = "ok"),
            ChatMessageDto(role = "user", content = "follow up"),
        )
        val arr = MultimodalContent.encodeMessages(messages).toString()
        assertThat(arr).contains("image_url")
        assertThat(arr).contains("follow up")
    }

    @Test
    fun encodeRequestBody_includes_sampling_when_set() {
        val body = MultimodalContent.encodeRequestBody(
            model = "gpt-4o-mini",
            messages = listOf(ChatMessageDto(role = "user", content = "hi")),
            stream = true,
            tools = null,
            includeStreamUsage = true,
            temperature = 0.7,
            maxTokens = 2048,
            topP = 0.9,
        )
        assertThat(body).contains("\"temperature\":0.7")
        assertThat(body).contains("\"max_tokens\":2048")
        assertThat(body).contains("\"top_p\":0.9")
    }

    @Test
    fun encodeRequestBody_omits_sampling_when_null() {
        val body = MultimodalContent.encodeRequestBody(
            model = "gpt-4o-mini",
            messages = listOf(ChatMessageDto(role = "user", content = "hi")),
            stream = false,
            tools = null,
            includeStreamUsage = false,
            temperature = null,
            maxTokens = null,
            topP = null,
        )
        assertThat(body).doesNotContain("temperature")
        assertThat(body).doesNotContain("max_tokens")
        assertThat(body).doesNotContain("top_p")
    }

    @Test
    fun build_request_forwards_sampling_from_config() {
        val req = ChatRequestBuilder.build(
            ChatProviderConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-test",
                model = "gpt-4o",
                temperature = 1.2,
                maxTokens = 512,
                topP = 0.8,
            ),
            listOf(ChatMessageDto(role = "user", content = "x")),
            stream = false,
        )
        val body = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertThat(body).contains("\"temperature\":1.2")
        assertThat(body).contains("\"max_tokens\":512")
        assertThat(body).contains("\"top_p\":0.8")
    }

    @Test
    fun encodeRequestBody_sanitizes_bad_sampling() {
        val body = MultimodalContent.encodeRequestBody(
            model = "gpt-4o-mini",
            messages = listOf(ChatMessageDto(role = "user", content = "hi")),
            stream = false,
            tools = null,
            includeStreamUsage = false,
            temperature = Double.NaN,
            maxTokens = -5,
            topP = Double.POSITIVE_INFINITY,
        )
        assertThat(body).doesNotContain("temperature")
        assertThat(body).doesNotContain("max_tokens")
        assertThat(body).doesNotContain("top_p")

        val body2 = MultimodalContent.encodeRequestBody(
            model = "gpt-4o-mini",
            messages = listOf(ChatMessageDto(role = "user", content = "hi")),
            stream = false,
            tools = null,
            includeStreamUsage = false,
            temperature = 9.0,
            maxTokens = Int.MAX_VALUE,
            topP = 1.7,
        )
        assertThat(body2).contains("\"temperature\":2.0")
        assertThat(body2).contains("\"max_tokens\":200000")
        assertThat(body2).contains("\"top_p\":1.0")
    }

    @Test
    fun build_request_clamps_sampling_from_untrusted_config() {
        val req = ChatRequestBuilder.build(
            ChatProviderConfig(
                baseUrl = "https://api.openai.com",
                apiKey = "sk-test",
                model = "gpt-4o",
                temperature = -3.0,
                maxTokens = 0,
                topP = -0.5,
            ),
            listOf(ChatMessageDto(role = "user", content = "x")),
            stream = false,
        )
        val body = okio.Buffer().also { req.body!!.writeTo(it) }.readUtf8()
        assertThat(body).contains("\"temperature\":0.0")
        assertThat(body).doesNotContain("max_tokens")
        assertThat(body).contains("\"top_p\":0.0")
    }

}
