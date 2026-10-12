package com.minima.chat.core.agent.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val requiresGrant: Grant = Grant.None,
)

enum class Grant {
    None,
    /** App-private sandbox files only */
    Sandbox,
    /** User-granted SAF tree / public Documents */
    PublicFiles,
    Network,
}

data class ToolResult(
    val ok: Boolean,
    val output: String,
)

interface AgentTool {
    val spec: ToolSpec
    suspend fun invoke(argumentsJson: String): ToolResult
}

object JsonSchema {
    fun obj(vararg props: Pair<String, JsonObject>, required: List<String> = emptyList()) =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                props.forEach { (k, v) -> put(k, v) }
            }
            if (required.isNotEmpty()) {
                put("required", kotlinx.serialization.json.JsonArray(required.map {
                    kotlinx.serialization.json.JsonPrimitive(it)
                }))
            }
        }

    fun string(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    fun boolean(desc: String) = buildJsonObject {
        put("type", "boolean")
        put("description", desc)
    }

    fun integer(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }
}
