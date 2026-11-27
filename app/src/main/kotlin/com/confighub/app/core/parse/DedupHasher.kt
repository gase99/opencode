package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import java.security.MessageDigest

/**
 * Stable deduplication hash for configs.
 *
 * Canonical input: protocol + host + port + SHA-256(secret) + network + tls + sni +
 * method/aid/flow/publicKey. The raw secret itself never enters the hash — only its
 * digest — so the stored hash cannot reveal the credential.
 *
 * Two entries that differ only by remark/tag decode to the SAME hash (true duplicates),
 * while a changed credential always produces a different hash.
 */
object DedupHasher {

    fun hash(profile: ConfigProfile): String {
        val secretDigest = sha256Hex(profile.secret + "\u0000" + (profile.secondarySecret ?: ""))
        val canonical = listOf(
            profile.protocol.wireName,
            profile.host.trim().lowercase(),
            profile.port.toString(),
            secretDigest,
            profile.network.wireName,
            profile.tls.wireName,
            profile.sni?.trim()?.lowercase() ?: "-",
            profile.method?.trim()?.lowercase() ?: "-",
            profile.alterId?.toString() ?: "-",
            profile.flow?.trim() ?: "-",
            profile.publicKey?.trim() ?: "-",
        ).joinToString("|")
        return sha256Hex(canonical)
    }

    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
