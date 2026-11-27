package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind

/**
 * Parser for Shadowsocks share links.
 *
 * Supported formats:
 * 1. SIP002:  `ss://BASE64URL(method:password)@host:port?plugin=...#tag`
 * 2. Plain userinfo: `ss://method%3Apassword@host:port#tag`
 * 3. Legacy full-Base64: `ss://BASE64URL(method:password@host:port)#tag`
 */
object ShadowsocksLinkParser : LinkParser {

    override fun parse(candidate: String): ConfigProfile? {
        if (!candidate.lowercase().startsWith("ss://")) return null
        val body = candidate.substring("ss://".length)

        var fragment: String? = null
        var rest = body
        val hashIndex = rest.indexOf('#')
        if (hashIndex >= 0) {
            fragment = UriParts.percentDecode(rest.substring(hashIndex + 1))
            rest = rest.substring(0, hashIndex)
        }

        var query: Map<String, String> = emptyMap()
        val queryIndex = rest.indexOf('?')
        if (queryIndex >= 0) {
            query = UriParts.parseQuery(rest.substring(queryIndex + 1))
            rest = rest.substring(0, queryIndex)
        }

        // Legacy form: the entire remainder is Base64 of "method:password@host:port".
        if (!rest.contains('@')) {
            val decoded = Base64Compat.decode(rest) ?: return null
            val atIndex = decoded.lastIndexOf('@')
            if (atIndex < 0) return null
            val hostPort = decoded.substring(atIndex + 1)
            val parts = UriParts.parse("ss://x@$hostPort") ?: return null
            val (method, password) = splitUserInfo(decoded.substring(0, atIndex)) ?: return null
            return build(method, password, parts.host, parts.port, query, fragment)
        }

        val atIndex = rest.lastIndexOf('@')
        if (atIndex < 0) return null
        val userInfoRaw = rest.substring(0, atIndex)
        val parts = UriParts.parse("ss://x@${rest.substring(atIndex + 1)}") ?: return null
        val (method, password) = splitUserInfo(userInfoRaw) ?: return null
        return build(method, password, parts.host, parts.port, query, fragment)
    }

    private fun splitUserInfo(raw: String): Pair<String, String>? {
        val decoded = if (raw.contains(':')) {
            UriParts.percentDecode(raw)
        } else {
            Base64Compat.decode(raw) ?: return null
        }
        val colon = decoded.indexOf(':')
        if (colon <= 0 || colon == decoded.length - 1) return null
        return decoded.substring(0, colon) to decoded.substring(colon + 1)
    }

    private fun build(
        method: String,
        password: String,
        host: String,
        port: Int?,
        query: Map<String, String>,
        fragment: String?,
    ): ConfigProfile = ConfigProfile(
        protocol = Protocol.SHADOWSOCKS,
        host = host,
        port = port ?: 443,
        secret = password,
        secretKind = SecretKind.PASSWORD,
        method = method.trim(),
        plugin = query["plugin"],
        name = fragment,
    )
}
