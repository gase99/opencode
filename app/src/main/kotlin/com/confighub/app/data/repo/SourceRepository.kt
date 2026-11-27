package com.confighub.app.data.repo

import com.confighub.app.core.model.ConfigProfile
import com.confighub.app.core.model.TlsMode
import com.confighub.app.core.parse.ConfigLinkParser
import com.confighub.app.core.parse.ConfigValidator
import com.confighub.app.core.parse.DedupHasher
import com.confighub.app.core.parse.SubscriptionDecoder
import com.confighub.app.core.security.SecretMasker
import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.db.FetchStatus
import com.confighub.app.data.db.SourceEntity
import com.confighub.app.data.db.SourceType
import com.confighub.app.data.net.FetchPolicy
import com.confighub.app.data.net.RateLimiter
import com.confighub.app.data.net.SourceFetcher
import com.confighub.app.data.security.FieldCipher
import com.confighub.app.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source Registry repository: CRUD for public sources plus the fetch → decode →
 * parse → validate → dedup → encrypt → store pipeline.
 */
@Singleton
class SourceRepository @Inject constructor(
    private val db: AppDatabase,
    private val fetcher: SourceFetcher,
    private val fieldCipher: FieldCipher,
    private val rateLimiter: RateLimiter,
    private val fetchPolicy: FetchPolicy,
) {

    fun observeSources(): Flow<List<SourceEntity>> = db.sourceDao().observeAll()

    suspend fun addSource(
        name: String,
        url: String,
        type: SourceType,
        licenseNote: String?,
        refreshIntervalHours: Int,
        urlPolicy: UrlPolicy,
    ): Result<SourceEntity> {
        val cleanUrl = url.trim()
        if (name.isBlank()) return Result.failure(IllegalArgumentException("source name is empty"))
        when (val verdict = urlPolicy.check(cleanUrl)) {
            is com.confighub.app.core.security.SafeUrlValidator.Result.Rejected ->
                return Result.failure(IllegalArgumentException(verdict.reason))
            com.confighub.app.core.security.SafeUrlValidator.Result.Ok -> Unit
        }
        val entity = SourceEntity(
            name = name.trim(),
            url = cleanUrl,
            type = type,
            licenseNote = licenseNote?.trim()?.takeIf { it.isNotEmpty() },
            refreshIntervalHours = refreshIntervalHours.coerceIn(1, 168),
            createdAt = now(),
        )
        val id = db.sourceDao().insert(entity)
        return Result.success(entity.copy(id = id))
    }

    suspend fun deleteSource(id: Long) {
        db.configDao().deleteBySource(id)
        db.sourceDao().delete(id)
    }

    suspend fun setSourceEnabled(id: Long, enabled: Boolean) {
        db.sourceDao().setEnabled(id, enabled)
    }

    /** Refreshes every enabled source that is due. Never throws. */
    suspend fun refreshAll(settings: AppSettings, urlPolicy: UrlPolicy, force: Boolean = false) {
        for (source in db.sourceDao().getAllOnce()) {
            if (!source.enabled) continue
            if (!force && !isDue(source)) continue
            runCatching { refreshSource(source, settings, urlPolicy) }
        }
    }

    suspend fun isDue(source: SourceEntity): Boolean {
        val last = source.lastFetchAt ?: return true
        return now() - last >= source.refreshIntervalHours * 3_600_000L
    }

    /** Fetches and imports a single source. Returns the updated entity. */
    suspend fun refreshSource(
        source: SourceEntity,
        settings: AppSettings,
        urlPolicy: UrlPolicy,
    ): SourceEntity {
        val updated: SourceEntity = when (val verdict = urlPolicy.check(source.url)) {
            is com.confighub.app.core.security.SafeUrlValidator.Result.Rejected -> source.copy(
                lastStatus = FetchStatus.SKIPPED,
                lastError = "URL policy: ${verdict.reason}",
                lastFetchAt = now(),
            )

            com.confighub.app.core.security.SafeUrlValidator.Result.Ok -> {
                val host = runCatching { URI(source.url).host }.getOrNull()?.lowercase() ?: source.url
                rateLimiter.withPermit(host, fetchPolicy.minHostIntervalMs) {
                    applyFetch(source, settings)
                }
            }
        }
        db.sourceDao().update(updated)
        return updated
    }

    private suspend fun applyFetch(source: SourceEntity, settings: AppSettings): SourceEntity {
        return when (val outcome = fetcher.fetch(source.url, source.etag, source.lastModified)) {
            is SourceFetcher.Outcome.Success -> importPayload(source, outcome, settings)

            SourceFetcher.Outcome.NotModified -> source.copy(
                lastStatus = FetchStatus.NOT_MODIFIED,
                lastFetchAt = now(),
                lastError = null,
            )

            is SourceFetcher.Outcome.HttpError -> source.copy(
                lastStatus = FetchStatus.ERROR_HTTP,
                lastHttpCode = outcome.code,
                lastError = buildString {
                    append("HTTP ").append(outcome.code)
                    outcome.retryAfterSeconds?.let { append(" (retry after ").append(it).append("s)") }
                },
                lastFetchAt = now(),
            )

            is SourceFetcher.Outcome.Failure -> source.copy(
                lastStatus = FetchStatus.ERROR_NETWORK,
                lastError = SecretMasker.sanitize(outcome.message),
                lastFetchAt = now(),
            )
        }
    }

    private suspend fun importPayload(
        source: SourceEntity,
        outcome: SourceFetcher.Outcome.Success,
        settings: AppSettings,
    ): SourceEntity {
        val validator = ConfigValidator(settings.allowExperimentalProtocols)
        val parseOptions = ConfigLinkParser.Options(allowExperimental = settings.allowExperimentalProtocols)
        val nowMs = now()
        val seenHashes = LinkedHashSet<String>()
        val newEntities = mutableListOf<ConfigEntity>()

        for (candidate in SubscriptionDecoder.decode(outcome.body)) {
            if (seenHashes.size >= settings.maxConfigsPerSource) break
            val profile = ConfigLinkParser.parse(candidate, parseOptions) ?: continue
            if (validator.validate(profile).isNotEmpty()) continue
            val hash = DedupHasher.hash(profile)
            if (!seenHashes.add(hash)) continue
            newEntities += profile.toEntity(hash, source.id, nowMs, fieldCipher)
        }

        val existingHashes = db.configDao().getByHashes(seenHashes.toList()).map { it.hash }.toSet()
        db.configDao().insertAll(newEntities.filterNot { it.hash in existingHashes })
        if (seenHashes.isNotEmpty()) db.configDao().touch(seenHashes.toList(), nowMs)

        return source.copy(
            etag = outcome.etag,
            lastModified = outcome.lastModified,
            lastFetchAt = nowMs,
            lastStatus = FetchStatus.OK,
            lastHttpCode = outcome.httpCode,
            lastError = null,
            lastItemCount = seenHashes.size,
        )
    }

    private fun now(): Long = System.currentTimeMillis()
}

internal fun ConfigProfile.toEntity(
    hash: String,
    sourceId: Long,
    seenAt: Long,
    fieldCipher: FieldCipher,
): ConfigEntity {
    val secretEncrypted = fieldCipher.encrypt(secret.toByteArray(Charsets.UTF_8))
    val secondaryEncrypted = secondarySecret?.let {
        fieldCipher.encrypt(it.toByteArray(Charsets.UTF_8))
    }
    return ConfigEntity(
        hash = hash,
        protocol = protocol,
        host = host,
        port = port,
        network = network,
        tls = tls,
        sni = sni,
        method = method,
        name = name,
        sourceId = sourceId,
        firstSeenAt = seenAt,
        lastSeenAt = seenAt,
        cipher = cipher,
        alterId = alterId,
        flow = flow,
        publicKey = publicKey,
        shortId = shortId,
        wsPath = wsPath,
        wsHost = wsHost,
        grpcServiceName = grpcServiceName,
        alpnText = alpn?.joinToString(","),
        fingerprint = fingerprint,
        allowInsecure = allowInsecure,
        plugin = plugin,
        secretIv = secretEncrypted.iv,
        secretEnc = secretEncrypted.data,
        secondarySecretIv = secondaryEncrypted?.iv,
        secondarySecretEnc = secondaryEncrypted?.data,
    )
}

/** Rebuilds the connect-time [ConfigProfile] from the stored (encrypted) entity. */
fun ConfigEntity.toConfigProfile(fieldCipher: FieldCipher): ConfigProfile? {
    val secretPlain = runCatching {
        String(fieldCipher.decrypt(secretIv, secretEnc), Charsets.UTF_8)
    }.getOrNull() ?: return null
    val secondaryPlain = secondarySecretEnc?.let { encrypted ->
        secondarySecretIv?.let { iv ->
            runCatching { String(fieldCipher.decrypt(iv, encrypted), Charsets.UTF_8) }.getOrNull()
        }
    }
    return ConfigProfile(
        protocol = protocol,
        host = host,
        port = port,
        secret = secretPlain,
        secretKind = when (protocol) {
            com.confighub.app.core.model.Protocol.VMESS,
            com.confighub.app.core.model.Protocol.VLESS,
            com.confighub.app.core.model.Protocol.TUIC,
            -> com.confighub.app.core.model.SecretKind.UUID

            else -> com.confighub.app.core.model.SecretKind.PASSWORD
        },
        secondarySecret = secondaryPlain,
        method = method,
        cipher = cipher,
        alterId = alterId,
        network = network,
        tls = tls,
        sni = sni,
        alpn = alpnText?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
        fingerprint = fingerprint,
        wsPath = wsPath,
        wsHost = wsHost,
        grpcServiceName = grpcServiceName,
        flow = flow,
        publicKey = publicKey,
        shortId = shortId,
        allowInsecure = allowInsecure,
        plugin = plugin,
        name = name,
    )
}
