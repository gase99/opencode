package com.confighub.app.core.parse

import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VlessTrojanParserTest {

    @Test
    fun `parses reality vless link with encoded tag`() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443" +
            "?type=ws&security=reality&sni=www.example.com&fp=chrome&pbk=pubkey123&sid=abcd" +
            "&flow=xtls-rprx-vision&path=%2Fws&host=h.example.com#My%20Tag"
        val profile = VlessLinkParser.parse(uri)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.host).isEqualTo("example.com")
        assertThat(profile.port).isEqualTo(443)
        assertThat(profile.secret).isEqualTo("b831381d-6324-4d53-ad4f-8cda48b30811")
        assertThat(profile.network).isEqualTo(Transport.WS)
        assertThat(profile.tls).isEqualTo(TlsMode.REALITY)
        assertThat(profile.sni).isEqualTo("www.example.com")
        assertThat(profile.publicKey).isEqualTo("pubkey123")
        assertThat(profile.shortId).isEqualTo("abcd")
        assertThat(profile.flow).isEqualTo("xtls-rprx-vision")
        assertThat(profile.wsPath).isEqualTo("/ws")
        assertThat(profile.wsHost).isEqualTo("h.example.com")
        assertThat(profile.name).isEqualTo("My Tag")
    }

    @Test
    fun `vless defaults to none tls and tcp when params missing`() {
        val profile = VlessLinkParser.parse("vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com")
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.port).isEqualTo(443)
        assertThat(profile.tls).isEqualTo(TlsMode.NONE)
        assertThat(profile.network).isEqualTo(Transport.TCP)
    }

    @Test
    fun `vless allowInsecure flag`() {
        val profile = VlessLinkParser.parse(
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443?allowInsecure=1",
        )
        assertThat(profile!!.allowInsecure).isTrue()
    }

    @Test
    fun `vless without uuid rejected`() {
        assertThat(VlessLinkParser.parse("vless://@example.com:443")).isNull()
    }

    @Test
    fun `parses trojan link with grpc and default tls`() {
        val uri = "trojan://s3cret-pass@example.com:443?sni=s.example.com&type=grpc&serviceName=trojanSvc#t1"
        val profile = TrojanLinkParser.parse(uri)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.secret).isEqualTo("s3cret-pass")
        assertThat(profile.secretKind).isEqualTo(SecretKind.PASSWORD)
        assertThat(profile.tls).isEqualTo(TlsMode.TLS)
        assertThat(profile.network).isEqualTo(Transport.GRPC)
        assertThat(profile.grpcServiceName).isEqualTo("trojanSvc")
        assertThat(profile.sni).isEqualTo("s.example.com")
        assertThat(profile.name).isEqualTo("t1")
    }

    @Test
    fun `trojan without password rejected`() {
        assertThat(TrojanLinkParser.parse("trojan://@example.com:443")).isNull()
    }
}
