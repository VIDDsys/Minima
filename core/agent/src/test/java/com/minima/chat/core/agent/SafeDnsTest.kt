package com.minima.chat.core.agent

import com.google.common.truth.Truth.assertThat
import com.minima.chat.core.agent.httptools.DeclarativeHttpTool
import com.minima.chat.core.agent.httptools.DeclarativeHttpToolDef
import com.minima.chat.core.agent.tools.AddressLookup
import com.minima.chat.core.agent.tools.HostSafety
import com.minima.chat.core.agent.tools.HttpGetTool
import com.minima.chat.core.agent.tools.SafeDns
import com.minima.chat.core.agent.tools.ToolPolicy
import com.minima.chat.core.agent.tools.ToolPolicyStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class SafeDnsTest {

    private fun ipv4(a: Int, b: Int, c: Int, d: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte()))

    @Test
    fun assertResolved_blocksLoopbackRebinding() {
        val lookup = AddressLookup { listOf(ipv4(127, 0, 0, 1)) }
        val err = HostSafety.assertResolvedHostSafe("evil.example", lookup)
        assertThat(err).contains("blocked")
        assertThat(err).contains("127.0.0.1")
    }

    @Test
    fun assertResolved_blocksRfc1918Rebinding() {
        val lookup = AddressLookup { listOf(ipv4(10, 0, 0, 5)) }
        assertThat(HostSafety.assertResolvedHostSafe("public-looking.example", lookup))
            .contains("blocked")
    }

    @Test
    fun assertResolved_blocksMetadataLinkLocal() {
        val lookup = AddressLookup { listOf(ipv4(169, 254, 169, 254)) }
        assertThat(HostSafety.assertResolvedHostSafe("metadata-trick.example", lookup))
            .contains("blocked")
    }

    @Test
    fun assertResolved_blocksCgnat() {
        val lookup = AddressLookup { listOf(ipv4(100, 64, 0, 1)) }
        assertThat(HostSafety.assertResolvedHostSafe("cgnat.example", lookup))
            .contains("blocked")
    }

    @Test
    fun assertResolved_blocksIfAnyAddressPrivate() {
        val lookup = AddressLookup {
            listOf(ipv4(1, 1, 1, 1), ipv4(192, 168, 1, 1))
        }
        assertThat(HostSafety.assertResolvedHostSafe("multi.example", lookup))
            .contains("blocked")
    }

    @Test
    fun assertResolved_allowsPublicOnly() {
        val lookup = AddressLookup { listOf(ipv4(1, 1, 1, 1), ipv4(8, 8, 8, 8)) }
        assertThat(HostSafety.assertResolvedHostSafe("cdn.example", lookup)).isNull()
    }

    @Test
    fun assertResolved_blocksHostnameAlreadyPrivate() {
        val lookup = AddressLookup { error("should not lookup") }
        assertThat(HostSafety.assertResolvedHostSafe("127.0.0.1", lookup))
            .contains("Private")
    }

    @Test
    fun assertResolved_emptyLookupFailsClosed() {
        val lookup = AddressLookup { emptyList() }
        assertThat(HostSafety.assertResolvedHostSafe("empty.example", lookup))
            .contains("no addresses")
    }

    @Test
    fun assertResolved_lookupExceptionSurfaced() {
        val lookup = AddressLookup { throw UnknownHostException("nxdomain") }
        assertThat(HostSafety.assertResolvedHostSafe("missing.example", lookup))
            .contains("DNS lookup failed")
    }

    @Test
    fun isBlockedInetAddress_ipv6UlaAndLoopback() {
        val ula = InetAddress.getByName("fd12:3456::1")
        val loop = InetAddress.getByName("::1")
        val link = InetAddress.getByName("fe80::1")
        assertThat(HostSafety.isBlockedInetAddress(ula)).isTrue()
        assertThat(HostSafety.isBlockedInetAddress(loop)).isTrue()
        assertThat(HostSafety.isBlockedInetAddress(link)).isTrue()
    }

    @Test
    fun isBlockedInetAddress_ipv4MappedPrivate() {
        // ::ffff:127.0.0.1
        val bytes = ByteArray(16)
        bytes[10] = 0xff.toByte()
        bytes[11] = 0xff.toByte()
        bytes[12] = 127
        bytes[15] = 1
        val mapped = InetAddress.getByAddress(bytes)
        assertThat(HostSafety.isBlockedInetAddress(mapped)).isTrue()
    }

    @Test
    fun isBlockedInetAddress_allowsPublicV4() {
        assertThat(HostSafety.isBlockedInetAddress(ipv4(1, 1, 1, 1))).isFalse()
        assertThat(HostSafety.isBlockedInetAddress(ipv4(8, 8, 4, 4))).isFalse()
        assertThat(HostSafety.isBlockedInetAddress(ipv4(172, 32, 0, 1))).isFalse()
    }

    @Test
    fun safeDns_throwsOnPrivateResolution() {
        val dns = SafeDns(AddressLookup { listOf(ipv4(10, 1, 2, 3)) })
        val ex = assertThrows(UnknownHostException::class.java) {
            dns.lookup("attacker.example")
        }
        assertThat(ex.message).contains("blocked")
    }

    @Test
    fun safeDns_throwsOnBlockedHostnameBeforeLookup() {
        val dns = SafeDns(AddressLookup { error("no") })
        val ex = assertThrows(UnknownHostException::class.java) {
            dns.lookup("localhost")
        }
        assertThat(ex.message).contains("Blocked host")
    }

    @Test
    fun safeDns_returnsPublicAddresses() {
        val addrs = listOf(ipv4(93, 184, 216, 34))
        val dns = SafeDns(AddressLookup { addrs })
        assertThat(dns.lookup("example.com")).isEqualTo(addrs)
    }

    @Test
    fun declarativeHttpTool_blocksRebindingBeforeRequest() = runBlocking {
        val def = DeclarativeHttpToolDef(
            name = "rebind",
            description = "test",
            method = "GET",
            url = "https://example.com/x",
            parameters = buildJsonObject { },
        )
        var httpCalled = false
        val tool = DeclarativeHttpTool(
            def = def,
            clientFactory = {
                httpCalled = true
                OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
            },
            addressLookup = AddressLookup { listOf(ipv4(127, 0, 0, 1)) },
        )
        val r = tool.invoke("{}")
        assertThat(r.ok).isFalse()
        assertThat(r.output).contains("blocked")
        assertThat(httpCalled).isFalse()
    }

    @Test
    fun declarativeHttpTool_allowsWhenDnsPublic() = runBlocking {
        // Still won't hit network: clientFactory returns a client but we only assert
        // that DNS check passes (no "blocked") — calling example.com may fail offline.
        val def = DeclarativeHttpToolDef(
            name = "okdns",
            description = "test",
            method = "GET",
            url = "https://example.com/x",
            parameters = buildJsonObject { },
        )
        val tool = DeclarativeHttpTool(
            def = def,
            clientFactory = {
                OkHttpClient.Builder()
                    .callTimeout(1, TimeUnit.MILLISECONDS)
                    .connectTimeout(1, TimeUnit.MILLISECONDS)
                    .build()
            },
            addressLookup = AddressLookup { listOf(ipv4(1, 1, 1, 1)) },
        )
        val r = tool.invoke("{}")
        // Either network timeout/error or success — must NOT be DNS block
        assertThat(r.output).doesNotContain("Resolved address blocked")
        assertThat(r.output).doesNotContain("Private/link-local hosts blocked")
    }

    @Test
    fun httpGetTool_blocksRebinding() = runBlocking {
        val store = ToolPolicyStore().apply { policy = ToolPolicy(allowNetwork = true) }
        val tool = HttpGetTool(store)
        tool.addressLookup = AddressLookup { listOf(ipv4(192, 168, 0, 1)) }
        tool.resetClientForTest()
        val r = tool.invoke("""{"url":"https://example.com/"}""")
        assertThat(r.ok).isFalse()
        assertThat(r.output).contains("blocked")
    }

    @Test
    fun defaultClient_usesSafeDns() {
        val c = DeclarativeHttpTool.defaultClient(5)
        assertThat(c.dns).isInstanceOf(SafeDns::class.java)
        assertThat(c.followRedirects).isFalse()
    }
}
