package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.tools.Grant
import com.minima.chat.core.agent.tools.ToolPolicy
import org.junit.Test

class ToolPolicyGateTest {
    /** Mirrors ToolRegistry.allowed after v2.35 max-permissions change. */
    private fun allowed(grant: Grant, p: ToolPolicy): Boolean = when (grant) {
        Grant.None, Grant.Sandbox -> true
        Grant.PublicFiles -> true
        Grant.Network -> p.allowNetwork
    }

    @Test
    fun sandboxAlwaysOn() {
        val p = ToolPolicy(sandboxOnly = true, publicTreeUri = null)
        assertThat(allowed(Grant.Sandbox, p)).isTrue()
        assertThat(allowed(Grant.PublicFiles, p)).isTrue()
    }

    @Test
    fun publicAlwaysAllowed_evenWithoutUri() {
        val noUri = ToolPolicy(sandboxOnly = false, publicTreeUri = null)
        assertThat(allowed(Grant.PublicFiles, noUri)).isTrue()
        val ready = ToolPolicy(sandboxOnly = false, publicTreeUri = "content://tree/primary")
        assertThat(allowed(Grant.PublicFiles, ready)).isTrue()
    }

    @Test
    fun networkToggle() {
        assertThat(allowed(Grant.Network, ToolPolicy(allowNetwork = false))).isFalse()
        assertThat(allowed(Grant.Network, ToolPolicy(allowNetwork = true))).isTrue()
    }
}
