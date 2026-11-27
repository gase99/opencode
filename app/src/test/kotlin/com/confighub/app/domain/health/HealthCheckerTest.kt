package com.confighub.app.domain.health

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport
import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HealthCheckerTest {

    private val privateIp = InetAddress.getByName("192.168.1.20")

    private class PrivateDns(private val ip: InetAddress) : SystemDnsResolver() {
        override fun resolve(host: String): List<InetAddress> = listOf(ip)
    }

    private fun checker(
        tcpLatencies: List<Int>,
        tlsLatency: Int? = null,
        resolver: SystemDnsResolver = FakeDnsResolver(),
    ): HealthChecker = HealthChecker(
        socketFactory = FakeProbeSocketFactory(tcpLatencies = tcpLatencies, tlsLatency = tlsLatency),
        dnsResolver = resolver,
    )

    private fun profile(tls: TlsMode = TlsMode.NONE) = ConfigProfile(
        protocol = Protocol.VLESS,
        host = "example.com",
        port = 443,
        secret = "b831381d-6324-4d53-ad4f-8cda48b30811",
        secretKind = SecretKind.UUID,
        network = Transport.TCP,
        tls = tls,
    )

    @Test
    fun `successful probe computes mean min jitter and dns`() = runTest {
        val result = checker(tcpLatencies = listOf(100, 140, 120)).probe(profile())

        assertThat(result.ok).isTrue()
        assertThat(result.dnsMs).isNotNull()
        assertThat(result.tcpMeanMs).isEqualTo(120)
        assertThat(result.tcpMinMs).isEqualTo(100)
        // jitter = mean(|100-140|, |140-120|) = 30
        assertThat(result.jitterMs).isEqualTo(30)
        assertThat(result.tlsProbed).isFalse()
        assertThat(result.error).isNull()
    }

    @Test
    fun `tls config is probed when mode is TLS`() = runTest {
        val result = checker(tcpLatencies = listOf(80), tlsLatency = 200).probe(profile(tls = TlsMode.TLS))
        assertThat(result.ok).isTrue()
        assertThat(result.tlsMs).isEqualTo(200)
        assertThat(result.tlsProbed).isTrue()
    }

    @Test
    fun `reality configs skip the tls probe`() = runTest {
        val result = checker(tcpLatencies = listOf(80)).probe(profile(tls = TlsMode.REALITY))
        assertThat(result.ok).isTrue()
        assertThat(result.tlsProbed).isFalse()
        assertThat(result.tlsMs).isNull()
    }

    @Test
    fun `tls failure marks probe unsuccessful`() = runTest {
        val result = checker(tcpLatencies = listOf(80), tlsLatency = null).probe(profile(tls = TlsMode.TLS))
        assertThat(result.ok).isFalse()
        assertThat(result.error).isEqualTo(ProbeError.TLS_HANDSHAKE)
    }

    @Test
    fun `all tcp failures reported`() = runTest {
        val result = checker(tcpLatencies = emptyList()).probe(profile())
        assertThat(result.ok).isFalse()
        assertThat(result.error).isEqualTo(ProbeError.TCP_CONNECT)
    }

    @Test
    fun `private dns answer blocked by ssrf guard`() = runTest {
        val result = checker(tcpLatencies = listOf(10), resolver = PrivateDns(privateIp)).probe(profile())
        assertThat(result.ok).isFalse()
        assertThat(result.error).isEqualTo(ProbeError.PRIVATE_ADDRESS)
    }

    @Test
    fun `partial tcp failures still produce a result from successful samples`() = runTest {
        // One success, the rest refused → jitter null (single sample), mean = 90.
        val result = checker(tcpLatencies = listOf(90)).probe(profile())
        assertThat(result.ok).isTrue()
        assertThat(result.tcpMeanMs).isEqualTo(90)
        assertThat(result.jitterMs).isNull()
    }
}
