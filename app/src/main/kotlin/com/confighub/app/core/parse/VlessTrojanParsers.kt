package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport

/**
 * Parser for VLESS share links:
 * `vless://uuid@host:port?type=ws&security=reality&sni=...&pbk=...&sid=...#tag`
 */
object VlessLinkParser : LinkParser {

    override fun parse(candidate: String): ConfigProfile? {
        if (!candidate.lowercase().startsWith("vless://")) return null
        val parts = UriParts.parse(candidate) ?: return null
        val uuid = parts.userinfo?.trim() ?: return null
        if (uuid.isBlank()) return null

        return ConfigProfile(
            protocol = Protocol.VLESS,
            host = parts.host,
            port = parts.port ?: 443,
            secret = uuid,
            secretKind = SecretKind.UUID,
            network = Transport.fromWire(parts.query["type"]),
            tls = TlsMode.fromWire(parts.query["security"]),
            sni = parts.query["sni"],
            alpn = parts.query["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            fingerprint = parts.query["fp"],
            wsPath = parts.query["path"],
            wsHost = parts.query["host"],
            grpcServiceName = parts.query["serviceName"],
            flow = parts.query["flow"],
            publicKey = parts.query["pbk"],
            shortId = parts.query["sid"],
            allowInsecure = parseBool(parts.query["allowInsecure"]) || parseBool(parts.query["insecure"]),
            name = parts.fragment,
        )
    }

    internal fun parseBool(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }
}

/**
 * Parser for Trojan share links:
 * `trojan://password@host:port?sni=...&type=ws&path=...#tag`
 * Trojan is TLS-based by definition, so the default TLS mode is TLS.
 */
object TrojanLinkParser : LinkParser {

    override fun parse(candidate: String): ConfigProfile? {
        if (!candidate.lowercase().startsWith("trojan://")) return null
        val parts = UriParts.parse(candidate) ?: return null
        val password = parts.userinfo?.trim() ?: return null
        if (password.isBlank()) return null

        return ConfigProfile(
            protocol = Protocol.TROJAN,
            host = parts.host,
            port = parts.port ?: 443,
            secret = password,
            secretKind = SecretKind.PASSWORD,
            network = Transport.fromWire(parts.query["type"]),
            tls = if (parts.query["security"] == null) TlsMode.TLS else TlsMode.fromWire(parts.query["security"]),
            sni = parts.query["sni"],
            alpn = parts.query["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            fingerprint = parts.query["fp"],
            wsPath = parts.query["path"],
            wsHost = parts.query["host"],
            grpcServiceName = parts.query["serviceName"],
            allowInsecure = VlessLinkParser.parseBool(parts.query["allowInsecure"]),
            name = parts.fragment,
        )
    }
}
