package com.confighub.app.core.security

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecretMaskerTest {

    private val secret = "b831381d-6324-4d53-ad4f-8cda48b30811"

    @Test
    fun `masks uuid shapes in arbitrary text`() {
        val sanitized = SecretMasker.sanitize("user id=$secret was used")
        assertThat(sanitized.contains(secret)).isFalse()
        assertThat(sanitized.contains(SecretMasker.MASK)).isTrue()
    }

    @Test
    fun `masks password key-value pairs`() {
        val sanitized = SecretMasker.sanitize("login ok with password=superSecret123 done")
        assertThat(sanitized.contains("superSecret123")).isFalse()
    }

    @Test
    fun `masks uri userinfo`() {
        val sanitized = SecretMasker.sanitize("connecting to //alice:hunter2@trojan.example.com:443")
        assertThat(sanitized.contains("hunter2")).isFalse()
    }

    @Test
    fun `redact replaces exact occurrences`() {
        val out = SecretMasker.redact("token=abc123 and token=abc123", "abc123")
        assertThat(out).doesNotContain("abc123")
    }

    @Test
    fun `normal text is untouched`() {
        val text = "refreshed 12 configs from 3 sources in 420 ms"
        assertThat(SecretMasker.sanitize(text)).isEqualTo(text)
    }

    @Test
    fun `config profile toString never contains the secret`() {
        val profile = ConfigProfile(
            protocol = Protocol.VMESS,
            host = "example.com",
            port = 443,
            secret = secret,
            secretKind = SecretKind.UUID,
            name = "my node",
        )
        val asText = profile.toString()
        assertThat(asText.contains(secret)).isFalse()
        assertThat(asText).contains("example.com")
        assertThat(asText).contains("REDACTED")
    }
}
