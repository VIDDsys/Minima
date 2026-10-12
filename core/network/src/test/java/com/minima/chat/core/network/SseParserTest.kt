package com.minima.chat.core.network

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.network.sse.SseParser
import org.junit.Test

class SseParserTest {
    @Test
    fun extractsDeltasFromSseBody() {
        val body = """
            data: {"choices":[{"delta":{"content":"Hello"}}]}
            
            data: {"choices":[{"delta":{"content":" world"}}]}
            
            data: [DONE]
            
        """.trimIndent()
        assertThat(SseParser.extractContentDeltas(body)).isEqualTo("Hello world")
    }

    @Test
    fun parseDataLine_doneReturnsNull() {
        assertThat(SseParser.parseDataLine("[DONE]")).isNull()
    }
}
