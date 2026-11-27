package com.confighub.app.vpn

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.util.SafeLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Honest stub core: reports clearly that no real proxy engine is integrated in this
 * build and NEVER opens a tunnel. Exists so the whole connection UX, state machine and
 * consent flow can be developed and reviewed without native binaries.
 *
 * See docs/CORE_INTEGRATION.md for replacing this with sing-box / Xray-core.
 */
@Singleton
class MockProxyCore @Inject constructor() : ProxyCore {

    private val _status = MutableStateFlow(CoreStatus.IDLE)
    override val status: StateFlow<CoreStatus> = _status.asStateFlow()

    @Volatile
    private var lastError: CoreError? = null

    override val id: String = "mock"

    override val isReal: Boolean = false

    override suspend fun start(
        profile: ConfigProfile,
        tun: TunParams,
        listener: (CoreEvent) -> Unit,
    ): CoreStartResult {
        _status.value = CoreStatus.STARTING
        SafeLog.i(TAG, "mock core start requested for ${profile.protocol.wireName} ${profile.host}:${profile.port}")
        delay(200)
        _status.value = CoreStatus.FAILED
        val error = CoreError(
            code = "CORE_NOT_INTEGRATED",
            message = "stub build: no real proxy core integrated — no tunnel was opened",
        )
        lastError = error
        listener(CoreEvent(CoreEventType.ERROR, error.message))
        return CoreStartResult.NotAvailable(
            reason = "The proxy core is not integrated in this build (stub). " +
                "See docs/CORE_INTEGRATION.md — no tunnel was opened.",
        )
    }

    override suspend fun stop() {
        _status.value = CoreStatus.IDLE
    }

    override fun trafficStats(): Flow<TrafficStats>? = null

    override fun lastError(): CoreError? = lastError

    private companion object {
        const val TAG = "MockProxyCore"
    }
}
