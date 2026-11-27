package com.confighub.app.domain.health

import com.confighub.app.core.model.TlsMode

/** Stable error categories recorded in health history (never contains secrets). */
enum class ProbeError {
    DNS_FAIL,
    DNS_EMPTY,
    PRIVATE_ADDRESS,
    TCP_CONNECT,
    TLS_HANDSHAKE,
    IO_ERROR,
}

/** Tunables for a single probe run. */
data class ProbeOptions(
    val tcpSamples: Int = 3,
    val tcpTimeoutMs: Int = 4_000,
    val tlsTimeoutMs: Int = 6_000,
)

/**
 * Result of one health-check run against a config.
 *
 * IMPORTANT: Android does not grant raw ICMP to normal apps, so there is **no ping**.
 * We measure DNS resolution, TCP connect and (when applicable) TLS handshake latency.
 */
data class ProbeResult(
    val ok: Boolean,
    val dnsMs: Long? = null,
    val tcpMeanMs: Long? = null,
    val tcpMinMs: Long? = null,
    val jitterMs: Long? = null,
    val tlsMs: Long? = null,
    val tlsProbed: Boolean = false,
    val error: ProbeError? = null,
    val resolvedIp: String? = null,
)

/** Whether a TLS handshake probe makes sense for this profile. */
internal fun shouldProbeTls(tls: TlsMode): Boolean = tls == TlsMode.TLS
