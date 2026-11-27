package com.confighub.app.data.net

import com.confighub.app.core.security.PrivateAddressGuard
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * SSRF-safe DNS resolver for all app HTTP traffic: refuses to return loopback,
 * link-local, private, CGNAT, documentation or ULA addresses, so neither sources nor
 * redirects can point the fetcher into the local network.
 */
object SafeDns : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val answers = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrElse {
            throw UnknownHostException("dns lookup failed for $hostname")
        }
        if (answers.isEmpty()) {
            throw UnknownHostException("no addresses for $hostname")
        }
        if (answers.any { PrivateAddressGuard.isPrivateIp(it) }) {
            throw UnknownHostException("blocked private address for $hostname")
        }
        return answers
    }
}
