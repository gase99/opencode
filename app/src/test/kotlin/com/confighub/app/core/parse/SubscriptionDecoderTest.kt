package com.confighub.app.core.parse

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class SubscriptionDecoderTest {

    private val uriA = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@a.example.com:443#a"
    private val uriB = "trojan://pw@b.example.com:443#b"
    private val uriC = "ss://${Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:pw".toByteArray())}@c.example.com:8388#c"

    @Test
    fun `decodes plain text lines`() {
        val payload = listOf(uriA, "", "  ", uriB, uriC).joinToString("\n")
        val lines = SubscriptionDecoder.decode(payload)
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).isEqualTo(uriA)
    }

    @Test
    fun `decodes base64 blob with crlf`() {
        val payload = Base64.getEncoder().encodeToString(listOf(uriA, uriB).joinToString("\r\n").toByteArray())
        val lines = SubscriptionDecoder.decode(payload)
        assertThat(lines).containsExactly(uriA, uriB).inOrder()
    }

    @Test
    fun `decodes double base64 payload`() {
        val inner = Base64.getEncoder().encodeToString(uriA.toByteArray())
        val outer = Base64.getUrlEncoder().withoutPadding().encodeToString(inner.toByteArray())
        val lines = SubscriptionDecoder.decode(outer)
        assertThat(lines).containsExactly(uriA)
    }

    @Test
    fun `decodes json object with outbounds array`() {
        val payload = """
            {"outbounds":[
              {"protocol":"vmess","settings":{"vnext":[{"address":"a.example.com","port":443,
                "users":[{"id":"b831381d-6324-4d53-ad4f-8cda48b30811","alterId":0,"security":"auto"}]}]},
               "streamSettings":{"network":"tcp"},"tag":"o1"},
              {"protocol":"trojan","settings":{"servers":[{"address":"b.example.com","port":443,
                "password":"pw123"}]},"tag":"o2"}
            ]}
        """.trimIndent()
        val candidates = SubscriptionDecoder.decode(payload)
        assertThat(candidates).hasSize(2)
        assertThat(candidates[0]).startsWith("{")
    }

    @Test
    fun `decodes json array of uri strings`() {
        val payload = """["$uriA", "$uriB"]"""
        val lines = SubscriptionDecoder.decode(payload)
        assertThat(lines).containsExactly(uriA, uriB).inOrder()
    }

    @Test
    fun `empty payload yields empty list`() {
        assertThat(SubscriptionDecoder.decode("")).isEmpty()
        assertThat(SubscriptionDecoder.decode("   \n  ")).isEmpty()
    }

    @Test
    fun `junk payload returns raw line for parser to reject`() {
        val lines = SubscriptionDecoder.decode("hello world")
        assertThat(lines).containsExactly("hello world")
    }
}

class V2rayJsonOutboundParserTest {

    @Test
    fun `parses vmess outbound with tls and ws`() {
        val json = """
            {"protocol":"vmess","tag":"node-1",
             "settings":{"vnext":[{"address":"a.example.com","port":443,
               "users":[{"id":"b831381d-6324-4d53-ad4f-8cda48b30811","alterId":2,"security":"auto"}]}]},
             "streamSettings":{"network":"ws","security":"tls",
               "wsSettings":{"path":"/ws","headers":{"Host":"h.example.com"}},
               "tlsSettings":{"serverName":"sni.example.com","fingerprint":"chrome","alpn":["h2","http/1.1"]}}}
        """.trimIndent()
        val profile = V2rayJsonOutboundParser.parse(json)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.host).isEqualTo("a.example.com")
        assertThat(profile.port).isEqualTo(443)
        assertThat(profile.secret).isEqualTo("b831381d-6324-4d53-ad4f-8cda48b30811")
        assertThat(profile.alterId).isEqualTo(2)
        assertThat(profile.network.name).isEqualTo("WS")
        assertThat(profile.tls.name).isEqualTo("TLS")
        assertThat(profile.sni).isEqualTo("sni.example.com")
        assertThat(profile.wsPath).isEqualTo("/ws")
        assertThat(profile.wsHost).isEqualTo("h.example.com")
        assertThat(profile.name).isEqualTo("node-1")
    }

    @Test
    fun `parses trojan outbound`() {
        val json = """
            {"protocol":"trojan","settings":{"servers":[{"address":"t.example.com","port":443,"password":"pw"}]},
             "streamSettings":{"network":"tcp","security":"tls"},"tag":"trojan-tag"}
        """.trimIndent()
        val profile = V2rayJsonOutboundParser.parse(json)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.secret).isEqualTo("pw")
        assertThat(profile.tls.name).isEqualTo("TLS")
        assertThat(profile.name).isEqualTo("trojan-tag")
    }

    @Test
    fun `parses shadowsocks outbound`() {
        val json = """
            {"protocol":"shadowsocks","settings":{"servers":[{"address":"1.2.3.4","port":8388,
              "method":"chacha20-ietf-poly1305","password":"pw"}]}}
        """.trimIndent()
        val profile = V2rayJsonOutboundParser.parse(json)
        assertThat(profile).isNotNull()
        profile!!
        assertThat(profile.method).isEqualTo("chacha20-ietf-poly1305")
        assertThat(profile.secret).isEqualTo("pw")
    }

    @Test
    fun `rejects unknown outbound protocol`() {
        val json = """{"protocol":"wireguard","settings":{}}"""
        assertThat(V2rayJsonOutboundParser.parse(json)).isNull()
    }
}
