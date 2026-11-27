package com.confighub.app.domain.health

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.security.PrivateAddressGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/** DNS resolver wrapper (open so tests can stub resolution). */
open class SystemDnsResolver @Inject constructor() {
    open fun resolve(host: String): List<InetAddress> =
        InetAddress.getAllByName(host).toList()
}

/**
 * Measures real network health for a config:
 *
 * 1. DNS resolution time (system resolver),
 * 2. TCP connect latency — several samples → mean, min and jitter,
 * 3. TLS handshake latency (only for TLS configs; REALITY is skipped because a plain
 *    TLS probe is meaningless against REALITY servers — recorded as `tlsProbed=false`).
 *
 * NO ICMP: Android restricts raw sockets for normal apps; TCP/TLS latency is a far
 * better predictor of proxy usability anyway.
 *
 * SSRF guard: if the host resolves to a private/loopback address, probing is refused.
 */
@Singleton
class HealthChecker @Inject constructor(
    private val socketFactory: ProbeSocketFactory,
    private val dnsResolver: SystemDnsResolver,
) {

    suspend fun probe(
        profile: ConfigProfile,
        options: ProbeOptions = ProbeOptions(),
    ): ProbeResult = withContext(Dispatchers.IO) {
        try {
            probeInternal(profile, options)
        } catch (failure: Exception) {
            // Never leak exception details (they can embed host/credential material).
            ProbeResult(ok = false, error = ProbeError.IO_ERROR)
        }
    }

    private fun probeInternal(profile: ConfigProfile, options: ProbeOptions): ProbeResult {
        // 1. DNS resolution + SSRF guard on answers.
        val dnsMs: Long
        val ip: InetAddress
        try {
            val started = System.nanoTime()
            val answers = dnsResolver.resolve(profile.host)
            dnsMs = (System.nanoTime() - started) / 1_000_000L
            if (answers.isEmpty()) {
                return ProbeResult(ok = false, error = ProbeError.DNS_EMPTY)
            }
            if (answers.any { PrivateAddressGuard.isPrivateIp(it) }) {
                return ProbeResult(ok = false, error = ProbeError.PRIVATE_ADDRESS)
            }
            ip = answers.first()
        } catch (failure: Exception) {
            return ProbeResult(ok = false, error = ProbeError.DNS_FAIL)
        }

        // 2. TCP samples.
        val samples = mutableListOf<Int>()
        repeat(options.tcpSamples) {
            try {
                samples += socketFactory.tcpConnect(ip, profile.port, options.tcpTimeoutMs)
            } catch (failure: Exception) {
                // counted as a failed sample
            }
        }
        if (samples.isEmpty()) {
            return ProbeResult(
                ok = false,
                dnsMs = dnsMs,
                resolvedIp = ip.hostAddress,
                error = ProbeError.TCP_CONNECT,
            )
        }
        val mean = samples.average().toLong()
        val min = samples.min()
        val jitter = if (samples.size >= 2) {
            samples.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toLong()
        } else {
            null
        }

        // 3. TLS handshake (only for plain TLS; REALITY servers reject generic handshakes).
        var tlsMs: Long? = null
        var tlsProbed = false
        if (shouldProbeTls(profile.tls)) {
            tlsProbed = true
            try {
                tlsMs = socketFactory.tlsHandshake(
                    host = profile.sni ?: profile.host,
                    ip = ip,
                    port = profile.port,
                    timeoutMs = options.tlsTimeoutMs,
                ).toLong()
            } catch (failure: Exception) {
                return ProbeResult(
                    ok = false,
                    dnsMs = dnsMs,
                    tcpMeanMs = mean,
                    tcpMinMs = min.toLong(),
                    jitterMs = jitter,
                    tlsProbed = true,
                    resolvedIp = ip.hostAddress,
                    error = ProbeError.TLS_HANDSHAKE,
                )
            }
        }

        return ProbeResult(
            ok = true,
            dnsMs = dnsMs,
            tcpMeanMs = mean,
            tcpMinMs = min.toLong(),
            jitterMs = jitter,
            tlsMs = tlsMs,
            tlsProbed = tlsProbed,
            resolvedIp = ip.hostAddress,
            error = null,
        )
    }
}
