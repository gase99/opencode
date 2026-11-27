package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.SecretKind
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.model.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser for a single V2Ray/Xray JSON **outbound** object (JSON subscription format).
 *
 * The [SubscriptionDecoder] splits a JSON payload with an `outbounds` array into
 * individual JSON strings; this parser turns one outbound into a [ConfigProfile].
 * Supported outbound protocols: vmess, vless, trojan, shadowsocks.
 */
object V2rayJsonOutboundParser : LinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    override fun parse(candidate: String): ConfigProfile? {
        if (!candidate.trim().startsWith("{")) return null
        val obj = runCatching { json.parseToJsonElement(candidate.trim()).jsonObject }.getOrNull() ?: return null
        val protocol = obj["protocol"]?.jsonPrimitive?.content?.lowercase() ?: return null
        val settings = obj["settings"] as? JsonObject ?: return null
        val stream = obj["streamSettings"] as? JsonObject
        val tag = obj["tag"]?.jsonPrimitive?.content

        return when (protocol) {
            "vmess", "vless" -> parseVnext(protocol, settings, stream, tag)
            "trojan" -> parseTrojan(settings, stream, tag)
            "shadowsocks" -> parseShadowsocks(settings, tag)
            else -> null
        }
    }

    private fun parseVnext(
        protocol: String,
        settings: JsonObject,
        stream: JsonObject?,
        tag: String?,
    ): ConfigProfile? {
        val server = (settings["vnext"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val address = server["address"]?.jsonPrimitive?.content ?: return null
        val port = server["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val user = (server["users"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val id = user["id"]?.jsonPrimitive?.content ?: return null

        return ConfigProfile(
            protocol = if (protocol == "vmess") Protocol.VMESS else Protocol.VLESS,
            host = address.trim(),
            port = port,
            secret = id.trim(),
            secretKind = SecretKind.UUID,
            alterId = user["alterId"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            cipher = user["security"]?.jsonPrimitive?.content ?: "auto",
            flow = user["flow"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            network = Transport.fromWire(stream?.get("network")?.jsonPrimitive?.content),
            tls = parseTls(stream),
            sni = stream?.tlsOrRealitySetting()?.let { it.optString("serverName") },
            alpn = stream?.tlsOrRealitySetting()?.let { setting ->
                (setting["alpn"] as? JsonArray)?.mapNotNull { element ->
                    element.toString().trim('"').takeIf { it.isNotBlank() }
                }
            },
            fingerprint = stream?.tlsOrRealitySetting()?.let { it.optString("fingerprint") },
            publicKey = (stream?.get("realitySettings") as? JsonObject)?.optString("publicKey"),
            shortId = (stream?.get("realitySettings") as? JsonObject)?.optString("shortId"),
            name = tag,
        )
    }

    private fun parseTrojan(settings: JsonObject, stream: JsonObject?, tag: String?): ConfigProfile? {
        val server = (settings["servers"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val address = server["address"]?.jsonPrimitive?.content ?: return null
        val port = server["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val password = server["password"]?.jsonPrimitive?.content ?: return null

        return ConfigProfile(
            protocol = Protocol.TROJAN,
            host = address.trim(),
            port = port,
            secret = password.trim(),
            secretKind = SecretKind.PASSWORD,
            network = Transport.fromWire(stream?.get("network")?.jsonPrimitive?.content),
            tls = parseTls(stream).takeIf { it != TlsMode.NONE } ?: TlsMode.TLS,
            sni = stream?.tlsOrRealitySetting()?.let { it.optString("serverName") },
            name = tag,
        )
    }

    private fun parseShadowsocks(settings: JsonObject, tag: String?): ConfigProfile? {
        val server = (settings["servers"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val address = server["address"]?.jsonPrimitive?.content ?: return null
        val port = server["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val password = server["password"]?.jsonPrimitive?.content ?: return null
        val method = server["method"]?.jsonPrimitive?.content ?: return null

        return ConfigProfile(
            protocol = Protocol.SHADOWSOCKS,
            host = address.trim(),
            port = port,
            secret = password.trim(),
            secretKind = SecretKind.PASSWORD,
            method = method.trim(),
            name = tag,
        )
    }

    private fun parseTls(stream: JsonObject?): TlsMode {
        val security = stream?.get("security")?.jsonPrimitive?.content?.lowercase()
        return when (security) {
            "tls" -> TlsMode.TLS
            "reality" -> TlsMode.REALITY
            else -> TlsMode.NONE
        }
    }

    private fun JsonObject.tlsOrRealitySetting(): JsonObject? =
        (this["tlsSettings"] as? JsonObject) ?: (this["realitySettings"] as? JsonObject)
}
