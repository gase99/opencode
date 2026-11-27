package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.security.PrivateAddressGuard

/**
 * Semantic validation of parsed profiles: rejects malformed, obviously unsafe or
 * unsupported configurations before they enter the cache.
 *
 * Syntax parsing (URI shape) is done by the parsers; this class applies policy:
 * host sanity, port range, secret presence/format, safe SS cipher, REALITY
 * completeness and the experimental-protocol gate.
 */
class ConfigValidator(private val allowExperimental: Boolean = false) {

    enum class Kind {
        MALFORMED_HOST,
        UNSAFE_HOST,
        BAD_PORT,
        MISSING_SECRET,
        BAD_SECRET,
        UNSAFE_METHOD,
        INCOMPLETE_REALITY,
        EXPERIMENTAL_DISABLED,
    }

    data class Issue(val kind: Kind, val detail: String)

    fun validate(profile: ConfigProfile): List<Issue> {
        val issues = mutableListOf<Issue>()

        if (profile.protocol.isExperimental && !allowExperimental) {
            issues += Issue(Kind.EXPERIMENTAL_DISABLED, "protocol ${profile.protocol.wireName} is disabled by default")
        }

        val host = profile.host.trim()
        if (host.isEmpty() || host.length > 253 || host.contains(Regex("\\s"))) {
            issues += Issue(Kind.MALFORMED_HOST, "host is blank, too long or contains whitespace")
        } else if (isUnsafeHost(host)) {
            issues += Issue(Kind.UNSAFE_HOST, "host points to a local/private or documentation address")
        }

        if (profile.port !in 1..65535) {
            issues += Issue(Kind.BAD_PORT, "port ${profile.port} outside 1..65535")
        }

        if (profile.secret.isBlank()) {
            issues += Issue(Kind.MISSING_SECRET, "credential (uuid/password) is missing")
        } else if (profile.protocol == com.confighub.app.core.model.Protocol.VMESS ||
            profile.protocol == com.confighub.app.core.model.Protocol.VLESS
        ) {
            if (!isUuidLike(profile.secret)) {
                issues += Issue(Kind.BAD_SECRET, "protocol requires a UUID-formatted id")
            }
        }

        val method = profile.method
        if (profile.protocol == com.confighub.app.core.model.Protocol.SHADOWSOCKS) {
            if (method.isNullOrBlank()) {
                issues += Issue(Kind.BAD_SECRET, "shadowsocks cipher method is missing")
            } else if (method.trim().lowercase() in UNSAFE_METHODS) {
                issues += Issue(Kind.UNSAFE_METHOD, "unencrypted shadowsocks method '$method' rejected")
            }
        }

        if (profile.tls == com.confighub.app.core.model.TlsMode.REALITY && profile.publicKey.isNullOrBlank()) {
            issues += Issue(Kind.INCOMPLETE_REALITY, "reality config without public key")
        }

        return issues
    }

    private fun isUnsafeHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local", ignoreCase = true) ||
            host.endsWith(".localhost", ignoreCase = true)
        ) {
            return true
        }
        return PrivateAddressGuard.isPrivateLiteral(host)
    }

    private fun isUuidLike(value: String): Boolean {
        if (value.length == 36 && value.count { it == '-' } == 4) {
            return value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        }
        return value.length == 32 && value.matches(Regex("[0-9a-fA-F]{32}"))
    }

    companion object {
        /** Unencrypted SS ciphers are rejected outright. */
        private val UNSAFE_METHODS = setOf("none", "plain", "null", "simple-obfs")
    }
}
