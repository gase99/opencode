package com.confighub.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.data.repo.ConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session state machine.
 *
 * INVARIANTS (enforced by design and documented in README/SECURITY):
 *  * every connect is USER-initiated and passes through `VpnService.prepare()`
 *    (the mandatory Android VPN consent dialog);
 *  * no auto-reconnect, no silent retry, no boot-start — a disconnect is final until
 *    the user explicitly connects again;
 *  * the mock core can never produce a Connected state.
 */
sealed class VpnState {
    data object Idle : VpnState()
    data class NeedsConsent(val pendingHash: String) : VpnState()
    data object Connecting : VpnState()
    data class Connected(val hash: String, val displayName: String?) : VpnState()
    data object Disconnecting : VpnState()
    data class Failed(val reason: String) : VpnState()
}

@Singleton
class VpnSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository,
    private val coreRegistry: CoreRegistry,
) {

    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _coreStatus = MutableStateFlow<CoreStatus?>(null)
    val coreStatus: StateFlow<CoreStatus?> = _coreStatus.asStateFlow()

    @Volatile
    private var pendingHash: String? = null

    val core: ProxyCore get() = coreRegistry.get()

    /**
     * Starts the connect flow. Returns the mandatory consent [Intent] when Android
     * requires it (state → NeedsConsent), or null when consent is already granted
     * (state → Connecting, the foreground service is started immediately).
     */
    fun connect(hash: String): Intent? {
        if (_state.value is VpnState.Connecting || _state.value is VpnState.Connected) {
            return null
        }
        val consentIntent = VpnService.prepare(context)
        return if (consentIntent != null) {
            pendingHash = hash
            _state.value = VpnState.NeedsConsent(hash)
            consentIntent
        } else {
            startServiceConnect(hash)
            null
        }
    }

    /** Called after the user APPROVED the consent dialog. */
    fun onConsentGranted() {
        val hash = pendingHash
        pendingHash = null
        if (hash != null) startServiceConnect(hash)
    }

    /** Called after the user DENIED the consent dialog — no retry, no fallback. */
    fun onConsentDenied() {
        pendingHash = null
        _state.value = VpnState.Idle
    }

    fun consentLaunched() {
        // The Activity launched the dialog; keep NeedsConsent until result arrives.
    }

    private fun startServiceConnect(hash: String) {
        _state.value = VpnState.Connecting
        val intent = Intent(context, ConfigHubVpnService::class.java)
            .setAction(ConfigHubVpnService.ACTION_CONNECT)
            .putExtra(ConfigHubVpnService.EXTRA_HASH, hash)
        context.startForegroundService(intent)
    }

    fun requestDisconnect() {
        _state.value = VpnState.Disconnecting
        val intent = Intent(context, ConfigHubVpnService::class.java)
            .setAction(ConfigHubVpnService.ACTION_DISCONNECT)
        context.startService(intent)
    }

    suspend fun resolveProfile(hash: String): ConfigProfile? =
        configRepository.getConfigOnce(hash)?.let { configRepository.resolveProfile(it) }

    // --- callbacks used by ConfigHubVpnService ---

    fun onCoreStarted(hash: String, displayName: String?) {
        _coreStatus.value = CoreStatus.RUNNING
        _state.value = VpnState.Connected(hash, displayName)
    }

    fun onCoreFailed(reason: String) {
        _coreStatus.value = CoreStatus.FAILED
        _state.value = VpnState.Failed(reason)
    }

    fun onDisconnected() {
        _coreStatus.value = CoreStatus.IDLE
        _state.value = VpnState.Idle
    }
}
