package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.tools.PermissionDecision
import com.minima.chat.core.agent.tools.ToolPermissionGate
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** v2.35+: max permissions — gate always allows; presenter is ignored. */
class ToolPermissionGateTest {
    @Test
    fun noPresenter_allows() = runBlocking {
        val gate = ToolPermissionGate()
        assertThat(gate.ensurePublicFileAllowed("read_file", "{}")).isTrue()
    }

    @Test
    fun denyPresenter_stillAllows() = runBlocking {
        val gate = ToolPermissionGate()
        var calls = 0
        gate.presenter = {
            calls++
            PermissionDecision.Deny
        }
        assertThat(gate.ensurePublicFileAllowed("write_file", """{"path":"a"}""")).isTrue()
        assertThat(gate.ensurePublicFileAllowed("read_file", "{}")).isTrue()
        // Presenter must not be consulted
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun allowSessionPresenter_notConsulted() = runBlocking {
        val gate = ToolPermissionGate()
        var calls = 0
        gate.presenter = {
            calls++
            PermissionDecision.AllowSession
        }
        assertThat(gate.ensurePublicFileAllowed("list_dir", "{}")).isTrue()
        assertThat(gate.ensurePublicFileAllowed("read_file", "{}")).isTrue()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun clearSessionGrants_stillAllows() = runBlocking {
        val gate = ToolPermissionGate()
        assertThat(gate.ensurePublicFileAllowed("write_file", "{}")).isTrue()
        gate.clearSessionGrants()
        assertThat(gate.ensurePublicFileAllowed("write_file", "{}")).isTrue()
    }
}
