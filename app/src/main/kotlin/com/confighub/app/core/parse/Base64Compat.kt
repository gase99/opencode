package com.confighub.app.core.parse

/**
 * Lenient Base64 handling for subscription payloads and share links.
 *
 * Public sources in the wild use standard and URL-safe alphabets, with or without
 * padding, sometimes with embedded whitespace. This helper tries both alphabets and
 * fixes missing padding before decoding.
 */
object Base64Compat {

    /** Decodes [input] as Base64 (standard or URL-safe, padding optional). Returns null on failure. */
    fun decode(input: String): String? {
        val cleaned = input.trim().replace(Regex("\\s+"), "")
        if (cleaned.isEmpty()) return null
        for (decoder in listOf(java.util.Base64.getDecoder(), java.util.Base64.getUrlDecoder())) {
            val bytes = runCatching { decoder.decode(padFor(cleaned)) }.getOrNull() ?: continue
            return String(bytes, Charsets.UTF_8)
        }
        return null
    }

    /**
     * Decodes up to two nested layers (some subscriptions double-encode their payload).
     * Stops early once the result already looks like a list of URIs.
     */
    fun decodeMulti(input: String, maxLayers: Int = 2): String? {
        var current: String? = input
        repeat(maxLayers) {
            val next = current?.let { decode(it) } ?: return null
            current = next
            if (next.contains("://")) return next
        }
        return current
    }

    private fun padFor(s: String): String {
        val remainder = s.length % 4
        return if (remainder == 0) s else s + "=".repeat(4 - remainder)
    }
}
