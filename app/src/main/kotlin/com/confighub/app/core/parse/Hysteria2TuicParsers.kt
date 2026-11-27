package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode

/**
 * Parser for Hysteria2 / hy2 share links (experimental protocol):
 * `hysteria2://password@host:port?sni=...&insecure=1&obfs=salamander&obfs-password=...#tag`
 * Port defaults to 443 and may be a range ("443,8443") — the first value is used.
 */
object Hysteria2LinkParser : LinkParser {

    override fun parse(candidate: String): ConfigProfile? {
        val lower = candidate.lowercase()
        if (!lower.startsWith("hysteria2://") && !lower.startsWith("hy2://")) return null
        val parts = UriParts.parse(candidate) ?: return null
        val password = parts.userinfo?.trim() ?: return null

        return ConfigProfile(
            protocol = Protocol.HYSTERIA2,
            host = parts.host,
            port = parts.port ?: 443,
            secret = password,
            secretKind = SecretKind.PASSWORD,
            tls = TlsMode.TLS, // Hysteria2 is QUIC/TLS based.
            sni = parts.query["sni"],
            allowInsecure = VlessLinkParser.parseBool(parts.query["insecure"]),
            name = parts.fragment,
        )
    }
}

/**
 * Parser for TUIC share links (experimental protocol):
 * `tuic://uuid:password@host:port?congestion_control=bbr&alpn=h3&sni=...#tag`
 */
object TuicLinkParser : LinkParser {

    override fun parse(candidate: String): ConfigProfile? {
        if (!candidate.lowercase().startsWith("tuic://")) return null
        val parts = UriParts.parse(candidate) ?: return null
        val userInfo = parts.userinfo?.trim() ?: return null

        val (uuid, password) = if (userInfo.contains(':')) {
            userInfo.substringBefore(':') to userInfo.substringAfter(':')
        } else {
            userInfo to ""
        }
        if (uuid.isBlank()) return null

        return ConfigProfile(
            protocol = Protocol.TUIC,
            host = parts.host,
            port = parts.port ?: 443,
            secret = uuid,
            secretKind = SecretKind.UUID,
            secondarySecret = password.takeIf { it.isNotBlank() },
            tls = TlsMode.TLS,
            sni = parts.query["sni"],
            alpn = parts.query["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            name = parts.fragment,
        )
    }
}
