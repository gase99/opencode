package com.confighub.app.core.model

/**
 * Proxy protocols supported by ConfigHub.
 *
 * Experimental protocols are parsed for informational purposes but are hidden and
 * disabled unless the user explicitly enables them in Settings (default OFF).
 */
enum class Protocol(val wireName: String, val isExperimental: Boolean) {
    VMESS("vmess", isExperimental = false),
    VLESS("vless", isExperimental = false),
    TROJAN("trojan", isExperimental = false),
    SHADOWSOCKS("ss", isExperimental = false),
    HYSTERIA2("hysteria2", isExperimental = true),
    TUIC("tuic", isExperimental = true),
}

/** What kind of secret the [ConfigProfile.secret] field holds (used for masking). */
enum class SecretKind {
    UUID,
    PASSWORD,
}

/** Transport ("network") layer of the config. */
enum class Transport(val wireName: String) {
    TCP("tcp"),
    WS("ws"),
    GRPC("grpc"),
    HTTP_UPGRADE("httpupgrade"),
    HTTP2("h2"),
    KCP("kcp"),
    QUIC("quic"),
    XHTTP("xhttp");

    companion object {
        fun fromWire(value: String?): Transport = when (value?.trim()?.lowercase()) {
            "ws", "websocket" -> WS
            "grpc", "gun", "grpc-multi" -> GRPC
            "httpupgrade" -> HTTP_UPGRADE
            "h2", "http", "h2c" -> HTTP2
            "kcp", "mkcp" -> KCP
            "quic" -> QUIC
            "xhttp", "splithttp" -> XHTTP
            else -> TCP
        }
    }
}

/** TLS mode of the config. */
enum class TlsMode(val wireName: String) {
    NONE("none"),
    TLS("tls"),
    REALITY("reality");

    companion object {
        fun fromWire(value: String?): TlsMode = when (value?.trim()?.lowercase()) {
            "tls" -> TLS
            "reality" -> REALITY
            else -> NONE
        }
    }
}
