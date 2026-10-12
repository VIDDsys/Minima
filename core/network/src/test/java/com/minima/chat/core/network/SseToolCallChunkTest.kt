package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.sse.SseParser
import org.junit.Test

class SseToolCallChunkTest {
    @Test
    fun parsesContentDelta() {
        val data = """{"choices":[{"delta":{"content":"Hi"},"index":0}]}"""
        val chunk = SseParser.parseChunk(data)
        assertThat(chunk?.content).isEqualTo("Hi")
    }

    @Test
    fun parsesToolCallDelta() {
        val data = """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"now","arguments":"{}"}}]},"index":0}]}"""
        val chunk = SseParser.parseChunk(data)
        assertThat(chunk?.toolCalls).isNotNull()
        assertThat(chunk!!.toolCalls!![0].id).isEqualTo("call_1")
        assertThat(chunk.toolCalls!![0].function?.name).isEqualTo("now")
    }

    @Test
    fun doneReturnsNull() {
        assertThat(SseParser.parseChunk("[DONE]")).isNull()
    }
}
