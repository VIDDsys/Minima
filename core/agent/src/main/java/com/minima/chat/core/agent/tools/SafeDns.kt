package com.minima.chat.core.agent.tools

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * OkHttp [Dns] that rejects hosts whose *resolved* addresses are private /
 * link-local / metadata / CGNAT / ULA — basic DNS-rebinding defense.
 *
 * Lookup is injectable for offline unit tests ([AddressLookup]).
 */
class SafeDns(
    private val lookup: AddressLookup = SystemAddressLookup,
) : Dns {
    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        if (HostSafety.isBlockedHost(hostname)) {
            throw UnknownHostException("Blocked host: $hostname")
        }
        val addrs = try {
            lookup.lookup(hostname)
        } catch (e: UnknownHostException) {
            throw e
        } catch (e: Exception) {
            throw UnknownHostException("DNS lookup failed for $hostname: ${e.message}")
        }
        if (addrs.isEmpty()) {
            throw UnknownHostException("No addresses for $hostname")
        }
        for (addr in addrs) {
            if (HostSafety.isBlockedInetAddress(addr)) {
                throw UnknownHostException(
                    "Resolved address blocked for $hostname (${addr.hostAddress})",
                )
            }
        }
        return addrs
    }
}

/**
 * Pluggable name → addresses for [HostSafety.assertResolvedHostSafe] / [SafeDns].
 * Production uses [SystemAddressLookup]; tests inject fixed [InetAddress] lists.
 */
fun interface AddressLookup {
    fun lookup(hostname: String): List<InetAddress>
}

object SystemAddressLookup : AddressLookup {
    override fun lookup(hostname: String): List<InetAddress> =
        InetAddress.getAllByName(hostname).toList()
}
