package com.confighub.app.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.net.InetAddress

class PrivateAddressGuardTest {

    @Test
    fun `blocks loopback private cgnat documentation and ula ranges`() {
        val blocked = listOf(
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1",
            "169.254.10.10", "100.64.0.1", "100.127.255.254", "0.0.0.0",
            "192.0.2.9", "198.51.100.7", "203.0.113.5", "198.19.0.1", "198.18.255.255",
            "::1", "fe80::1", "fd12:3456::1", "::ffff:192.168.0.1",
        )
        for (ip in blocked) {
            assertThat(PrivateAddressGuard.isPrivateIp(InetAddress.getByName(ip))).isTrue()
        }
    }

    @Test
    fun `allows public addresses`() {
        val allowed = listOf("8.8.8.8", "1.1.1.1", "2606:4700::1111", "9.9.9.9")
        for (ip in allowed) {
            assertThat(PrivateAddressGuard.isPrivateIp(InetAddress.getByName(ip))).isFalse()
        }
    }

    @Test
    fun `literal detection`() {
        assertThat(PrivateAddressGuard.isPrivateLiteral("127.0.0.1")).isTrue()
        assertThat(PrivateAddressGuard.isPrivateLiteral("[fd00::5]")).isTrue()
        assertThat(PrivateAddressGuard.isPrivateLiteral("example.com")).isFalse()
        assertThat(PrivateAddressGuard.isPrivateLiteral("")).isTrue()
    }

    @Test
    fun `resolver-based check blocks private dns answers`() {
        val blocked = PrivateAddressGuard.resolveAndCheck("host.example") {
            listOf(InetAddress.getByName("10.0.0.5"))
        }
        assertThat(blocked).isTrue()

        val allowed = PrivateAddressGuard.resolveAndCheck("host.example") {
            listOf(InetAddress.getByName("93.184.216.34"))
        }
        assertThat(allowed).isFalse()
    }

    @Test
    fun `empty dns answers are treated as unsafe`() {
        val blocked = PrivateAddressGuard.resolveAndCheck("host.example") { emptyList() }
        assertThat(blocked).isTrue()
    }
}

class SafeUrlValidatorTest {

    private val validator = SafeUrlValidator(allowInsecureHttp = false)
    private val permissive = SafeUrlValidator(allowInsecureHttp = true)

    @Test
    fun `https source passes`() {
        assertThat(validator.validate("https://example.com/sub.txt")).isEqualTo(SafeUrlValidator.Result.Ok)
    }

    @Test
    fun `http source rejected by default and allowed when opted in`() {
        val result = validator.validate("http://example.com/sub.txt")
        assertThat(result).isInstanceOf(SafeUrlValidator.Result.Rejected::class.java)
        assertThat(permissive.validate("http://example.com/sub.txt")).isEqualTo(SafeUrlValidator.Result.Ok)
    }

    @Test
    fun `private literals rejected`() {
        val result = validator.validate("http://192.168.1.10/configs")
        assertThat(result).isInstanceOf(SafeUrlValidator.Result.Rejected::class.java)
    }

    @Test
    fun `credential urls rejected`() {
        val result = validator.validate("https://user:pass@example.com/sub")
        assertThat(result).isInstanceOf(SafeUrlValidator.Result.Rejected::class.java)
    }

    @Test
    fun `non-http schemes rejected`() {
        assertThat(validator.validate("ftp://example.com/x"))
            .isInstanceOf(SafeUrlValidator.Result.Rejected::class.java)
    }

    @Test
    fun `garbage rejected`() {
        assertThat(validator.validate("https://exa mple.com/x"))
            .isInstanceOf(SafeUrlValidator.Result.Rejected::class.java)
    }
}
