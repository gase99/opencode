package com.confighub.app.domain.health

import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Socket abstraction so health checks are unit-testable without real sockets. */
interface ProbeSocketFactory {
    /** Opens a TCP connection to (ip, port); returns connect latency in ms; throws on failure. */
    fun tcpConnect(ip: InetAddress, port: Int, timeoutMs: Int): Int

    /**
     * Performs a TLS handshake against (ip, port) with SNI [host] and standard system
     * trust + hostname verification; returns handshake latency in ms; throws on failure.
     */
    fun tlsHandshake(host: String, ip: InetAddress, port: Int, timeoutMs: Int): Int
}

/** Production implementation using real sockets with secure defaults. */
@Singleton
class DefaultProbeSocketFactory @Inject constructor() : ProbeSocketFactory {

    override fun tcpConnect(ip: InetAddress, port: Int, timeoutMs: Int): Int {
        val socket = java.net.Socket()
        try {
            val started = System.nanoTime()
            socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
            return ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(0)
        } finally {
            runCatching { socket.close() }
        }
    }

    override fun tlsHandshake(host: String, ip: InetAddress, port: Int, timeoutMs: Int): Int {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket() as SSLSocket
        try {
            socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val params = socket.sslParameters
            // Secure defaults: verify certificate chain AND hostname.
            params.endpointIdentificationAlgorithm = "HTTPS"
            params.serverNames = listOf(SNIHostName(host))
            socket.sslParameters = params
            val started = System.nanoTime()
            socket.startHandshake()
            return ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(0)
        } finally {
            runCatching { socket.close() }
        }
    }
}
