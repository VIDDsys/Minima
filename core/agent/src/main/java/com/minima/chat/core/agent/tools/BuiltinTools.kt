package com.minima.chat.core.agent.tools

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Policy for file tools — sandbox vs user-granted public tree. */
data class ToolPolicy(
    val sandboxOnly: Boolean = false,
    val publicTreeUri: String? = null,
    val allowNetwork: Boolean = true,
)

@Singleton
class ToolPolicyStore @Inject constructor() {
    @Volatile
    var policy: ToolPolicy = ToolPolicy()
}

class NowTool : AgentTool {
    override val spec = ToolSpec(
        name = "now",
        description = "Current local date/time on the device.",
        parameters = JsonSchema.obj(),
        requiresGrant = Grant.None,
    )
    override suspend fun invoke(argumentsJson: String): ToolResult {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        return ToolResult(true, fmt.format(Date()))
    }
}

class ListSandboxTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    override val spec = ToolSpec(
        name = "list_sandbox",
        description = "List files in the app private workspace (files/minima_workspace).",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative path under workspace; default '.'"),
        ),
        requiresGrant = Grant.Sandbox,
    )
    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: "."
        val root = workspaceRoot(context)
        val target = resolveSafe(root, rel) ?: return ToolResult(false, "Path escapes workspace")
        if (!target.exists()) return ToolResult(false, "Not found: $rel")
        if (!target.isDirectory) return ToolResult(false, "Not a directory")
        val listing = target.listFiles()?.sortedBy { it.name }?.joinToString("\n") {
            val kind = if (it.isDirectory) "dir" else "file"
            "$kind\t${it.name}\t${it.length()}"
        } ?: "(empty)"
        return ToolResult(true, listing)
    }
}

class ReadSandboxTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val spec = ToolSpec(
        name = "read_sandbox_file",
        description = "Read a UTF-8 text file from the app private workspace (max 64KB).",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative path under workspace"),
            required = listOf("path"),
        ),
        requiresGrant = Grant.Sandbox,
    )
    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: return ToolResult(false, "path required")
        val root = workspaceRoot(context)
        val target = resolveSafe(root, rel) ?: return ToolResult(false, "Path escapes workspace")
        if (!target.isFile) return ToolResult(false, "Not a file")
        if (target.length() > 64 * 1024) return ToolResult(false, "File too large (>64KB)")
        return ToolResult(true, target.readText())
    }
}

class WriteSandboxTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val spec = ToolSpec(
        name = "write_sandbox_file",
        description = "Write UTF-8 text to a file in the app private workspace.",
        parameters = JsonSchema.obj(
            "path" to JsonSchema.string("Relative path under workspace"),
            "content" to JsonSchema.string("File contents"),
            required = listOf("path", "content"),
        ),
        requiresGrant = Grant.Sandbox,
    )
    override suspend fun invoke(argumentsJson: String): ToolResult {
        val rel = parseString(argumentsJson, "path") ?: return ToolResult(false, "path required")
        val content = parseString(argumentsJson, "content") ?: return ToolResult(false, "content required")
        val root = workspaceRoot(context)
        val target = resolveSafe(root, rel) ?: return ToolResult(false, "Path escapes workspace")
        target.parentFile?.mkdirs()
        target.writeText(content)
        return ToolResult(true, "Wrote ${content.length} bytes to $rel")
    }
}

class HttpGetTool @Inject constructor(
    private val policyStore: ToolPolicyStore,
) : AgentTool {
    /** Test seam: injectable DNS lookup for offline rebinding checks. */
    internal var addressLookup: AddressLookup = SystemAddressLookup

    private fun buildClient(): OkHttpClient =
        OkHttpClient.Builder()
            .dns(SafeDns(addressLookup))
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /** Lazily built so tests can swap [addressLookup] before first use. */
    @Volatile
    private var clientField: OkHttpClient? = null

    private val client: OkHttpClient
        get() = clientField ?: buildClient().also { clientField = it }

    /** Test-only: rebuild client after changing [addressLookup]. */
    internal fun resetClientForTest() {
        clientField = null
    }

    override val spec = ToolSpec(
        name = "http_get",
        description = "HTTP GET a public https URL; returns truncated text body (max 32KB).",
        parameters = JsonSchema.obj(
            "url" to JsonSchema.string("https URL"),
            required = listOf("url"),
        ),
        requiresGrant = Grant.Network,
    )
    override suspend fun invoke(argumentsJson: String): ToolResult {
        if (!policyStore.policy.allowNetwork) return ToolResult(false, "Network tools disabled")
        val url = parseString(argumentsJson, "url") ?: return ToolResult(false, "url required")
        if (!url.startsWith("https://")) return ToolResult(false, "Only https:// allowed")
        if (looksPrivateHost(url)) return ToolResult(false, "Private/link-local hosts blocked")
        val host = try {
            java.net.URI(url).host
        } catch (_: Exception) {
            null
        } ?: return ToolResult(false, "bad host")
        HostSafety.assertResolvedHostSafe(host, addressLookup)?.let {
            return ToolResult(false, it)
        }
        return try {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            val body = resp.body?.string().orEmpty().take(32 * 1024)
            if (resp.code in 300..399) ToolResult(false, "Redirects are blocked (HTTP ${resp.code})")
            else if (!resp.isSuccessful) ToolResult(false, "HTTP ${resp.code}: $body")
            else ToolResult(true, body)
        } catch (e: Exception) {
            ToolResult(false, e.message ?: "Network error")
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

internal fun parseString(argumentsJson: String, key: String): String? = try {
    json.parseToJsonElement(argumentsJson).jsonObject[key]?.jsonPrimitive?.content
} catch (_: Exception) {
    null
}

/** Same as [parseString] but tolerates JSON numbers (content still works via JsonPrimitive). */
internal fun parseOptionalNumberString(argumentsJson: String, key: String): String? = parseString(argumentsJson, key)

internal fun workspaceRoot(context: Context): File {
    val dir = File(context.filesDir, "minima_workspace")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

internal fun resolveSafe(root: File, relative: String): File? =
    PathSafety.resolveSafe(root, relative)

/**
 * Append Markdown to today's daily note under sandbox notes/YYYY-MM-DD.md.
 * Creates the file with a heading if missing.
 */
class AppendDailyNoteTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val spec = ToolSpec(
        name = "append_daily_note",
        description = "Append text to today's daily note in the app workspace (notes/YYYY-MM-DD.md). Creates the file if needed.",
        parameters = JsonSchema.obj(
            "content" to JsonSchema.string("Markdown or plain text to append"),
            "heading" to JsonSchema.string("Optional ## heading before the content"),
            required = listOf("content"),
        ),
        requiresGrant = Grant.Sandbox,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val content = parseString(argumentsJson, "content")
            ?: return ToolResult(false, "content required")
        if (content.isBlank()) return ToolResult(false, "content is blank")
        val heading = parseString(argumentsJson, "heading")
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val root = workspaceRoot(context)
        val notes = File(root, "notes").apply { mkdirs() }
        val file = File(notes, "$day.md")
        val block = buildString {
            if (!file.exists()) {
                appendLine("# Daily note — $day")
                appendLine()
            }
            if (!heading.isNullOrBlank()) {
                appendLine()
                appendLine("## ${heading.trim()}")
            }
            appendLine()
            appendLine(content.trimEnd())
            appendLine()
        }
        file.appendText(block)
        return ToolResult(true, "Appended ${content.length} chars to notes/$day.md")
    }
}


/**
 * Non-sensitive device / app facts for the agent (no IMEI, no ads id).
 */
class DeviceInfoTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val spec = ToolSpec(
        name = "device_info",
        description = "Return non-sensitive device and app info (manufacturer, model, SDK, app version, locale, timezone).",
        parameters = JsonSchema.obj(),
        requiresGrant = Grant.None,
    )

    override suspend fun invoke(argumentsJson: String): ToolResult {
        val pm = context.packageManager
        val pkg = context.packageName
        @Suppress("DEPRECATION")
        val version = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull().orEmpty()
        val locale = Locale.getDefault().toLanguageTag()
        val tz = java.util.TimeZone.getDefault().id
        val body = buildString {
            appendLine("app=$pkg")
            appendLine("version=$version")
            appendLine("manufacturer=${android.os.Build.MANUFACTURER}")
            appendLine("model=${android.os.Build.MODEL}")
            appendLine("sdk=${android.os.Build.VERSION.SDK_INT}")
            appendLine("release=${android.os.Build.VERSION.RELEASE}")
            appendLine("locale=$locale")
            appendLine("timezone=$tz")
        }.trim()
        return ToolResult(true, body)
    }
}
