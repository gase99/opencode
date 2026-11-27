package com.confighub.app.core.parse

import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class VmessLinkParserTest {

    private fun encode(json: String): String =
        Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

    private val sampleJson = """
        {"v":"2","ps":"سرور تهران","add":"example.com","port":"443",
         "id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto",
         "net":"ws","type":"none","host":"cdn.example.com","path":"/ray",
         "tls":"tls","sni":"sni.example.com","alpn":"h2,http/1.1","fp":"chrome"}
    """.trimIndent()

    @Test
    fun `parses full vmess link with string port`() {
        val profile = VmessLinkParser.parse("vmess://" + encode(sampleJson))
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.protocol.name).isEqualTo("VMESS")
        assertThat(profile.host).isEqualTo("example.com")
        assertThat(profile.port).isEqualTo(443)
        assertThat(profile.secret).isEqualTo("b831381d-6324-4d53-ad4f-8cda48b30811")
        assertThat(profile.secretKind).isEqualTo(SecretKind.UUID)
        assertThat(profile.alterId).isEqualTo(0)
        assertThat(profile.cipher).isEqualTo("auto")
        assertThat(profile.network).isEqualTo(Transport.WS)
        assertThat(profile.tls).isEqualTo(TlsMode.TLS)
        assertThat(profile.sni).isEqualTo("sni.example.com")
        assertThat(profile.wsPath).isEqualTo("/ray")
        assertThat(profile.wsHost).isEqualTo("cdn.example.com")
        assertThat(profile.fingerprint).isEqualTo("chrome")
        assertThat(profile.alpn).containsExactly("h2", "http/1.1").inOrder()
        // Persian remark survives parsing (RTL-safe).
        assertThat(profile.name).isEqualTo("سرور تهران")
    }

    @Test
    fun `parses vmess link with integer port and missing optional fields`() {
        val json = """{"add":"1.2.3.4","port":8443,"id":"6ba7b8109dad11d180b400c04fd430c8","net":"tcp"}"""
        val profile = VmessLinkParser.parse("vmess://" + encode(json))
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.port).isEqualTo(8443)
        assertThat(profile.secret).isEqualTo("6ba7b8109dad11d180b400c04fd430c8")
        assertThat(profile.secretKind).isEqualTo(SecretKind.UUID)
        assertThat(profile.alterId).isEqualTo(0)
        assertThat(profile.tls).isEqualTo(TlsMode.NONE)
        assertThat(profile.name).isNull()
    }

    @Test
    fun `reality marked as REALITY tls mode`() {
        val json = """{"add":"a.example.com","port":443,"id":"b831381d-6324-4d53-ad4f-8cda48b30811","tls":"reality"}"""
        val profile = VmessLinkParser.parse("vmess://" + encode(json))
        assertThat(profile!!.tls).isEqualTo(TlsMode.REALITY)
    }

    @Test
    fun `rejects invalid base64`() {
        assertThat(VmessLinkParser.parse("vmess://!!!not-base64!!!")).isNull()
    }

    @Test
    fun `rejects non-json payload`() {
        assertThat(VmessLinkParser.parse("vmess://" + encode("just some text"))).isNull()
    }

    @Test
    fun `rejects payload missing required fields`() {
        val json = """{"add":"example.com"}"""
        assertThat(VmessLinkParser.parse("vmess://" + encode(json))).isNull()
    }

    @Test
    fun `ignores non-vmess candidates`() {
        assertThat(VmessLinkParser.parse("vless://x@example.com:443")).isNull()
    }
}
