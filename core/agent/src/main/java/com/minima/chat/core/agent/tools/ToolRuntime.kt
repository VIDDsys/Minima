package com.minima.chat.core.agent.tools

import com.minima.chat.core.network.dto.ToolDefinitionDto

/**
 * Narrow surface AgentLoop needs from [ToolRegistry] — also implemented by test fakes.
 */
interface ToolRuntime {
    fun definitions(): List<ToolDefinitionDto>
    suspend fun dispatch(name: String, argumentsJson: String): ToolResult
}
