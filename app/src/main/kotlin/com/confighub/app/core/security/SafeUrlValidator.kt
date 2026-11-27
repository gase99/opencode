package com.confighub.app.core.security

import java.net.URI

/**
 * Validates source URLs before anything is fetched.
 *
 * Policy (privacy-respecting defaults):
 * * only `https` is allowed by default — plain `http` sources can be enabled
 *   explicitly in Settings (they expose the subscription content on the wire);
 * * URLs carrying credentials (user:pass@) are rejected — tokens belong to the
 *   path/query and are never persisted beyond the encrypted database;
 * * IP literals in private/loopback/documentation ranges are rejected (SSRF guard).
 */
class SafeUrlValidator(private val allowInsecureHttp: Boolean = false) {

    sealed class Result {
        data object Ok : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun validate(url: String): Result {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return Result.Rejected("unparseable URL")
        val scheme = uri.scheme?.lowercase() ?: return Result.Rejected("missing scheme")
        if (scheme != "https" && scheme != "http") return Result.Rejected("scheme '$scheme' not allowed")
        if (scheme == "http" && !allowInsecureHttp) {
            return Result.Rejected("plain http sources are disabled in privacy settings")
        }
        val host = uri.host ?: return Result.Rejected("missing host")
        if (uri.userInfo != null) return Result.Rejected("credentials in URL are not allowed")
        if (PrivateAddressGuard.isPrivateLiteral(host)) return Result.Rejected("private/loopback address")
        val port = uri.port
        if (port != -1 && port !in 1..65535) return Result.Rejected("invalid port")
        return Result.Ok
    }
}
