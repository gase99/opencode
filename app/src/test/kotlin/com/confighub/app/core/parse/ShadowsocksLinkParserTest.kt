package com.confighub.app.core.parse

import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class ShadowsocksLinkParserTest {

    private fun urlB64(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    @Test
    fun `parses SIP002 link with base64 userinfo`() {
        val userInfo = urlB64("aes-256-gcm:secretpw")
        val uri = "ss://$userInfo@1.2.3.4:8388#ss-node"
        val profile = ShadowsocksLinkParser.parse(uri)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.protocol).isEqualTo(Protocol.SHADOWSOCKS)
        assertThat(profile.method).isEqualTo("aes-256-gcm")
        assertThat(profile.secret).isEqualTo("secretpw")
        assertThat(profile.secretKind).isEqualTo(SecretKind.PASSWORD)
        assertThat(profile.host).isEqualTo("1.2.3.4")
        assertThat(profile.port).isEqualTo(8388)
        assertThat(profile.name).isEqualTo("ss-node")
    }

    @Test
    fun `parses plain percent-encoded userinfo`() {
        val profile = ShadowsocksLinkParser.parse("ss://aes-256-gcm:my%40pass@example.com:443#tag")
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.method).isEqualTo("aes-256-gcm")
        assertThat(profile.secret).isEqualTo("my@pass")
        assertThat(profile.host).isEqualTo("example.com")
    }

    @Test
    fun `parses legacy fully-base64 link`() {
        val payload = urlB64("aes-256-gcm:pass@1.2.3.4:8388")
        val profile = ShadowsocksLinkParser.parse("ss://$payload#legacy")
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.method).isEqualTo("aes-256-gcm")
        assertThat(profile.secret).isEqualTo("pass")
        assertThat(profile.host).isEqualTo("1.2.3.4")
        assertThat(profile.port).isEqualTo(8388)
        assertThat(profile.name).isEqualTo("legacy")
    }

    @Test
    fun `keeps plugin parameter`() {
        val userInfo = urlB64("chacha20-ietf-poly1305:pw")
        val uri = "ss://$userInfo@example.com:8388?plugin=obfs-local%3Bobfs%3Dhttp%3Bobfs-host%3Dexample.com#p"
        val profile = ShadowsocksLinkParser.parse(uri)
        assertThat(profile!!.plugin).contains("obfs-local")
    }

    @Test
    fun `rejects garbage`() {
        assertThat(ShadowsocksLinkParser.parse("ss://!!!")).isNull()
        assertThat(ShadowsocksLinkParser.parse("ss://")).isNull()
    }
}
