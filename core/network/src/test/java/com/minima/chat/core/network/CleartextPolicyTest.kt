package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.minima.chat.core.network.api.ChatRequestBuilder
import com.minima.chat.core.network.api.CleartextPolicy
import com.minima.chat.core.network.dto.ChatMessageDto
import com.minima.chat.core.network.dto.ChatProviderConfig
import org.junit.Test

class CleartextPolicyTest {
    @Test
    fun allowsHttpsAnywhere() {
        assertThat(CleartextPolicy.rejectDisallowedCleartext("https://api.openai.com/v1")).isNull()
        assertThat(CleartextPolicy.rejectDisallowedCleartext("api.openai.com/v1")).isNull() // → https
    }

    @Test
    fun allowsLoopbackCleartext() {
        for (host in listOf(
            "http://127.0.0.1:11434/v1",
            "http://localhost:11434/v1",
            "http://10.0.2.2:11434/v1",
            "http://[::1]:11434/v1",
        )) {
            assertWithMessage(host)
                .that(CleartextPolicy.rejectDisallowedCleartext(host))
                .isNull()
        }
    }

    @Test
    fun rejectsPublicCleartext() {
        assertThat(CleartextPolicy.rejectDisallowedCleartext("http://api.openai.com/v1"))
            .isEqualTo(CleartextPolicy.REJECT_MESSAGE)
        assertThat(CleartextPolicy.rejectDisallowedCleartext("http://192.168.1.1/v1"))
            .isEqualTo(CleartextPolicy.REJECT_MESSAGE)
        assertThat(CleartextPolicy.rejectDisallowedCleartext("http://evil.example/v1"))
            .isEqualTo(CleartextPolicy.REJECT_MESSAGE)
    }

    @Test
    fun allowedHostsSetMatchesPolicy() {
        assertThat(CleartextPolicy.ALLOWED_CLEARTEXT_HOSTS).containsAtLeast(
            "localhost", "127.0.0.1", "10.0.2.2", "::1",
        )
        assertThat(CleartextPolicy.isAllowedCleartextHost("127.0.0.1")).isTrue()
        assertThat(CleartextPolicy.isAllowedCleartextHost("evil.com")).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun chatRequestBuilder_rejectsPublicCleartext() {
        ChatRequestBuilder.build(
            ChatProviderConfig("http://api.openai.com", "sk-test", "gpt-4o-mini"),
            listOf(ChatMessageDto("user", "hi")),
            stream = false,
        )
    }

    @Test
    fun chatRequestBuilder_allowsLocalCleartext() {
        val req = ChatRequestBuilder.build(
            ChatProviderConfig("http://127.0.0.1:11434/v1", "ollama", "llama3.2"),
            listOf(ChatMessageDto("user", "hi")),
            stream = false,
        )
        assertThat(req.url.host).isEqualTo("127.0.0.1")
        assertThat(req.url.isHttps).isFalse()
    }
}
