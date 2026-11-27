package com.confighub.app.core.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Turns a raw subscription payload into a list of *candidate* strings:
 * URI lines (`vmess://…`) or JSON outbound objects (`{"protocol": …}`).
 *
 * Supported payload shapes:
 * * plain text with one URI per line
 * * Base64 blob (standard or URL-safe, optionally double-encoded)
 * * JSON object with an `outbounds` array (V2Ray/Xray style)
 * * JSON array of URI strings or of outbound objects
 *
 * Junk lines and empty lines are dropped; the caller parses each candidate and
 * ignores the ones that fail.
 */
object SubscriptionDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: String): List<String> {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return decodeJson(trimmed)
        }
        if (trimmed.contains("://")) {
            return splitLines(trimmed)
        }
        val decoded = Base64Compat.decodeMulti(trimmed)
        if (decoded != null && decoded.contains("://")) {
            return splitLines(decoded)
        }
        // Last resort: treat as plain text (may be a list of URIs with odd spacing).
        return splitLines(trimmed)
    }

    private fun decodeJson(text: String): List<String> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return splitLines(text)
        return when (root) {
            is JsonObject -> {
                val outbounds = root["outbounds"] as? JsonArray
                if (outbounds != null) {
                    outbounds.map { it.toString() }
                } else {
                    listOf(root.toString())
                }
            }

            is JsonArray -> root.map { element ->
                when (element) {
                    is JsonPrimitive -> element.content
                    else -> element.toString()
                }
            }

            else -> splitLines(text)
        }
    }

    private fun splitLines(text: String): List<String> =
        text.split(Regex("\r?\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
