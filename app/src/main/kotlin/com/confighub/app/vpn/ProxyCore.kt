package com.confighub.app.vpn

import com.confighub.app.core.model.ConfigProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Parameters of the VPN tunnel interface (from `VpnService.Builder.establish()`).
 *
 * The VPN fd is only ever handed to a REAL core. The mock core receives an invalid
 * fd (-1) and never opens a tunnel.
 */
data class TunParams(
    val vpnFd: Int,
    val mtu: Int,
    val sessionName: String,
    val vpnAddress: String,
    val vpnDns: String,
    /** Per-app routing — only ever populated when the user explicitly configured it. */
    val allowedApps: List<String>? = null,
)

/** Traffic snapshot reported by a real core (null stream from the mock). */
data class TrafficStats(
    val bytesIn: Long,
    val bytesOut: Long,
    val connections: Int,
)

enum class CoreStatus { IDLE, STARTING, RUNNING, STOPPING, STOPPED, FAILED }

enum class CoreEventType { STARTED, STATUS, ERROR, STOPPED }

data class CoreEvent(val type: CoreEventType, val message: String? = null)

/** Sanitized error info (implementations must never embed credential material). */
data class CoreError(val code: String, val message: String?)

sealed interface CoreStartResult {
    data object Started : CoreStartResult
    data class NotAvailable(val reason: String) : CoreStartResult
    data class Failure(val error: CoreError) : CoreStartResult
}

/**
 * Abstraction over the native proxy core (Xray-core, sing-box, …).
 *
 * The whole VPN layer talks ONLY to this interface. A real core is plugged in by
 * implementing this interface with a JNI wrapper — see docs/CORE_INTEGRATION.md.
 * Nothing in the UI or the service assumes a particular core.
 */
interface ProxyCore {
    /** Stable id, e.g. "mock", "sing-box". */
    val id: String

    /** False for stub/mocked cores — the service refuses to open tunnels for those. */
    val isReal: Boolean

    val status: StateFlow<CoreStatus>

    suspend fun start(
        profile: ConfigProfile,
        tun: TunParams,
        listener: (CoreEvent) -> Unit,
    ): CoreStartResult

    suspend fun stop()

    /** Null when the core cannot report traffic (mock core). */
    fun trafficStats(): Flow<TrafficStats>?

    fun lastError(): CoreError?
}
