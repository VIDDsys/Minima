package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.loop.ToolCallAggregator
import com.minima.chat.core.network.dto.ToolCallDeltaDto
import com.minima.chat.core.network.dto.ToolCallFunctionDeltaDto
import org.junit.Test

class ToolCallAggregatorTest {
    @Test
    fun aggregatesFragmentedArguments() {
        val agg = ToolCallAggregator()
        agg.accept(
            listOf(
                ToolCallDeltaDto(0, id = "c1", function = ToolCallFunctionDeltaDto(name = "http_get", arguments = "{\"url\":")),
                ToolCallDeltaDto(0, function = ToolCallFunctionDeltaDto(arguments = "\"https://example.com\"}")),
            ),
        )
        val calls = agg.build()
        assertThat(calls).hasSize(1)
        assertThat(calls[0].id).isEqualTo("c1")
        assertThat(calls[0].function.name).isEqualTo("http_get")
        assertThat(calls[0].function.arguments).contains("example.com")
    }

    @Test
    fun aggregatesParallelToolCallsByIndex() {
        val agg = ToolCallAggregator()
        agg.accept(
            listOf(
                ToolCallDeltaDto(0, id = "a", function = ToolCallFunctionDeltaDto(name = "now", arguments = "{}")),
                ToolCallDeltaDto(1, id = "b", function = ToolCallFunctionDeltaDto(name = "http_get", arguments = "{\"url\":\"https://x\"}")),
            ),
        )
        val calls = agg.build()
        assertThat(calls).hasSize(2)
        assertThat(calls[0].id).isEqualTo("a")
        assertThat(calls[0].function.name).isEqualTo("now")
        assertThat(calls[1].id).isEqualTo("b")
        assertThat(calls[1].function.name).isEqualTo("http_get")
    }

    @Test
    fun skipsBlankToolNames() {
        val agg = ToolCallAggregator()
        agg.accept(
            listOf(
                ToolCallDeltaDto(0, id = "ghost", function = ToolCallFunctionDeltaDto(name = "", arguments = "{}")),
                ToolCallDeltaDto(1, id = "ok", function = ToolCallFunctionDeltaDto(name = "now", arguments = "{}")),
            ),
        )
        val calls = agg.build()
        assertThat(calls).hasSize(1)
        assertThat(calls[0].function.name).isEqualTo("now")
    }

    @Test
    fun syntheticIdsAreUniqueWhenProviderOmitsId() {
        val a = ToolCallAggregator()
        a.accept(
            listOf(
                ToolCallDeltaDto(0, function = ToolCallFunctionDeltaDto(name = "now", arguments = "{}")),
                ToolCallDeltaDto(1, function = ToolCallFunctionDeltaDto(name = "now", arguments = "{}")),
            ),
        )
        val calls = a.build()
        assertThat(calls).hasSize(2)
        assertThat(calls[0].id).startsWith("syn_0_now_")
        assertThat(calls[1].id).startsWith("syn_1_now_")
        assertThat(calls[0].id).isNotEqualTo(calls[1].id)

        val b = ToolCallAggregator()
        b.accept(
            listOf(ToolCallDeltaDto(0, function = ToolCallFunctionDeltaDto(name = "now", arguments = "{}"))),
        )
        assertThat(b.build()[0].id).isNotEqualTo(calls[0].id)
    }
}
