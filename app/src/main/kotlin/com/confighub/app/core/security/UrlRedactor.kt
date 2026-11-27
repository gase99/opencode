package com.confighub.app.core.security

import java.net.URI

/**
 * Redacts source URLs for logs and exported diagnostics.
 *
 * Subscription URLs frequently embed access tokens in the path or query — both are
 * removed. Only scheme + host (+ optional port) + non-token path segments survive.
 */
object UrlRedactor {

    private val TOKEN_LIKE = Regex("^[A-Za-z0-9_-]{16,}$")

    fun redact(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return "<unparseable-url>"
        val scheme = uri.scheme?.lowercase() ?: return "<unparseable-url>"
        val host = uri.host ?: return "<unparseable-url>"
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = (uri.rawPath ?: "")
            .split('/')
            .filter { it.isNotBlank() }
            .map { segment -> if (TOKEN_LIKE.matches(segment)) "<token>" else segment }
        val suffix = if (path.isEmpty()) "" else "/" + path.joinToString("/")
        return "$scheme://$host$port$suffix"
    }
}
