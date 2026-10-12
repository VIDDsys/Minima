package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.mcp.RemoteMcpClient
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class McpErrorClassifyTest {
    @Test
    fun timeoutIsTransient() {
        assertThat(RemoteMcpClient.isTransient(SocketTimeoutException("read timed out"))).isTrue()
    }

    @Test
    fun dnsIsNotTransient() {
        assertThat(RemoteMcpClient.isTransient(UnknownHostException("nohost"))).isFalse()
    }

    @Test
    fun friendlyDns() {
        val msg = RemoteMcpClient.friendlyError("demo", UnknownHostException("x"))
        assertThat(msg).contains("DNS")
        assertThat(msg).contains("demo")
    }

    @Test
    fun http502IsTransient() {
        assertThat(RemoteMcpClient.isTransient(IllegalStateException("HTTP 502: bad gateway"))).isTrue()
    }
}
