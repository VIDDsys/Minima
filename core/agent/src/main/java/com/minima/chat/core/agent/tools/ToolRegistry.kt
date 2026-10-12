package com.minima.chat.core.agent.tools

import com.minima.chat.core.agent.httptools.SkillHttpToolsLoader
import com.minima.chat.core.agent.mcp.AgentToolAdapter
import com.minima.chat.core.agent.mcp.McpConfigParser
import com.minima.chat.core.agent.mcp.McpServerHealth
import com.minima.chat.core.agent.mcp.McpToolBridge
import com.minima.chat.core.network.dto.ToolDefinitionDto
import kotlinx.coroutines.CancellationException
import com.minima.chat.core.network.dto.ToolFunctionDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor(
    nowTool: NowTool,
    listSandboxTool: ListSandboxTool,
    readSandboxTool: ReadSandboxTool,
    writeSandboxTool: WriteSandboxTool,
    appendDailyNoteTool: AppendDailyNoteTool,
    httpGetTool: HttpGetTool,
    listPublicDirTool: ListPublicDirTool,
    readPublicFileTool: ReadPublicFileTool,
    writePublicFileTool: WritePublicFileTool,
    mkdirPublicTool: MkdirPublicTool,
    deviceInfoTool: DeviceInfoTool,
    searchMemoryTool: SearchMemoryTool,
    readSkillTool: ReadSkillTool,
    focusTaskTool: FocusTaskTool,
    private val policyStore: ToolPolicyStore,
    private val permissionGate: ToolPermissionGate,
    private val skillHttpToolsLoader: SkillHttpToolsLoader,
) : ToolRuntime {
    private val builtin: Map<String, AgentTool> = listOf(
        nowTool,
        listSandboxTool,
        readSandboxTool,
        writeSandboxTool,
        appendDailyNoteTool,
        httpGetTool,
        listPublicDirTool,
        readPublicFileTool,
        writePublicFileTool,
        mkdirPublicTool,
        deviceInfoTool,
        searchMemoryTool,
        readSkillTool,
        focusTaskTool,
    ).associateBy { it.spec.name }

    @Volatile
    private var mcpTools: Map<String, AgentTool> = emptyMap()

    @Volatile
    private var skillHttpTools: Map<String, AgentTool> = emptyMap()

    @Volatile
    private var lastSkillHttpErrors: List<String> = emptyList()

    @Volatile
    private var lastHealth: List<McpServerHealth> = emptyList()

    @Volatile
    private var lastHealthCheckedAtMs: Long = 0L

    private val bridge = McpToolBridge()
    /** Refresh MCP tools from settings JSON. Safe to call off main thread. */
    fun refreshMcp(mcpConfigJson: String) {
        val cfg = McpConfigParser.parse(mcpConfigJson)
        val adapters: List<AgentToolAdapter> = try {
            bridge.discover(cfg.servers)
        } catch (_: Exception) {
            emptyList()
        }
        mcpTools = adapters.associateBy { it.spec.name }
    }

    /**
     * Load declarative HTTP tools from skill-pack `tools.json` (sandbox + optional SAF).
     * Does not touch MCP tools. Safe off main thread.
     */
    fun refreshSkillHttp(safTreeUri: String? = null) {
        val result = try {
            skillHttpToolsLoader.load(safTreeUri)
        } catch (_: Exception) {
            lastSkillHttpErrors = listOf("skill HTTP load failed")
            skillHttpTools = emptyMap()
            return
        }
        // Drop names that collide with builtins or MCP
        val filtered = result.tools.filter { t ->
            t.spec.name !in builtin && !mcpTools.containsKey(t.spec.name)
        }
        skillHttpTools = filtered.associateBy { it.spec.name }
        lastSkillHttpErrors = result.errors
    }

    fun lastSkillHttpErrors(): List<String> = lastSkillHttpErrors

    fun skillHttpCount(): Int = skillHttpTools.size

    fun skillHttpNames(): List<String> = skillHttpTools.keys.sorted()

    /** Ping each configured server (initialize + tools/list) and return health. */
    fun probeMcp(mcpConfigJson: String): List<McpServerHealth> {
        val cfg = McpConfigParser.parse(mcpConfigJson)
        val (adapters, health) = bridge.discoverWithErrors(cfg.servers)
        val now = System.currentTimeMillis()
        val stamped = health.map { it.copy(checkedAtMs = if (it.checkedAtMs > 0L) it.checkedAtMs else now) }
        mcpTools = adapters.associateBy { it.spec.name }
        lastHealth = stamped
        lastHealthCheckedAtMs = now
        return stamped
    }

    fun lastMcpHealth(): List<McpServerHealth> = lastHealth

    fun lastMcpHealthCheckedAtMs(): Long = lastHealthCheckedAtMs

    private fun all(): Map<String, AgentTool> = builtin + skillHttpTools + mcpTools

    override fun definitions(): List<ToolDefinitionDto> = all().values
        .filter { allowed(it.spec.requiresGrant) }
        .map { t ->
            ToolDefinitionDto(
                function = ToolFunctionDto(
                    name = t.spec.name,
                    description = t.spec.description,
                    parameters = t.spec.parameters,
                ),
            )
        }

    fun names(): Set<String> = all().keys

    fun mcpCount(): Int = mcpTools.size

    fun builtinCount(): Int = builtin.size

    fun builtinNames(): List<String> = builtin.keys.sorted()

    fun mcpNames(): List<String> = mcpTools.keys.sorted()

    data class ToolInventory(
        val builtin: List<String>,
        val mcp: List<String>,
        val http: List<String> = emptyList(),
    ) {
        val builtinCount: Int get() = builtin.size
        val mcpCount: Int get() = mcp.size
        val httpCount: Int get() = http.size
        val summary: String
            get() = if (http.isEmpty()) {
                "Built-in $builtinCount · MCP $mcpCount"
            } else {
                "Built-in $builtinCount · HTTP $httpCount · MCP $mcpCount"
            }
    }

    fun inventory(): ToolInventory = ToolInventory(
        builtin = builtinNames(),
        mcp = mcpNames(),
        http = skillHttpNames(),
    )

    override suspend fun dispatch(name: String, argumentsJson: String): ToolResult {
        val tool = all()[name] ?: return ToolResult(false, "Unknown tool: $name")
        if (!allowed(tool.spec.requiresGrant)) {
            return ToolResult(
                false,
                when (tool.spec.requiresGrant) {
                    Grant.PublicFiles ->
                        "Tool '$name' needs storage access (grant all-files or pick a folder)."
                    Grant.Network -> "Tool '$name' blocked: network tools disabled."
                    else -> "Tool '$name' blocked by policy (${tool.spec.requiresGrant})"
                },
            )
        }
        if (tool.spec.requiresGrant == Grant.PublicFiles) {
            val ok = permissionGate.ensurePublicFileAllowed(name, argumentsJson.take(120))
            if (!ok) {
                return ToolResult(false, "Denied for this chat: '$name' (leave chat to ask again).")
            }
        }
        return try {
            tool.invoke(argumentsJson.ifBlank { "{}" })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult(false, e.message ?: "Tool error")
        }
    }

    private fun allowed(grant: Grant): Boolean {
        val p = policyStore.policy
        return when (grant) {
            Grant.None, Grant.Sandbox -> true
            // Max permissions: public file tools always enabled (SAF tree or external fallback).
            Grant.PublicFiles -> true
            Grant.Network -> p.allowNetwork
        }
    }
}
