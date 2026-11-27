package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile

/**
 * Top-level parsing entry point: dispatches candidates to protocol-specific parsers.
 *
 * Experimental protocols (Hysteria2/TUIC) are only returned when
 * [Options.allowExperimental] is enabled by the user in Settings.
 */
object ConfigLinkParser {

    data class Options(val allowExperimental: Boolean = false)

    private val uriParsers: Map<String, LinkParser> = mapOf(
        "vmess" to VmessLinkParser,
        "vless" to VlessLinkParser,
        "trojan" to TrojanLinkParser,
        "ss" to ShadowsocksLinkParser,
        "hysteria2" to Hysteria2LinkParser,
        "hy2" to Hysteria2LinkParser,
        "tuic" to TuicLinkParser,
    )

    fun parse(candidate: String, options: Options = Options()): ConfigProfile? {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return null

        val profile = if (trimmed.startsWith("{")) {
            V2rayJsonOutboundParser.parse(trimmed)
        } else {
            val scheme = trimmed.substringBefore("://", "").lowercase()
            uriParsers[scheme]?.parse(trimmed)
        } ?: return null

        if (profile.protocol.isExperimental && !options.allowExperimental) return null
        return profile
    }

    fun parseAll(candidates: List<String>, options: Options = Options()): List<ConfigProfile> =
        candidates.mapNotNull { parse(it, options) }
}
