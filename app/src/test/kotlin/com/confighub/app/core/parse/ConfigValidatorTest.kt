package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigValidatorTest {

    private val validator = ConfigValidator(allowExperimental = true)

    private fun vmess(): ConfigProfile = ConfigProfile(
        protocol = Protocol.VMESS,
        host = "example.com",
        port = 443,
        secret = "b831381d-6324-4d53-ad4f-8cda48b30811",
        secretKind = SecretKind.UUID,
    )

    @Test
    fun `valid vmess passes`() {
        assertThat(validator.validate(vmess())).isEmpty()
    }

    @Test
    fun `non-uuid id rejected for vmess`() {
        val issues = validator.validate(vmess().copy(secret = "not-a-uuid"))
        assertThat(issues.map { it.kind }).containsExactly(ConfigValidator.Kind.BAD_SECRET)
    }

    @Test
    fun `32-hex id accepted`() {
        val issues = validator.validate(vmess().copy(secret = "6ba7b8109dad11d180b400c04fd430c8"))
        assertThat(issues).isEmpty()
    }

    @Test
    fun `private host rejected`() {
        val issues = validator.validate(vmess().copy(host = "127.0.0.1"))
        assertThat(issues.map { it.kind }).contains(ConfigValidator.Kind.UNSAFE_HOST)
    }

    @Test
    fun `localhost rejected`() {
        val issues = validator.validate(vmess().copy(host = "localhost"))
        assertThat(issues.map { it.kind }).contains(ConfigValidator.Kind.UNSAFE_HOST)
    }

    @Test
    fun `out of range port rejected`() {
        val issues = validator.validate(vmess().copy(port = 70000))
        assertThat(issues.map { it.kind }).contains(ConfigValidator.Kind.BAD_PORT)
    }

    @Test
    fun `missing secret rejected`() {
        val issues = validator.validate(vmess().copy(secret = ""))
        assertThat(issues.map { it.kind }).contains(ConfigValidator.Kind.MISSING_SECRET)
    }

    @Test
    fun `unencrypted shadowsocks method rejected`() {
        val ss = ConfigProfile(
            protocol = Protocol.SHADOWSOCKS,
            host = "example.com",
            port = 8388,
            secret = "pw",
            secretKind = SecretKind.PASSWORD,
            method = "none",
        )
        assertThat(validator.validate(ss).map { it.kind }).contains(ConfigValidator.Kind.UNSAFE_METHOD)
    }

    @Test
    fun `reality without public key rejected`() {
        val reality = vmess().copy(tls = TlsMode.REALITY)
        assertThat(validator.validate(reality).map { it.kind })
            .contains(ConfigValidator.Kind.INCOMPLETE_REALITY)
    }

    @Test
    fun `experimental protocol gated when disabled`() {
        val strict = ConfigValidator(allowExperimental = false)
        val hy2 = ConfigProfile(
            protocol = Protocol.HYSTERIA2,
            host = "example.com",
            port = 443,
            secret = "pw",
            secretKind = SecretKind.PASSWORD,
        )
        assertThat(strict.validate(hy2).map { it.kind }).contains(ConfigValidator.Kind.EXPERIMENTAL_DISABLED)
        assertThat(validator.validate(hy2).map { it.kind }).doesNotContain(
            ConfigValidator.Kind.EXPERIMENTAL_DISABLED,
        )
    }
}
