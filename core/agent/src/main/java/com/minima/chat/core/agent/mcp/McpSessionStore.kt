package com.minima.chat.core.agent.mcp

import okhttp3.Headers
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory MCP Streamable HTTP session ids ([Mcp-Session-Id]), keyed by server identity.
 * User-configured static headers (bearer + [McpServerConfig.headers]) always win over a stored session.
 */
class McpSessionStore {
    private val sessions = ConcurrentHashMap<String, String>()

    fun sessionKey(server: McpServerConfig): String = companionKey(server)

    fun get(server: McpServerConfig): String? = sessions[sessionKey(server)]

    fun put(server: McpServerConfig, sessionId: String) {
        val id = sessionId.trim()
        if (id.isEmpty()) return
        sessions[sessionKey(server)] = id
    }

    fun clear(server: McpServerConfig) {
        sessions.remove(sessionKey(server))
    }

    fun clearAll() {
        sessions.clear()
    }

    /** Capture from a successful response; no-op if header absent. */
    fun captureFrom(server: McpServerConfig, responseHeaders: Headers) {
        extractSessionId(responseHeaders)?.let { put(server, it) }
    }

    /**
     * Apply stored session onto [builder] unless the user already set that header
     * (case-insensitive) in effective static headers. Then apply effective user headers
     * (bearerToken + headers) so user values win on collision.
     */
    fun applyTo(builder: Request.Builder, server: McpServerConfig) {
        val userHeaders = McpAuthHeaders.effectiveUserHeaders(server)
        val userHasSession = userSetsSessionHeader(userHeaders)
        if (!userHasSession) {
            get(server)?.let { builder.header(SESSION_HEADER, it) }
        }
        userHeaders.forEach { (k, v) -> builder.header(k, v) }
    }

    companion object {
        const val SESSION_HEADER = "Mcp-Session-Id"

        fun companionKey(server: McpServerConfig): String {
            val name = server.name.trim().lowercase()
            val url = normalizeUrl(server.url)
            return "$name|$url"
        }

        fun normalizeUrl(url: String): String {
            var u = url.trim()
            while (u.endsWith("/") && u.length > 1) {
                u = u.dropLast(1)
            }
            return u
        }

        fun userSetsSessionHeader(headers: Map<String, String>): Boolean =
            headers.keys.any { it.equals(SESSION_HEADER, ignoreCase = true) }

        fun extractSessionId(headers: Headers): String? {
            val raw = headers[SESSION_HEADER] ?: return null
            val id = raw.trim()
            return id.takeIf { it.isNotEmpty() }
        }

        /** Pure merge for unit tests: stored session + effective user headers → final map. */
        fun mergeRequestHeaders(
            storedSessionId: String?,
            userHeaders: Map<String, String>,
        ): Map<String, String> {
            val out = linkedMapOf<String, String>()
            if (!userSetsSessionHeader(userHeaders) && !storedSessionId.isNullOrBlank()) {
                out[SESSION_HEADER] = storedSessionId.trim()
            }
            userHeaders.forEach { (k, v) -> out[k] = v }
            return out
        }

        /**
         * Full wire merge: bearer + custom headers + optional stored session.
         * Same precedence as [applyTo].
         */
        fun mergeForServer(
            server: McpServerConfig,
            storedSessionId: String?,
        ): Map<String, String> =
            mergeRequestHeaders(storedSessionId, McpAuthHeaders.effectiveUserHeaders(server))
    }
}
