package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.tools.HostSafety
import org.junit.Test

class HostSafetyTest {
    @Test
    fun blocksClassicLoopbackAndRfc1918() {
        assertBlocked("https://127.0.0.1/")
        assertBlocked("https://127.0.0.1:8443/x")
        assertBlocked("https://10.0.0.5/")
        assertBlocked("https://192.168.1.1/")
        assertBlocked("https://172.16.0.1/")
        assertBlocked("https://172.31.255.255/")
        assertBlocked("https://localhost/")
        assertBlocked("https://foo.localhost/")
        assertBlocked("https://printer.local/")
    }

    @Test
    fun blocksLinkLocalAndMetadata() {
        assertBlocked("https://169.254.169.254/")
        assertBlocked("https://169.254.1.1/")
        assertBlocked("https://metadata.google.internal/")
        assertBlocked("https://metadata/")
        assertBlocked("https://svc.internal/")
    }

    @Test
    fun blocksCgnatAndReserved() {
        assertBlocked("https://100.64.0.1/")
        assertBlocked("https://100.127.255.255/")
        assertBlocked("https://0.0.0.0/")
        assertBlocked("https://224.0.0.1/")
        assertBlocked("https://240.0.0.1/")
    }

    @Test
    fun blocksIpv4EncodingTricks() {
        // decimal 127.0.0.1
        assertThat(HostSafety.isBlockedHost("2130706433")).isTrue()
        // hex
        assertThat(HostSafety.isBlockedHost("0x7f000001")).isTrue()
        // short form 127.1 → 127.0.0.1
        assertThat(HostSafety.isBlockedHost("127.1")).isTrue()
        // octal-ish 0177.0.0.1
        assertThat(HostSafety.isBlockedHost("0177.0.0.1")).isTrue()
        // trailing dot
        assertThat(HostSafety.isBlockedHost("127.0.0.1.")).isTrue()
    }

    @Test
    fun blocksIpv6LoopbackAndUla() {
        assertThat(HostSafety.isBlockedHost("::1")).isTrue()
        assertThat(HostSafety.isBlockedHost("[::1]")).isTrue()
        assertBlocked("https://[::1]/")
        assertThat(HostSafety.isBlockedHost("fc00::1")).isTrue()
        assertThat(HostSafety.isBlockedHost("fd12:3456::1")).isTrue()
        assertThat(HostSafety.isBlockedHost("fe80::1")).isTrue()
    }

    @Test
    fun blocksIpv4MappedIpv6() {
        assertThat(HostSafety.isBlockedHost("::ffff:127.0.0.1")).isTrue()
        assertThat(HostSafety.isBlockedHost("::ffff:10.0.0.1")).isTrue()
        assertThat(HostSafety.isBlockedHost("::ffff:7f00:1")).isTrue()
    }

    @Test
    fun allowsPublicHosts() {
        assertAllowed("https://example.com/")
        assertAllowed("https://api.openai.com/v1")
        assertAllowed("https://1.1.1.1/")
        assertAllowed("https://8.8.8.8/")
        assertThat(HostSafety.isBlockedHost("example.com")).isFalse()
        assertThat(HostSafety.isBlockedHost("172.32.0.1")).isFalse() // outside 172.16/12
        assertThat(HostSafety.isBlockedHost("100.63.0.1")).isFalse() // outside CGNAT
    }

    @Test
    fun parseIpv4ShortForms() {
        assertThat(HostSafety.parseIpv4Bytes("127.1")!!.toList())
            .isEqualTo(listOf(127, 0, 0, 1))
        assertThat(HostSafety.parseIpv4Bytes("10.1")!!.toList())
            .isEqualTo(listOf(10, 0, 0, 1))
        assertThat(HostSafety.parseIpv4Bytes("1.2.3")!!.toList())
            .isEqualTo(listOf(1, 2, 0, 3))
    }


    @Test
    fun resolvedAddressHelpers_publicAndPrivate() {
        val loop = java.net.InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val pub = java.net.InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
        assertThat(HostSafety.isBlockedInetAddress(loop)).isTrue()
        assertThat(HostSafety.isBlockedInetAddress(pub)).isFalse()
        val err = HostSafety.assertResolvedHostSafe(
            "rebinder.example",
            com.minima.chat.core.agent.tools.AddressLookup { listOf(loop) },
        )
        assertThat(err).contains("blocked")
    }

    private fun assertBlocked(url: String) {
        assertThat(HostSafety.looksPrivateHost(url)).isTrue()
    }

    private fun assertAllowed(url: String) {
        assertThat(HostSafety.looksPrivateHost(url)).isFalse()
    }
}
