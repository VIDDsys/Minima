package com.minima.chat.core.agent.mcp

import com.minima.chat.core.network.api.SecretRedact

/**
 * Builds effective static headers for a remote MCP server.
 *
 * Precedence (later wins on same header name, case-insensitive for Authorization / session):
 * 1. [McpServerConfig.bearerToken] → `Authorization: Bearer <token>` when non-blank
 * 2. [McpServerConfig.headers] (user-static; wins over bearer if Authorization is set here)
 *
 * Session id is applied separately by [McpSessionStore.applyTo] and still loses to user-static
 * `Mcp-Session-Id` in the effective map.
 */
object McpAuthHeaders {
    const val AUTHORIZATION = "Authorization"

    /**
     * User-static headers that go on the wire before session merge:
     * optional Bearer from [bearerToken], then explicit [headers] (explicit wins).
     */
    fun effectiveUserHeaders(server: McpServerConfig): Map<String, String> =
        merge(bearerToken = server.bearerToken, headers = server.headers)

    fun merge(
        bearerToken: String?,
        headers: Map<String, String>,
    ): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val token = bearerToken?.trim().orEmpty()
        if (token.isNotEmpty()) {
            val value = if (token.startsWith("Bearer ", ignoreCase = true)) {
                token
            } else {
                "Bearer $token"
            }
            out[AUTHORIZATION] = value
        }
        headers.forEach { (k, v) ->
            val key = k.trim()
            if (key.isEmpty()) return@forEach
            out[key] = v
        }
        return out
    }

    /** True if [headers] already sets Authorization (any casing). */
    fun userSetsAuthorization(headers: Map<String, String>): Boolean =
        headers.keys.any { it.equals(AUTHORIZATION, ignoreCase = true) }

    /**
     * Encode custom headers as ink-minimal editor lines: `Name: Value` per line.
     * Blank lines skipped. Values may contain `:`.
     */
    fun headersToLines(headers: Map<String, String>): String =
        headers.entries.joinToString("\n") { (k, v) -> "$k: $v" }

    /**
     * Parse editor lines into a header map. First `:` separates name and value.
     * Invalid / empty names are skipped. Later lines with the same name win.
     */
    fun linesToHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val idx = trimmed.indexOf(':')
            if (idx <= 0) continue
            val name = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (name.isEmpty()) continue
            out[name] = value
        }
        return out
    }

    /** Redact secrets in diagnostic / health strings (Bearer, sk-, api_key, …). */
    fun redact(text: String?): String = SecretRedact.redact(text)

    /**
     * Patch one server in a config file: update bearerToken and/or headers by name.
     * Returns updated JSON string, or original [rawJson] if parse fails / server missing.
     */
    fun patchServerAuth(
        rawJson: String,
        serverName: String,
        bearerToken: String? = null,
        headers: Map<String, String>? = null,
    ): String {
        val file = McpConfigParser.parse(rawJson)
        if (file.servers.isEmpty()) return rawJson
        val target = serverName.trim()
        var changed = false
        val updated = file.servers.map { s ->
            if (!s.name.equals(target, ignoreCase = true)) return@map s
            changed = true
            s.copy(
                bearerToken = bearerToken ?: s.bearerToken,
                headers = headers ?: s.headers,
            )
        }
        if (!changed) return rawJson
        return McpConfigParser.encode(McpConfigFile(updated))
    }
}
