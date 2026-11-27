package com.confighub.app.core.model

/**
 * A parsed proxy configuration profile.
 *
 * SECURITY: [secret] and [secondarySecret] hold credential material (UUID / password).
 * They are NEVER written to logs — the manual [toString] below excludes them, and all
 * logging goes through `core.security.SecretMasker`. Persistence encrypts them
 * per-field (see `data.db`).
 */
data class ConfigProfile(
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val secret: String,
    val secretKind: SecretKind,
    /** TUIC-style second credential (password next to the UUID). */
    val secondarySecret: String? = null,
    /** Shadowsocks cipher method. */
    val method: String? = null,
    /** VMess security (scy). */
    val cipher: String? = null,
    val alterId: Int? = null,
    val network: Transport = Transport.TCP,
    val tls: TlsMode = TlsMode.NONE,
    val sni: String? = null,
    val alpn: List<String>? = null,
    val fingerprint: String? = null,
    val wsPath: String? = null,
    val wsHost: String? = null,
    val grpcServiceName: String? = null,
    /** VLESS flow control (e.g. xtls-rprx-vision). */
    val flow: String? = null,
    /** REALITY public key. */
    val publicKey: String? = null,
    /** REALITY short id. */
    val shortId: String? = null,
    val allowInsecure: Boolean = false,
    /** Shadowsocks plugin string (e.g. obfs-local;...). */
    val plugin: String? = null,
    /** Human-readable tag / remark from the source (not a secret). */
    val name: String? = null,
) {
    /**
     * Deliberately excludes [secret], [secondarySecret] and other credential fields so
     * that an accidental `println(profile)` or exception message cannot leak secrets.
     */
    override fun toString(): String =
        "ConfigProfile(protocol=$protocol, host=$host, port=$port, secret=REDACTED, " +
            "network=$network, tls=$tls, sni=$sni, name=$name)"
}
