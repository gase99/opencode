package com.confighub.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a source entry points to. */
enum class SourceType {
    GITHUB_RAW,
    SUBSCRIPTION_URL,
    JSON_API,
    CUSTOM,
}

/** Outcome of the last fetch attempt of a source. */
enum class FetchStatus {
    NEVER,
    OK,
    NOT_MODIFIED,
    SKIPPED,
    ERROR_HTTP,
    ERROR_NETWORK,
}

/** A user-configured public source in the Source Registry. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val type: SourceType,
    val enabled: Boolean = true,
    /** License / terms note the user attaches to this source (Source Policy). */
    val licenseNote: String? = null,
    val refreshIntervalHours: Int = 6,
    // Conditional request state
    val etag: String? = null,
    val lastModified: String? = null,
    // Last fetch bookkeeping
    val lastFetchAt: Long? = null,
    val lastStatus: FetchStatus = FetchStatus.NEVER,
    val lastHttpCode: Int? = null,
    val lastError: String? = null,
    val lastItemCount: Int = 0,
    val createdAt: Long,
)

/**
 * A validated config cached locally.
 *
 * SECURITY: [secretEnc]/[secretIv] (and secondary) hold credential material encrypted
 * with an Android-Keystore-wrapped AES-GCM key. [hash] is the dedup identity — it
 * contains only a SHA-256 of the secret, never the secret itself.
 */
@Entity(
    tableName = "configs",
    indices = [Index("sourceId"), Index("protocol"), Index("isFavorite"), Index("lastSeenAt")],
)
data class ConfigEntity(
    @PrimaryKey val hash: String,
    val protocol: com.confighub.app.core.model.Protocol,
    val host: String,
    val port: Int,
    val network: com.confighub.app.core.model.Transport,
    val tls: com.confighub.app.core.model.TlsMode,
    val sni: String? = null,
    val method: String? = null,
    val name: String? = null,
    val sourceId: Long,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val isFavorite: Boolean = false,
    val enabled: Boolean = true,
    // Connection-time parameters (not secrets)
    val cipher: String? = null,
    val alterId: Int? = null,
    val flow: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val wsPath: String? = null,
    val wsHost: String? = null,
    val grpcServiceName: String? = null,
    val alpnText: String? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false,
    val plugin: String? = null,
    // Encrypted credentials (iv == null means "Keystore unavailable" plaintext fallback)
    val secretIv: ByteArray?,
    val secretEnc: ByteArray,
    val secondarySecretIv: ByteArray? = null,
    val secondarySecretEnc: ByteArray? = null,
    // Scoring cache
    val lastScore: Float? = null,
    val lastScoredAt: Long? = null,
    val lastTestAt: Long? = null,
)

/** One health-check result for a config. */
@Entity(
    tableName = "health_samples",
    indices = [Index("configHash"), Index("testedAt")],
)
data class HealthSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val configHash: String,
    val testedAt: Long,
    val ok: Boolean,
    val dnsMs: Long? = null,
    val tcpMeanMs: Long? = null,
    val tcpMinMs: Long? = null,
    val jitterMs: Long? = null,
    val tlsMs: Long? = null,
    val errorKind: String? = null,
    /** WIFI / MOBILE / UNKNOWN — where the measurement was taken. */
    val networkType: String,
    /** PLMN code (e.g. 43211) of the network the measurement was taken on. */
    val operatorCode: String?,
)
