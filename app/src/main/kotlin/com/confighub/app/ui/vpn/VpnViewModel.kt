package com.confighub.app.ui.vpn

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.confighub.app.vpn.VpnSessionManager
import com.confighub.app.vpn.VpnState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class VpnViewModel @Inject constructor(
    private val sessionManager: VpnSessionManager,
) : ViewModel() {

    val state: StateFlow<VpnState> = sessionManager.state

    private val consentIntent = MutableStateFlow<Intent?>(null)
    val pendingConsent: StateFlow<Intent?> = consentIntent.asStateFlow()

    fun connect(hash: String) {
        consentIntent.value = sessionManager.connect(hash)
    }

    fun onConsentLaunched() {
        consentIntent.value = null
    }

    fun onConsentGranted() = sessionManager.onConsentGranted()

    fun onConsentDenied() = sessionManager.onConsentDenied()

    fun disconnect() = sessionManager.requestDisconnect()
}
