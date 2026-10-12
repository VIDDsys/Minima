package com.minima.chat.core.agent.httptools

import com.minima.chat.core.agent.tools.AgentTool
import com.minima.chat.core.agent.tools.Grant
import com.minima.chat.core.agent.tools.ToolResult
import com.minima.chat.core.agent.tools.ToolSpec
import com.minima.chat.core.agent.tools.AddressLookup
import com.minima.chat.core.agent.tools.HostSafety
import com.minima.chat.core.agent.tools.SafeDns
import com.minima.chat.core.agent.tools.SystemAddressLookup
import com.minima.chat.core.agent.tools.looksPrivateHost
import com.minima.chat.core.network.api.SecretRedact
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Declarative HTTP tools from skill-pack `tools.json`.
 *
 * Host must be fixed (no `{{param}}` in host). Only https. Private hosts blocked.
 * Runtime DNS check (rebinding guard). No userinfo (@). Redirects disabled. Header CRLF rejected.
 * Methods: GET, POST, PUT, PATCH, HEAD. Response capped at 32KB + SecretRedact.
 */
data class DeclarativeHttpToolDef(
    val name: String,
    val description: String,
    val method: String,
    val url: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutSec: Int = 20,
    val parameters: JsonObject = emptyObjectSchema,
)

data class DeclarativeHttpParseResult(
    val tools: List<DeclarativeHttpToolDef>,
    val errors: List<String> = emptyList(),
)

private val emptyObjectSchema: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { })
}

object DeclarativeHttpToolsParser {
    private val json = Json { ignoreUnknownKeys = true }

    private val NAME_RE = Regex("""^[a-zA-Z][a-zA-Z0-9_]{0,47}$""")
    private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "HEAD")

    /** Reserved / builtin names that skill HTTP tools must not claim. */
    val reservedNames: Set<String> = setOf(
        "now", "device_info", "list_sandbox", "read_sandbox_file", "write_sandbox_file",
        "append_daily_note", "search_memory", "read_skill", "focus_task", "http_get",
        "list_dir", "read_file", "write_file", "mkdir",
    )

    fun parse(raw: String, sourceLabel: String = "tools.json"): DeclarativeHttpParseResult {
        val errors = mutableListOf<String>()
        val tools = mutableListOf<DeclarativeHttpToolDef>()
        try {
            val root = json.parseToJsonElement(raw).jsonObject
            val arr = root["tools"]?.jsonArray
                ?: return DeclarativeHttpParseResult(emptyList(), listOf("$sourceLabel: missing tools array"))
            arr.forEachIndexed { idx, el ->
                val obj = el as? JsonObject ?: run {
                    errors += "$sourceLabel[$idx]: not an object"
                    return@forEachIndexed
                }
                when (val def = parseOne(obj, "$sourceLabel[$idx]")) {
                    is ParseOne.Ok -> tools += def.def
                    is ParseOne.Err -> errors += def.message
                }
            }
        } catch (e: Exception) {
            return DeclarativeHttpParseResult(emptyList(), listOf("$sourceLabel: ${e.message ?: "parse error"}"))
        }
        return DeclarativeHttpParseResult(tools, errors)
    }

    private sealed class ParseOne {
        data class Ok(val def: DeclarativeHttpToolDef) : ParseOne()
        data class Err(val message: String) : ParseOne()
    }

    private fun parseOne(obj: JsonObject, label: String): ParseOne {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (!NAME_RE.matches(name)) {
            return ParseOne.Err("$label: invalid name (use [a-zA-Z][a-zA-Z0-9_]{0,47})")
        }
        if (name in reservedNames || name.startsWith("mcp__")) {
            return ParseOne.Err("$label: name '$name' is reserved")
        }
        val description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { "HTTP tool $name" }
        val method = (obj["method"]?.jsonPrimitive?.contentOrNull ?: "GET").trim().uppercase()
        if (method !in ALLOWED_METHODS) {
            return ParseOne.Err("$label: method must be one of $ALLOWED_METHODS")
        }
        val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val hostErr = validateUrlTemplate(url)
        if (hostErr != null) return ParseOne.Err("$label: $hostErr")

        val query = stringMap(obj["query"])
        val headers = stringMap(obj["headers"])
        val body = obj["body"]?.jsonPrimitive?.contentOrNull
        val timeoutSec = (obj["timeoutSec"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20)
            .coerceIn(5, 60)
        val parameters = obj["parameters"] as? JsonObject ?: emptyObjectSchema

        return ParseOne.Ok(
            DeclarativeHttpToolDef(
                name = name,
                description = description.take(500),
                method = method,
                url = url,
                query = query,
                headers = headers,
                body = body,
                timeoutSec = timeoutSec,
                parameters = parameters,
            ),
        )
    }

    private fun stringMap(el: kotlinx.serialization.json.JsonElement?): Map<String, String> {
        val obj = el as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val s = (v as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            k to s
        }.toMap()
    }

    /**
     * Host must be literal https host (no templates). Path/query may use {{param}}.
     */
    fun validateUrlTemplate(url: String): String? {
        if (url.isBlank()) return "url required"
        if (!url.startsWith("https://", ignoreCase = true)) return "Only https:// allowed"
        val afterScheme = url.substringAfter("://", "")
        val authority = afterScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        if (authority.isBlank()) return "missing host"
        if ("{{" in authority || "}}" in authority) {
            return "Host must be fixed (no {{param}} in host)"
        }
        if ("@" in authority) {
            return "Userinfo (@) not allowed in URL"
        }
        if ("\\" in authority || authority.any { it.isWhitespace() }) {
            return "bad host"
        }
        val probe = url.replace(Regex("""\{\{[^}]*\}\}"""), "x")
        return try {
            val uri = URI(probe)
            if (!uri.scheme.equals("https", ignoreCase = true)) return "Only https:// allowed"
            if (!uri.userInfo.isNullOrEmpty()) return "Userinfo (@) not allowed in URL"
            val host = uri.host?.lowercase() ?: return "bad host"
            if (looksPrivateHost("https://$host/")) return "Private/link-local hosts blocked"
            null
        } catch (_: Exception) {
            "bad URL"
        }
    }

    /**
     * Post-render URL must stay https, same host as template, and non-private.
     * @return error message or null if ok
     */
    fun assertSafeFinalUrl(templateUrl: String, finalUrl: String): String? {
        if (!finalUrl.startsWith("https://", ignoreCase = true)) return "Only https:// allowed"
        if (finalUrl.any { it == '\r' || it == '\n' || it == '\u0000' }) return "Invalid URL"
        if (looksPrivateHost(finalUrl)) return "Private/link-local hosts blocked"
        val expectedHost = try {
            val probe = templateUrl.replace(Regex("""\{\{[^}]*\}\}"""), "x")
            URI(probe).host?.lowercase()
        } catch (_: Exception) {
            null
        }
        val actual = try {
            val uri = URI(finalUrl)
            if (!uri.userInfo.isNullOrEmpty()) return "Userinfo (@) not allowed in URL"
            if (!uri.scheme.equals("https", ignoreCase = true)) return "Only https:// allowed"
            uri.host?.lowercase()
        } catch (_: Exception) {
            null
        }
        if (expectedHost == null || actual == null || expectedHost != actual) {
            return "Host mismatch after template render"
        }
        if (looksPrivateHost("https://$actual/")) return "Private/link-local hosts blocked"
        return null
    }
}

object HttpTemplate {
    private val TOKEN = Regex("""\{\{\s*([a-zA-Z][a-zA-Z0-9_]*)\s*\}\}""")

    fun render(template: String, args: Map<String, String>): String =
        TOKEN.replace(template) { m ->
            val key = m.groupValues[1]
            args[key].orEmpty()
        }

    fun parseArgs(argumentsJson: String): Map<String, String> = try {
        val obj = Json.parseToJsonElement(argumentsJson).jsonObject
        obj.mapNotNull { (k, v) ->
            when (v) {
                is JsonPrimitive -> k to (v.contentOrNull ?: v.toString())
                else -> k to v.toString()
            }
        }.toMap()
    } catch (_: Exception) {
        emptyMap()
    }
}

class DeclarativeHttpTool(
    private val def: DeclarativeHttpToolDef,
    private val addressLookup: AddressLookup = SystemAddressLookup,
    private val clientFactory: (timeoutSec: Int) -> OkHttpClient = Companion::defaultClient,
) : AgentTool {
    override val spec = ToolSpec(
        name = def.name,
        description = def.description,
        parameters = def.parameters,
        requiresGrant = Grant.Network,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val args = HttpTemplate.parseArgs(argumentsJson.ifBlank { "{}" })
        val urlRendered = HttpTemplate.render(def.url, args)
        val queryExtra = def.query.mapValues { HttpTemplate.render(it.value, args) }
            .filterValues { it.isNotBlank() }
        val finalUrl = mergeQuery(urlRendered, queryExtra)
            ?: return ToolResult(false, "Failed to build URL")

        DeclarativeHttpToolsParser.assertSafeFinalUrl(def.url, finalUrl)?.let {
            return ToolResult(false, it)
        }

        val host = try {
            URI(finalUrl).host
        } catch (_: Exception) {
            null
        } ?: return ToolResult(false, "bad host")
        HostSafety.assertResolvedHostSafe(host, addressLookup)?.let {
            return ToolResult(false, it)
        }

        val headers = def.headers.mapValues { HttpTemplate.render(it.value, args) }
        for ((k, v) in headers) {
            if (k.isBlank()) continue
            if (containsCrlf(k) || containsCrlf(v)) {
                return ToolResult(false, "Invalid header (CRLF not allowed)")
            }
        }
        val bodyText = def.body?.let { HttpTemplate.render(it, args) }

        return try {
            val b = Request.Builder().url(finalUrl)
            headers.forEach { (k, v) ->
                if (k.isNotBlank() && !k.equals("Host", ignoreCase = true)) {
                    b.header(k, v)
                }
            }
            val media = "application/json; charset=utf-8".toMediaType()
            when (def.method) {
                "GET" -> b.get()
                "HEAD" -> b.head()
                "POST" -> b.post((bodyText ?: "").toRequestBody(media))
                "PUT" -> b.put((bodyText ?: "").toRequestBody(media))
                "PATCH" -> b.patch((bodyText ?: "").toRequestBody(media))
                else -> return ToolResult(false, "Unsupported method")
            }
            val client = clientFactory(def.timeoutSec)
            client.newCall(b.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty().take(32 * 1024)
                val redacted = SecretRedact.redact(raw)
                when {
                    resp.code in 300..399 ->
                        ToolResult(false, "Redirects are blocked (HTTP ${resp.code})")
                    !resp.isSuccessful ->
                        ToolResult(false, "HTTP ${resp.code}: $redacted")
                    else ->
                        ToolResult(true, redacted.ifBlank { "(empty body)" })
                }
            }
        } catch (e: Exception) {
            ToolResult(false, SecretRedact.redact(e.message ?: "Network error"))
        }
    }

    companion object {
        fun defaultClient(timeoutSec: Int, dns: okhttp3.Dns = SafeDns()): OkHttpClient =
            OkHttpClient.Builder()
                .dns(dns)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
                .callTimeout((timeoutSec + 5).toLong(), TimeUnit.SECONDS)
                .build()

        fun mergeQuery(baseUrl: String, extra: Map<String, String>): String? {
            if (extra.isEmpty()) return baseUrl
            return try {
                val uri = URI(baseUrl)
                val existing = uri.query?.split("&")?.filter { it.isNotBlank() }.orEmpty()
                val encodedExtra = extra.map { (k, v) ->
                    "${URLEncoder.encode(k, Charsets.UTF_8.name())}=${URLEncoder.encode(v, Charsets.UTF_8.name())}"
                }
                val q = (existing + encodedExtra).joinToString("&")
                URI(
                    uri.scheme,
                    uri.authority,
                    uri.path,
                    q,
                    uri.fragment,
                ).toString()
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun containsCrlf(s: String): Boolean =
    s.any { it == '\r' || it == '\n' || it == '\u0000' }
