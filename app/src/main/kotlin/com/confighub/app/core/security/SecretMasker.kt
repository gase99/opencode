package com.confighub.app.core.security

/**
 * Masks credential material in UI text and log lines.
 *
 * Used everywhere logs or diagnostics are produced; nothing in the app should ever
 * print raw UUIDs, passwords, keys or subscription URLs.
 */
object SecretMasker {

    /** Visible replacement for any secret. */
    const val MASK = "••••••••"

    private val UUID_RE =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** key=value / key:value patterns for credential-ish keys. */
    private val KV_RE =
        Regex("(?i)(password|passwd|pwd|uuid|auth|token|secret|key|method|id)\\s*[=:]\\s*[^\\s&;\"',}]+")

    /** user:password@host userinfo in URIs. */
    private val USERINFO_RE = Regex("//([^/@\\s:]+):([^@\\s]+)@")

    /** Replaces every occurrence of the given secrets with the mask. */
    fun redact(message: String, vararg secrets: String): String {
        var result = message
        for (secret in secrets) {
            if (secret.isNotBlank()) result = result.replace(secret, MASK)
        }
        return result
    }

    /**
     * Best-effort sanitization of arbitrary text (log lines, exception messages):
     * masks UUID shapes, `password=…`-style key/value pairs and URI userinfo.
     */
    fun sanitize(text: String): String = text
        .replace(UUID_RE, MASK)
        .replace(KV_RE) { match -> match.groupValues[1] + "=" + MASK }
        .replace(USERINFO_RE) { match -> "//" + match.groupValues[1] + ":" + MASK + "@" }
}
