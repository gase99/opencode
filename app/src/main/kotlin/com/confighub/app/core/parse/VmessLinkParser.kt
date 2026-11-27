package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Parser for `vmess://BASE64(JSON)` share links (V2RayN style).
 *
 * The JSON payload uses short keys (add/port/id/aid/scy/net/type/host/path/tls/sni/
 * alpn/fp/ps); port and aid are frequently encoded as strings, so both are accepted.
 */
object VmessLinkParser : LinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    override fun parse(candidate: String): ConfigProfile? {
        if (!candidate.lowercase().startsWith("vmess://")) return null
        val decoded = Base64Compat.decode(candidate.removePrefix("vmess://").trim()) ?: return null
        val obj = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null

        val host = obj.optString("add")?.trim() ?: return null
        val port = obj.optInt("port") ?: return null
        val id = obj.optString("id")?.trim() ?: return null

        val tlsWire = obj.optString("tls")?.lowercase()
        val tls = when (tlsWire) {
            "tls" -> TlsMode.TLS
            "reality" -> TlsMode.REALITY
            else -> TlsMode.NONE
        }

        return ConfigProfile(
            protocol = Protocol.VMESS,
            host = host,
            port = port,
            secret = id,
            secretKind = SecretKind.UUID,
            alterId = obj.optInt("aid") ?: 0,
            cipher = obj.optString("scy") ?: "auto",
            network = Transport.fromWire(obj.optString("net")),
            tls = tls,
            sni = obj.optString("sni"),
            alpn = obj.optString("alpn")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            fingerprint = obj.optString("fp"),
            wsPath = obj.optString("path"),
            wsHost = obj.optString("host"),
            name = obj.optString("ps"),
        )
    }
}

/** Small extraction helpers shared by JSON-based parsers. */
internal fun JsonObject.optString(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.content.takeIf { it.isNotBlank() }
}

internal fun JsonObject.optInt(key: String): Int? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    return value.content.trim().toIntOrNull()
}
