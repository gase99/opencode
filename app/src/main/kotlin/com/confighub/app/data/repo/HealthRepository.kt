package com.confighub.app.data.repo

import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.db.HealthSampleEntity
import com.confighub.app.data.db.toConfigProfile
import com.confighub.app.data.security.FieldCipher
import com.confighub.app.data.settings.AppSettings
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.domain.health.HealthChecker
import com.confighub.app.domain.health.ProbeResult
import com.confighub.app.domain.operator.OperatorCatalog
import com.confighub.app.domain.operator.OperatorDetector
import com.confighub.app.domain.operator.OperatorId
import com.confighub.app.domain.scoring.ScoreEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs health checks (probe → persist sample → recompute score) and exposes history.
 *
 * Every result is tied to the network it was MEASURED on (network type + operator
 * PLMN); the app never labels a measurement as belonging to another operator.
 */
@Singleton
class HealthRepository @Inject constructor(
    private val db: AppDatabase,
    private val healthChecker: HealthChecker,
    private val fieldCipher: FieldCipher,
    private val operatorDetector: OperatorDetector,
    private val settingsRepository: SettingsRepository,
) {

    fun observeLatestSamples(): Flow<List<HealthSampleEntity>> = db.healthDao().observeLatestPerConfig()

    fun observeHistory(configHash: String): Flow<List<HealthSampleEntity>> =
        db.healthDao().observeForConfig(configHash, HISTORY_LIMIT)

    /** Runs one probe for [configHash] and stores the sample + refreshed score. */
    suspend fun testNow(configHash: String): ProbeResult? {
        val entity = db.configDao().getByHash(configHash) ?: return null
        val profile = entity.toConfigProfile(fieldCipher) ?: return null
        val settings = settingsRepository.settingsOnce()
        val detection = operatorDetector.detect(settings.manualOperatorId)

        val result = healthChecker.probe(profile)
        val nowMs = now()
        db.healthDao().insert(
            HealthSampleEntity(
                configHash = configHash,
                testedAt = nowMs,
                ok = result.ok,
                dnsMs = result.dnsMs,
                tcpMeanMs = result.tcpMeanMs,
                tcpMinMs = result.tcpMinMs,
                jitterMs = result.jitterMs,
                tlsMs = result.tlsMs,
                errorKind = result.error?.name,
                networkType = detection.networkType.name,
                operatorCode = detection.plmn,
            ),
        )
        val score = scoreFor(entity, settings, detection.operatorId)
        db.configDao().updateScore(configHash, score.total.toFloat(), nowMs, nowMs)
        return result
    }

    /** Score computation from stored history + current weights (+ operator overrides). */
    suspend fun scoreFor(
        entity: ConfigEntity,
        settings: AppSettings,
        operatorId: OperatorId,
    ): ScoreEngine.Result {
        val samples = db.healthDao().forConfig(entity.hash, HISTORY_WINDOW)
        val last = samples.firstOrNull()
        val successRate = if (samples.isEmpty()) {
            null
        } else {
            samples.count { it.ok }.toFloat() / samples.size
        }
        val lastSuccessAt = samples.firstOrNull { it.ok }?.testedAt
        val hoursSinceLastSuccess = lastSuccessAt?.let { (now() - it) / 3_600_000.0 }

        val operatorProfile = OperatorCatalog.profile(operatorId)
        val weights = operatorProfile.weightsOverride ?: ScoreEngine.Weights(
            latency = settings.latencyWeight,
            jitter = settings.jitterWeight,
            failure = settings.failureWeight,
            recency = settings.recencyWeight,
            protocol = settings.protocolWeight,
        )
        return ScoreEngine.score(
            input = ScoreEngine.Input(
                tcpMeanMs = last?.tcpMeanMs,
                jitterMs = last?.jitterMs,
                lastProbeOk = last?.ok,
                successRate = successRate,
                hoursSinceLastSuccess = hoursSinceLastSuccess,
                protocol = entity.protocol,
            ),
            weights = weights,
        )
    }

    /** Removes health samples older than [keepDays]. */
    suspend fun prune(keepDays: Int) {
        db.healthDao().pruneOlderThan(now() - keepDays.toLong() * 24 * 3_600_000L)
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val HISTORY_WINDOW = 10
        const val HISTORY_LIMIT = 50
    }
}
