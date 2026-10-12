package com.minima.chat.core.agent.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Streamable HTTP MCP client (JSON-RPC over POST).
 * Spec: initialize → tools/list → tools/call.
 * Persists [Mcp-Session-Id] from responses and reuses it on later requests
 * (unless the user sets that header statically). Tolerant of servers that omit it.
 * Retries once on transient network failures.
 */
class RemoteMcpClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
    private val sessions: McpSessionStore = McpSessionStore(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val idSeq = AtomicLong(1)
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    data class DiscoveredTool(
        val serverName: String,
        val name: String,
        val description: String,
        val inputSchema: JsonObject,
    )

    fun clearSession(server: McpServerConfig) {
        sessions.clear(server)
    }

    /** Test / diagnostics: current stored session id for [server], if any. */
    fun peekSession(server: McpServerConfig): String? = sessions.get(server)

    fun listTools(server: McpServerConfig): Result<List<DiscoveredTool>> {
        if (!server.enabled) return Result.success(emptyList())
        if (server.url.isBlank()) return Result.failure(IllegalArgumentException("Empty MCP URL for «${server.name}»"))
        return withRetry(server.name) {
            initialize(server)
            val resp = rpc(server, "tools/list", buildJsonObject {})
            val toolsEl = resp["result"]?.let { (it as? JsonObject)?.get("tools") }
                ?: return@withRetry Result.failure(
                    IllegalStateException("MCP «${server.name}»: no tools in response"),
                )
            val arr = toolsEl as? kotlinx.serialization.json.JsonArray
                ?: return@withRetry Result.failure(
                    IllegalStateException("MCP «${server.name}»: tools is not an array"),
                )
            val out = arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val desc = (obj["description"] as? JsonPrimitive)?.content ?: ""
                val schema = (obj["inputSchema"] as? JsonObject)
                    ?: (obj["input_schema"] as? JsonObject)
                    ?: buildJsonObject { put("type", "object") }
                DiscoveredTool(server.name, name, desc, schema)
            }
            Result.success(out)
        }
    }

    fun callTool(server: McpServerConfig, toolName: String, argumentsJson: String): Result<String> {
        if (!server.enabled) {
            return Result.failure(IllegalStateException("MCP «${server.name}» is disabled"))
        }
        return withRetry(server.name) {
            val argsEl: JsonElement = try {
                json.parseToJsonElement(argumentsJson.ifBlank { "{}" })
            } catch (_: Exception) {
                buildJsonObject {}
            }
            val params = buildJsonObject {
                put("name", toolName)
                put("arguments", argsEl)
            }
            val resp = rpc(server, "tools/call", params)
            val result = resp["result"]
            val err = resp["error"]
            when {
                err != null -> Result.failure(
                    IllegalStateException("MCP «${server.name}» tool error: ${summarizeJsonError(err)}"),
                )
                result != null -> Result.success(result.toString().take(64 * 1024))
                else -> Result.failure(IllegalStateException("MCP «${server.name}»: empty result"))
            }
        }
    }

    private fun <T> withRetry(serverName: String, block: () -> Result<T>): Result<T> {
        val first = runCatching { block() }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) },
        )
        if (first.isSuccess) return first
        val err = first.exceptionOrNull() ?: return first
        if (!isTransient(err)) {
            return Result.failure(IllegalStateException(friendlyError(serverName, err), err))
        }
        try {
            Thread.sleep(700)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return runCatching { block() }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Result.failure(IllegalStateException(friendlyError(serverName, e) + " (after retry)", e))
            },
        ).let { second ->
            if (second.isFailure && second.exceptionOrNull() != null) {
                second
            } else if (second.isFailure) {
                Result.failure(IllegalStateException(friendlyError(serverName, err) + " (after retry)", err))
            } else {
                second
            }
        }
    }

    private fun initialize(server: McpServerConfig) {
        // Fresh handshake: drop stale session before initialize (spec: first POST has no session).
        clearSession(server)
        val params = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject {
                put("name", "minima")
                put("version", "2.37.0")
            })
        }
        runCatching { rpc(server, "initialize", params) }
        runCatching { notify(server, "notifications/initialized", buildJsonObject {}) }
    }

    private fun rpc(server: McpServerConfig, method: String, params: JsonObject): JsonObject {
        val id = idSeq.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val payload = json.encodeToString(JsonObject.serializer(), body)
        val builder = Request.Builder()
            .url(server.url.trim())
            .post(payload.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
        sessions.applyTo(builder, server)
        val response = okHttpClient.newCall(builder.build()).execute()
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                maybeClearExpiredSession(server, resp.code)
                throw IllegalStateException("HTTP ${resp.code}: ${raw.take(280)}")
            }
            sessions.captureFrom(server, resp.headers)
            val jsonText = extractJson(raw)
            return json.decodeFromString(JsonObject.serializer(), jsonText)
        }
    }

    private fun notify(server: McpServerConfig, method: String, params: JsonObject) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        val payload = json.encodeToString(JsonObject.serializer(), body)
        val builder = Request.Builder()
            .url(server.url.trim())
            .post(payload.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
        sessions.applyTo(builder, server)
        okHttpClient.newCall(builder.build()).execute().use { resp ->
            if (resp.isSuccessful) {
                sessions.captureFrom(server, resp.headers)
            } else {
                maybeClearExpiredSession(server, resp.code)
            }
        }
    }

    private fun maybeClearExpiredSession(server: McpServerConfig, code: Int) {
        // 401 / 404 often mean missing or expired MCP session for Streamable HTTP.
        if (code == 401 || code == 404) {
            clearSession(server)
        }
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        for (line in trimmed.lineSequence()) {
            if (line.startsWith("data:")) {
                val payload = line.removePrefix("data:").trim()
                if (payload.startsWith("{")) return payload
            }
        }
        return trimmed
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun isTransient(e: Throwable): Boolean {
            var cur: Throwable? = e
            while (cur != null) {
                when (cur) {
                    is SocketTimeoutException -> return true
                    is UnknownHostException -> return false
                    is IOException -> {
                        val msg = cur.message.orEmpty().lowercase()
                        if (msg.contains("timeout") || msg.contains("reset") || msg.contains("broken pipe")) {
                            return true
                        }
                    }
                }
                val msg = cur.message.orEmpty()
                if (msg.contains("HTTP 502") || msg.contains("HTTP 503") || msg.contains("HTTP 504")) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }

        fun friendlyError(serverName: String, e: Throwable): String {
            var cur: Throwable? = e
            while (cur != null) {
                when (cur) {
                    is UnknownHostException ->
                        return "MCP «$serverName»: DNS failed — check URL / network"
                    is SocketTimeoutException ->
                        return "MCP «$serverName»: timed out"
                    is IOException -> {
                        val m = cur.message?.take(120)
                        if (!m.isNullOrBlank()) return "MCP «$serverName»: $m"
                    }
                }
                val msg = cur.message.orEmpty()
                if (msg.startsWith("HTTP ")) return "MCP «$serverName»: $msg"
                cur = cur.cause
            }
            return "MCP «$serverName»: ${e.message?.take(160) ?: e.javaClass.simpleName}"
        }

        fun summarizeJsonError(err: JsonElement): String {
            val obj = err as? JsonObject ?: return err.toString().take(200)
            val message = (obj["message"] as? JsonPrimitive)?.content
            val code = obj["code"]
            return buildString {
                if (code != null) append("code=$code ")
                append(message ?: err.toString().take(160))
            }.trim()
        }
    }
}

/** Bridge MCP tools into AgentTool wrappers with namespaced names. */
class McpToolBridge(
    private val client: RemoteMcpClient = RemoteMcpClient(),
) {
    fun discover(servers: List<McpServerConfig>): List<AgentToolAdapter> {
        val out = mutableListOf<AgentToolAdapter>()
        for (server in servers.filter { it.enabled }) {
            val result = client.listTools(server)
            val tools = result.getOrNull() ?: continue
            for (t in tools) {
                val ns = "mcp__${sanitize(server.name)}__${sanitize(t.name)}"
                out += AgentToolAdapter(
                    namespacedName = ns,
                    description = "[MCP:${server.name}] ${t.description}",
                    parameters = t.inputSchema,
                    server = server,
                    remoteName = t.name,
                    client = client,
                )
            }
        }
        return out
    }

    /** Discover and collect per-server failures (for health UI). */
    fun discoverWithErrors(servers: List<McpServerConfig>): Pair<List<AgentToolAdapter>, List<McpServerHealth>> {
        val tools = mutableListOf<AgentToolAdapter>()
        val health = mutableListOf<McpServerHealth>()
        for (server in servers) {
            if (!server.enabled) {
                health += McpServerHealth(server.name, server.url, ok = false, toolCount = 0, message = "disabled")
                continue
            }
            if (server.url.isBlank()) {
                health += McpServerHealth(server.name, server.url, ok = false, toolCount = 0, message = "empty url")
                continue
            }
            val result = client.listTools(server)
            result.fold(
                onSuccess = { list ->
                    health += McpServerHealth(server.name, server.url, ok = true, toolCount = list.size, message = McpAuthHeaders.redact("${list.size} tools"))
                    for (t in list) {
                        val ns = "mcp__${sanitize(server.name)}__${sanitize(t.name)}"
                        tools += AgentToolAdapter(
                            namespacedName = ns,
                            description = "[MCP:${server.name}] ${t.description}",
                            parameters = t.inputSchema,
                            server = server,
                            remoteName = t.name,
                            client = client,
                        )
                    }
                },
                onFailure = { e ->
                    health += McpServerHealth(
                        server.name,
                        server.url,
                        ok = false,
                        toolCount = 0,
                        message = McpAuthHeaders.redact(e.message?.take(160) ?: "error"),
                    )
                },
            )
        }
        return tools to health
    }

    private fun sanitize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifEmpty { "x" }
}

class AgentToolAdapter(
    val namespacedName: String,
    val description: String,
    val parameters: JsonObject,
    private val server: McpServerConfig,
    private val remoteName: String,
    private val client: RemoteMcpClient,
) : com.minima.chat.core.agent.tools.AgentTool {
    override val spec = com.minima.chat.core.agent.tools.ToolSpec(
        name = namespacedName,
        description = description,
        parameters = parameters,
        requiresGrant = com.minima.chat.core.agent.tools.Grant.Network,
    )

    override suspend fun invoke(argumentsJson: String): com.minima.chat.core.agent.tools.ToolResult {
        val r = client.callTool(server, remoteName, argumentsJson)
        return r.fold(
            onSuccess = { com.minima.chat.core.agent.tools.ToolResult(true, it) },
            onFailure = { com.minima.chat.core.agent.tools.ToolResult(false, it.message ?: "MCP call failed") },
        )
    }
}
