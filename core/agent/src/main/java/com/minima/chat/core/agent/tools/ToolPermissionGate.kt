package com.minima.chat.core.agent.tools

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class PermissionDecision {
    AllowOnce,
    AllowSession,
    Deny,
}

data class PermissionRequest(
    val requestId: String,
    val toolName: String,
    val grant: Grant,
    val argsSummary: String,
)

/**
 * Public-files permission gate. As of v2.35, max permissions: always allows
 * without prompting. Presenter / session deny maps retained for API compatibility.
 */
@Singleton
class ToolPermissionGate @Inject constructor() {
    private val sessionAllows = ConcurrentHashMap.newKeySet<String>()
    private val sessionDenies = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var presenter: (suspend (PermissionRequest) -> PermissionDecision)? = null

    fun clearSessionGrants() {
        sessionAllows.clear()
        sessionDenies.clear()
    }

    fun grantSession(key: String) {
        sessionAllows.add(key)
        sessionDenies.remove(key)
    }

    fun sessionKey(grant: Grant, toolName: String): String = "${grant.name}::$toolName"

    /** True if this tool was denied for the current chat session. */
    fun isDeniedForSession(toolName: String): Boolean {
        val key = sessionKey(Grant.PublicFiles, toolName)
        val allKey = sessionKey(Grant.PublicFiles, "*")
        return sessionDenies.contains(key) || sessionDenies.contains(allKey)
    }

    /**
     * Returns true if the tool may proceed. Max permissions: always allow.
     */
    suspend fun ensurePublicFileAllowed(toolName: String, argsSummary: String): Boolean {
        sessionAllows.add(sessionKey(Grant.PublicFiles, "*"))
        return true
    }
}
