package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DedupHasherTest {

    private fun profile(
        host: String = "Example.com",
        secret: String = "b831381d-6324-4d53-ad4f-8cda48b30811",
        name: String? = "tag",
    ) = ConfigProfile(
        protocol = Protocol.VLESS,
        host = host,
        port = 443,
        secret = secret,
        secretKind = SecretKind.UUID,
        tls = com.confighub.app.core.model.TlsMode.REALITY,
        sni = "www.example.com",
        name = name,
    )

    @Test
    fun `same config with different tags deduplicates`() {
        assertThat(DedupHasher.hash(profile(name = "tag-a")))
            .isEqualTo(DedupHasher.hash(profile(name = "tag-b")))
    }

    @Test
    fun `host comparison is case-insensitive`() {
        assertThat(DedupHasher.hash(profile(host = "example.com")))
            .isEqualTo(DedupHasher.hash(profile(host = "EXAMPLE.COM")))
    }

    @Test
    fun `different secret produces different hash`() {
        val other = profile(secret = "b831381d-6324-4d53-ad4f-8cda48b30812")
        assertThat(DedupHasher.hash(profile())).isNotEqualTo(DedupHasher.hash(other))
    }

    @Test
    fun `different port produces different hash`() {
        val other = profile().copy(port = 8443)
        assertThat(DedupHasher.hash(profile())).isNotEqualTo(DedupHasher.hash(other))
    }

    @Test
    fun `hash is 64 hex chars and contains no secret material`() {
        val hash = DedupHasher.hash(profile())
        assertThat(hash).matches("^[0-9a-f]{64}$")
        assertThat(hash.contains(profile().secret)).isFalse()
        assertThat(hash.contains("example.com")).isFalse()
    }
}
