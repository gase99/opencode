package com.confighub.app.core.parse

import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.TlsMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Hysteria2TuicParserTest {

    @Test
    fun `parses hysteria2 link with port range and insecure flag`() {
        val uri = "hysteria2://auth-pass@example.com:443,8443?sni=h.example.com&insecure=1#hy2-node"
        val profile = Hysteria2LinkParser.parse(uri)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.protocol).isEqualTo(Protocol.HYSTERIA2)
        assertThat(profile.secret).isEqualTo("auth-pass")
        assertThat(profile.port).isEqualTo(443)
        assertThat(profile.tls).isEqualTo(TlsMode.TLS)
        assertThat(profile.sni).isEqualTo("h.example.com")
        assertThat(profile.allowInsecure).isTrue()
        assertThat(profile.name).isEqualTo("hy2-node")
    }

    @Test
    fun `parses hy2 alias without port defaulting to 443`() {
        val profile = Hysteria2LinkParser.parse("hy2://x-pass@example.com#short")
        assertThat(profile!!.port).isEqualTo(443)
        assertThat(profile.protocol).isEqualTo(Protocol.HYSTERIA2)
    }

    @Test
    fun `parses tuic link with uuid and password`() {
        val uri = "tuic://b831381d-6324-4d53-ad4f-8cda48b30811:p%40ss@example.com:8443?congestion_control=bbr&alpn=h3#tuic1"
        val profile = TuicLinkParser.parse(uri)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.protocol).isEqualTo(Protocol.TUIC)
        assertThat(profile.secret).isEqualTo("b831381d-6324-4d53-ad4f-8cda48b30811")
        assertThat(profile.secondarySecret).isEqualTo("p@ss")
        assertThat(profile.port).isEqualTo(8443)
        assertThat(profile.alpn).containsExactly("h3")
    }

    @Test
    fun `experimental flags are set on both protocols`() {
        assertThat(Protocol.HYSTERIA2.isExperimental).isTrue()
        assertThat(Protocol.TUIC.isExperimental).isTrue()
    }
}
