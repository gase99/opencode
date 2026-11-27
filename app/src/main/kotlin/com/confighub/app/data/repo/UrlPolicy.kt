package com.confighub.app.data.repo

import com.confighub.app.core.security.SafeUrlValidator

/**
 * URL admission policy for sources, resolved from current settings.
 * Production implementation wraps [SafeUrlValidator]; tests may inject a permissive
 * policy (e.g. to talk to a local MockWebServer).
 */
fun interface UrlPolicy {
    fun check(url: String): SafeUrlValidator.Result

    companion object {
        fun fromSettings(allowInsecureHttp: Boolean): UrlPolicy = UrlPolicy { url ->
            SafeUrlValidator(allowInsecureHttp).validate(url)
        }
    }
}
