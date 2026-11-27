package com.confighub.app.core.parse

import java.io.ByteArrayOutputStream

/**
 * Minimal, dependency-free parser for proxy share-link URIs.
 *
 * Proxy share links in the wild are frequently not RFC-compliant (bare IPv6, percent
 * encoded tags, comma-separated port ranges for Hysteria2). `java.net.URI` rejects
 * many of them, so parsing is done manually here.
 */
internal object UriParts {

    private val SCHEME_RE = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://(.*)$", RegexOption.DOT_MATCHES_ALL)

    data class Parts(
        val scheme: String,
        val userinfo: String?,
        val host: String,
        val port: Int?,
        val query: Map<String, String>,
        val fragment: String?,
    )

    fun parse(uri: String): Parts? {
        val match = SCHEME_RE.find(uri.trim()) ?: return null
        val scheme = match.groupValues[1].lowercase()
        var rest = match.groupValues[2]

        var fragment: String? = null
        val hashIndex = rest.indexOf('#')
        if (hashIndex >= 0) {
            fragment = percentDecode(rest.substring(hashIndex + 1))
            rest = rest.substring(0, hashIndex)
        }

        val query: Map<String, String>
        val queryIndex = rest.indexOf('?')
        if (queryIndex >= 0) {
            query = parseQuery(rest.substring(queryIndex + 1))
            rest = rest.substring(0, queryIndex)
        } else {
            query = emptyMap()
        }

        val atIndex = rest.lastIndexOf('@')
        val userinfo: String?
        val hostPort: String
        if (atIndex >= 0) {
            userinfo = percentDecode(rest.substring(0, atIndex))
            hostPort = rest.substring(atIndex + 1)
        } else {
            userinfo = null
            hostPort = rest
        }

        val host: String
        val port: Int?
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            host = hostPort.substring(1, close)
            val after = hostPort.substring(close + 1)
            port = if (after.startsWith(":")) parsePort(after.substring(1)) else null
        } else {
            val colonCount = hostPort.count { it == ':' }
            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon >= 0 && colonCount == 1) {
                host = hostPort.substring(0, lastColon)
                port = parsePort(hostPort.substring(lastColon + 1))
            } else {
                // Bare IPv6 without brackets, or plain host without port.
                host = hostPort
                port = null
            }
        }

        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return null
        return Parts(scheme, userinfo, trimmedHost, port, query, fragment)
    }

    fun parseQuery(query: String): Map<String, String> = query
        .split('&')
        .filter { it.isNotBlank() }
        .associate { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) {
                percentDecode(pair) to ""
            } else {
                percentDecode(pair.substring(0, eq)) to percentDecode(pair.substring(eq + 1))
            }
        }

    /** Hysteria2-style port fields may be "443,8443" ranges — the first value wins. */
    private fun parsePort(raw: String): Int? =
        raw.trim().substringBefore(',').toIntOrNull()

    /**
     * Percent-decoding that does NOT treat '+' as space (unlike URLDecoder) and keeps
     * non-ASCII characters (e.g. Persian remarks) intact.
     */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = ByteArrayOutputStream(s.length)
        val n = s.length
        var i = 0
        while (i < n) {
            val c = s[i]
            if (c == '%' && i + 2 < n) {
                val byte = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (byte != null) {
                    out.write(byte)
                    i += 3
                    continue
                }
            }
            if (c.code < 0x80) {
                out.write(c.code)
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
            }
            i++
        }
        return out.toString("UTF-8")
    }
}
