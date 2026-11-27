package com.confighub.app.vpn

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MockProxyCoreTest {

    private fun profile() = ConfigProfile(
        protocol = Protocol.VLESS,
        host = "example.com",
        port = 443,
        secret = "b831381d-6324-4d53-ad4f-8cda48b30811",
        secretKind = SecretKind.UUID,
        network = Transport.TCP,
        tls = TlsMode.TLS,
    )

    @Test
    fun `mock core is explicitly not real`() {
        assertThat(MockProxyCore().isReal).isFalse()
    }

    @Test
    fun `start honestly reports not-available and never reaches RUNNING`() = runTest {
        val core = MockProxyCore()
        val events = mutableListOf<CoreEvent>()
        val tun = TunParams(vpnFd = -1, mtu = 0, sessionName = "s", vpnAddress = "", vpnDns = "")

        val result = core.start(profile(), tun, events::add)

        assertThat(result).isInstanceOf(CoreStartResult.NotAvailable::class.java)
        val notAvailable = result as CoreStartResult.NotAvailable
        assertThat(notAvailable.reason).contains("not integrated")
        assertThat(notAvailable.reason).contains("no tunnel was opened")
        assertThat(core.status.value).isEqualTo(CoreStatus.FAILED)
        assertThat(core.lastError()?.code).isEqualTo("CORE_NOT_INTEGRATED")
        assertThat(events.map { it.type }).contains(CoreEventType.ERROR)
        // A stub can never report traffic.
        assertThat(core.trafficStats()).isNull()
    }

    @Test
    fun `stop returns to idle`() = runTest {
        val core = MockProxyCore()
        core.stop()
        assertThat(core.status.value).isEqualTo(CoreStatus.IDLE)
    }

    @Test
    fun `registry falls back to mock`() {
        val registry = CoreRegistry(MockProxyCore())
        assertThat(registry.get().isReal).isFalse()
        assertThat(registry.get("unknown-id").isReal).isFalse()
    }
}
