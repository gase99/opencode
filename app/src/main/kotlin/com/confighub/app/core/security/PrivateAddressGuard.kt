package com.confighub.app.core.security

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Blocks SSRF-like behavior: neither source fetching nor health probing may target
 * loopback, link-local, private (RFC 1918), CGNAT, benchmark, documentation or ULA
 * addresses.
 */
object PrivateAddressGuard {

    /** True when the given address must not be fetched/probed. */
    fun isPrivateIp(ip: InetAddress): Boolean {
        val addr = unwrapMapped(ip)
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress ||
            addr.isSiteLocalAddress || addr.isMulticastAddress
        ) {
            return true
        }
        val b = addr.address
        return when (addr) {
            is Inet4Address -> {
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                val third = b[2].toInt() and 0xFF
                // CGNAT 100.64.0.0/10
                (first == 100 && second in 64..127) ||
                    // 0.0.0.0/8
                    (first == 0) ||
                    // TEST-NET-1/2/3 (documentation ranges)
                    (first == 192 && second == 0 && third == 2) ||
                    (first == 198 && second == 51 && third == 100) ||
                    (first == 203 && second == 0 && third == 113) ||
                    // Benchmark 198.18.0.0/15
                    (first == 198 && second in 18..19)
            }

            else -> (b[0].toInt() and 0xFE) == 0xFC // IPv6 ULA fc00::/7
        }
    }

    /**
     * Checks a host string WITHOUT resolving DNS (for URL validation): returns true
     * when the host is an IP literal in a blocked range. Hostnames are checked later
     * with [resolveAndCheck] right before any socket is opened.
     */
    fun isPrivateLiteral(host: String): Boolean {
        val cleaned = host.trim().removePrefix("[").removeSuffix("]")
        if (cleaned.isEmpty()) return true
        val looksLikeIp = cleaned.contains(':') ||
            Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(cleaned)
        if (!looksLikeIp) return false
        val addr = runCatching { InetAddress.getByName(cleaned) }.getOrNull() ?: return true
        return isPrivateIp(addr)
    }

    /**
     * Resolves [host] through the injected [resolver] and reports whether ANY result
     * is a blocked address (or resolution produced nothing — treated as unsafe).
     */
    fun resolveAndCheck(host: String, resolver: (String) -> List<InetAddress>): Boolean {
        val addresses = runCatching { resolver(host) }.getOrNull().orEmpty()
        if (addresses.isEmpty()) return true
        return addresses.any { isPrivateIp(it) }
    }

    /** Unwraps IPv4-mapped IPv6 (::ffff:10.0.0.1) so mapped private IPs cannot slip through. */
    private fun unwrapMapped(ip: InetAddress): InetAddress {
        val b = ip.address
        if (b.size == 16 && b[0] == 0.toByte() && b.slice(1..9).all { it == 0.toByte() } &&
            b[10] == 0.toByte() && b[11] == 0.toByte() && b[12] == 0xFF.toByte() && b[13] == 0xFF.toByte()
        ) {
            return runCatching { InetAddress.getByAddress(b.copyOfRange(12, 16)) }.getOrDefault(ip)
        }
        return ip
    }
}
