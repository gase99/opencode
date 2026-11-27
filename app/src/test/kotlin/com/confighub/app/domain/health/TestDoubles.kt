package com.confighub.app.domain.health

import java.net.InetAddress

/**
 * Test doubles shared between unit and Robolectric tests.
 * Deterministic — no real network is touched.
 */
class FakeProbeSocketFactory(
    private val tcpLatencies: List<Int>,
    private val tlsLatency: Int? = null,
) : ProbeSocketFactory {

    var tcpCallIndex = 0
        private set
    var tlsCalls = 0
        private set

    override fun tcpConnect(ip: InetAddress, port: Int, timeoutMs: Int): Int {
        val next = tcpLatencies.getOrNull(tcpCallIndex) ?: throw java.net.ConnectException("refused")
        tcpCallIndex++
        return next
    }

    override fun tlsHandshake(host: String, ip: InetAddress, port: Int, timeoutMs: Int): Int {
        tlsCalls++
        return tlsLatency ?: throw javax.net.ssl.SSLHandshakeException("bad certificate")
    }
}

/** Always resolves to a public documentation-safe address (93.184.216.34, example.net). */
open class FakeDnsResolver : SystemDnsResolver() {
    private val publicIp: InetAddress = InetAddress.getByName("93.184.216.34")
    override fun resolve(host: String): List<InetAddress> = listOf(publicIp)
}
