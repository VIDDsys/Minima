package com.minima.chat.core.agent.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Remote MCP server config (Streamable HTTP / SSE).
 * stdio MCP is documented as optional/external (Termux) — not embedded.
 *
 * Auth without browser OAuth:
 * - [bearerToken]: optional dedicated token → `Authorization: Bearer …`
 * - [headers]: arbitrary static headers (win over bearer on Authorization collision)
 */
@Serializable
data class McpServerConfig(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val bearerToken: String = "",
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class McpConfigFile(
    val servers: List<McpServerConfig> = emptyList(),
)

@Serializable
data class McpServerHealth(
    val name: String,
    val url: String,
    val ok: Boolean,
    val toolCount: Int,
    val message: String,
    val checkedAtMs: Long = 0L,
)

object McpHealthCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(list: List<McpServerHealth>): String =
        json.encodeToString(kotlinx.serialization.builtins.ListSerializer(McpServerHealth.serializer()), list)

    fun decode(raw: String): List<McpServerHealth> = try {
        if (raw.isBlank()) emptyList()
        else json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(McpServerHealth.serializer()),
            raw,
        )
    } catch (_: Exception) {
        emptyList()
    }
}

object McpConfigParser {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun parse(raw: String): McpConfigFile = try {
        json.decodeFromString(McpConfigFile.serializer(), raw)
    } catch (_: Exception) {
        McpConfigFile()
    }

    fun encode(file: McpConfigFile): String =
        json.encodeToString(McpConfigFile.serializer(), file)
}
