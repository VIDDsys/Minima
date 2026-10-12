package com.minima.chat.core.agent.tools

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * SSRF / private-network guards for agent HTTP tools.
 *
 * Blocks loopback, RFC1918, link-local, CGNAT, IPv6 ULA/link-local, common
 * cloud metadata hostnames, and several literal IPv4 encoding tricks
 * (decimal, hex, octal, short forms). Prefer failing closed on parse errors.
 *
 * Also provides **runtime DNS** checks ([assertResolvedHostSafe] /
 * [isBlockedInetAddress]) so a hostname that looks public cannot rebind to a
 * private IP after resolution.
 */
object HostSafety {
    fun looksPrivateHost(url: String): Boolean {
        return try {
            val uri = URI(url.trim())
            val host = uri.host?.lowercase() ?: return true
            isBlockedHost(host)
        } catch (_: Exception) {
            true
        }
    }

    fun isBlockedHost(hostRaw: String): Boolean {
        val host = hostRaw.trim().lowercase()
            .removePrefix("[")
            .removeSuffix("]")
            .trimEnd('.')
        if (host.isBlank()) return true

        if (host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host == "metadata.google.internal" ||
            host == "metadata" ||
            host == "kubernetes.default" ||
            host == "kubernetes.default.svc"
        ) {
            return true
        }

        if (":" in host) {
            return isBlockedIpv6(host)
        }

        parseIpv4Bytes(host)?.let { return isPrivateOrSpecialIpv4(it) }
        parseSingleTokenIpv4(host)?.let { return isPrivateOrSpecialIpv4(it) }

        return false
    }

    /**
     * Resolve [hostname] via [lookup] and fail if any address is blocked.
     * @return null if safe; otherwise a short error for tool results.
     */
    fun assertResolvedHostSafe(
        hostname: String,
        lookup: AddressLookup = SystemAddressLookup,
    ): String? {
        val host = hostname.trim().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (host.isBlank()) return "Missing host"
        if (isBlockedHost(host)) return "Private/link-local hosts blocked"

        val addrs = try {
            lookup.lookup(host)
        } catch (e: Exception) {
            return "DNS lookup failed: ${e.message ?: "error"}"
        }
        if (addrs.isEmpty()) return "DNS lookup returned no addresses"

        for (addr in addrs) {
            if (isBlockedInetAddress(addr)) {
                val ip = addr.hostAddress ?: addr.toString()
                return "Resolved address blocked ($ip)"
            }
        }
        return null
    }

    /**
     * Whether a resolved [InetAddress] is unsafe for agent HTTP egress.
     */
    fun isBlockedInetAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress) return true
        if (addr.isLoopbackAddress) return true
        if (addr.isLinkLocalAddress) return true
        if (addr.isSiteLocalAddress) return true
        if (addr.isMulticastAddress) return true

        return when (addr) {
            is Inet4Address -> {
                val octets = IntArray(4) { i -> addr.address[i].toInt() and 0xff }
                isPrivateOrSpecialIpv4(octets)
            }
            is Inet6Address -> isBlockedResolvedIpv6(addr)
            else -> true // fail closed
        }
    }

    private fun isBlockedResolvedIpv6(addr: Inet6Address): Boolean {
        val b = addr.address
        if (b.size != 16) return true

        // Unique local addresses fc00::/7
        val b0 = b[0].toInt() and 0xff
        if ((b0 and 0xfe) == 0xfc) return true

        // IPv4-mapped ::ffff:a.b.c.d
        val mapped = extractIpv4Mapped(b)
        if (mapped != null) return isPrivateOrSpecialIpv4(mapped)

        // IPv4-compatible ::a.b.c.d (deprecated)
        val compatible = extractIpv4Compatible(b)
        if (compatible != null) return isPrivateOrSpecialIpv4(compatible)

        return false
    }

    private fun extractIpv4Mapped(b: ByteArray): IntArray? {
        for (i in 0..9) if (b[i].toInt() != 0) return null
        if ((b[10].toInt() and 0xff) != 0xff || (b[11].toInt() and 0xff) != 0xff) return null
        return IntArray(4) { i -> b[12 + i].toInt() and 0xff }
    }

    private fun extractIpv4Compatible(b: ByteArray): IntArray? {
        for (i in 0..11) if (b[i].toInt() != 0) return null
        // ::1 and :: are already covered by loopback / any-local
        val octets = IntArray(4) { i -> b[12 + i].toInt() and 0xff }
        if (octets.all { it == 0 }) return octets
        if (octets[0] == 0 && octets[1] == 0 && octets[2] == 0 && octets[3] == 1) return octets
        return octets
    }

    private fun isBlockedIpv6(host: String): Boolean {
        val h = host.lowercase()
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        if (h.startsWith("fe8") || h.startsWith("fe9") ||
            h.startsWith("fea") || h.startsWith("feb")
        ) {
            return true
        }
        if (h.startsWith("fc") || h.startsWith("fd")) return true
        if ("ffff:" in h) {
            val mapped = h.substringAfter("ffff:")
            val v4 = if ("." in mapped) {
                parseIpv4Bytes(mapped)
            } else {
                parseMappedHexIpv4(mapped)
            }
            if (v4 != null) return isPrivateOrSpecialIpv4(v4)
        }
        if (h == "::" || h.matches(Regex("""^0(:0)+$"""))) return true
        return false
    }

    private fun parseMappedHexIpv4(mapped: String): IntArray? {
        val parts = mapped.split(":")
        if (parts.size != 2) return null
        val hi = parts[0].toIntOrNull(16) ?: return null
        val lo = parts[1].toIntOrNull(16) ?: return null
        if (hi !in 0..0xffff || lo !in 0..0xffff) return null
        return intArrayOf((hi shr 8) and 0xff, hi and 0xff, (lo shr 8) and 0xff, lo and 0xff)
    }

    /** Returns 4 octets or null if not an IPv4-like literal. */
    fun parseIpv4Bytes(host: String): IntArray? {
        if ('.' !in host) return null
        val parts = host.split('.')
        if (parts.size !in 2..4) return null
        val nums = IntArray(parts.size)
        for (i in parts.indices) {
            nums[i] = parseIpv4Part(parts[i]) ?: return null
        }
        val arr = when (nums.size) {
            4 -> {
                if (nums.any { it !in 0..255 }) return null
                nums
            }
            3 -> {
                val c = nums[2]
                if (nums[0] !in 0..255 || nums[1] !in 0..255 || c !in 0..0xffff) return null
                intArrayOf(nums[0], nums[1], (c shr 8) and 0xff, c and 0xff)
            }
            2 -> {
                val b = nums[1]
                if (nums[0] !in 0..255 || b !in 0..0xffffff) return null
                intArrayOf(
                    nums[0],
                    (b shr 16) and 0xff,
                    (b shr 8) and 0xff,
                    b and 0xff,
                )
            }
            else -> return null
        }
        return arr
    }

    private fun parseIpv4Part(part: String): Int? {
        if (part.isEmpty()) return null
        return when {
            part.startsWith("0x") || part.startsWith("0X") ->
                part.substring(2).toIntOrNull(16)
            part.length > 1 && part.startsWith("0") && part.all { it in '0'..'7' } ->
                part.toIntOrNull(8)
            part.all { it.isDigit() } -> part.toIntOrNull(10)
            else -> null
        }
    }

    private fun parseSingleTokenIpv4(host: String): IntArray? {
        val value = when {
            host.startsWith("0x") || host.startsWith("0X") ->
                host.substring(2).toLongOrNull(16)
            host.length > 1 && host.startsWith("0") && host.all { it in '0'..'7' } ->
                host.toLongOrNull(8)
            host.all { it.isDigit() } -> host.toLongOrNull(10)
            else -> null
        } ?: return null
        if (value !in 0..0xffff_ffffL) return null
        return intArrayOf(
            ((value shr 24) and 0xff).toInt(),
            ((value shr 16) and 0xff).toInt(),
            ((value shr 8) and 0xff).toInt(),
            (value and 0xff).toInt(),
        )
    }

    fun isPrivateOrSpecialIpv4(octets: IntArray): Boolean {
        if (octets.size != 4) return true
        val a = octets[0]
        val b = octets[1]
        if (a == 0) return true
        if (a == 10) return true
        if (a == 127) return true
        if (a == 169 && b == 254) return true
        if (a == 172 && b in 16..31) return true
        if (a == 192 && b == 168) return true
        if (a == 100 && b in 64..127) return true
        if (a == 192 && b == 0 && octets[2] == 0) return true
        if (a >= 224) return true
        return false
    }
}

/** Back-compat wrapper used across agent tools. */
internal fun looksPrivateHost(url: String): Boolean = HostSafety.looksPrivateHost(url)
