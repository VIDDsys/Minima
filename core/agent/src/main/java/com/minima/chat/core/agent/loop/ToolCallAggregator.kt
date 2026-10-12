package com.minima.chat.core.agent.loop

import com.minima.chat.core.network.dto.ToolCallDeltaDto
import com.minima.chat.core.network.dto.ToolCallDto
import com.minima.chat.core.network.dto.ToolCallFunctionDto

/** Accumulates streamed tool_calls deltas by index into complete ToolCallDto list. */
class ToolCallAggregator {
    private val byIndex = sortedMapOf<Int, Acc>()

    data class Acc(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )

    fun accept(deltas: List<ToolCallDeltaDto>) {
        for (d in deltas) {
            val acc = byIndex.getOrPut(d.index) { Acc() }
            if (!d.id.isNullOrEmpty()) acc.id = d.id!!
            d.function?.name?.let { if (it.isNotEmpty()) acc.name = it }
            d.function?.arguments?.let { acc.arguments.append(it) }
        }
    }

    fun isEmpty(): Boolean = byIndex.isEmpty()

    fun build(): List<ToolCallDto> = byIndex.entries
        .filter { it.value.name.isNotBlank() }
        .map { (index, acc) ->
            ToolCallDto(
                // Provider ids preferred; synthetic ids must be unique across turns/sessions
                // (Room look-ups key on toolCallId within a session).
                id = acc.id.ifEmpty { "syn_${index}_${acc.name}_${java.util.UUID.randomUUID()}" },
                function = ToolCallFunctionDto(
                    name = acc.name,
                    arguments = acc.arguments.toString().ifEmpty { "{}" },
                ),
            )
        }

    fun clear() = byIndex.clear()
}
