package com.confighub.app.data.diagnostics

import android.content.Context
import com.confighub.app.core.security.UrlRedactor
import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.security.SecretCrypto
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.domain.operator.OperatorDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a shareable diagnostics report with ZERO secrets:
 *
 * * config credentials never appear (they are not even read);
 * * source URLs pass through [UrlRedactor] (query/fragment/token segments removed);
 * * only aggregates + redacted metadata are included.
 */
@Singleton
class DiagnosticsExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val operatorDetector: OperatorDetector,
    private val secretCrypto: SecretCrypto,
) {

    suspend fun buildReport(nowMs: Long = System.currentTimeMillis()): String {
        val settings = settingsRepository.settingsOnce()
        val detection = operatorDetector.detect(settings.manualOperatorId)
        val sources = db.sourceDao().getAllOnce()
        val configs = db.configDao().observeAll().first()
        val latestSamples = db.healthDao().latestPerConfigOnce()
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

        return buildString {
            appendLine("ConfigHub diagnostics")
            appendLine("generated: ${Instant.ofEpochMilli(nowMs)}")
            appendLine("app version: $version")
            appendLine("android sdk: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("data-at-rest encryption: ${if (secretCrypto.secure) "ENABLED" else "DISABLED (fallback)"}")
            appendLine()
            appendLine("== Operator / network ==")
            appendLine("operator: ${detection.operatorId.displayName} (source: ${detection.source})")
            appendLine("network type: ${detection.networkType}")
            appendLine("plmn: ${detection.plmn ?: "-"}")
            appendLine()
            appendLine("== Sources (${sources.size}) ==")
            for (source in sources) {
                appendLine(
                    "- ${source.name} | type=${source.type} | enabled=${source.enabled} | " +
                        "status=${source.lastStatus} | http=${source.lastHttpCode ?: "-"} | " +
                        "items=${source.lastItemCount} | url=${UrlRedactor.redact(source.url)}",
                )
            }
            appendLine()
            appendLine("== Configs: ${configs.size} total ==")
            val byProtocol = configs.groupingBy { it.protocol.name }.eachCount()
            byProtocol.forEach { (protocol, count) -> appendLine("- $protocol: $count") }
            appendLine()
            appendLine("== Latest health samples (up to 20) ==")
            for (sample in latestSamples.take(20)) {
                appendLine(
                    "- ${sample.configHash.take(8)}… ok=${sample.ok} tcpMs=${sample.tcpMeanMs ?: "-"} " +
                        "jitterMs=${sample.jitterMs ?: "-"} tlsMs=${sample.tlsMs ?: "-"} " +
                        "error=${sample.errorKind ?: "-"} net=${sample.networkType} at=${sample.testedAt}",
                )
            }
            appendLine()
            appendLine("== Settings snapshot ==")
            appendLine("refreshIntervalHours=${settings.refreshIntervalHours}")
            appendLine("healthCheckIntervalHours=${settings.healthCheckIntervalHours}")
            appendLine("allowExperimentalProtocols=${settings.allowExperimentalProtocols}")
            appendLine("allowInsecureHttpSources=${settings.allowInsecureHttpSources}")
            appendLine("weights: latency=${settings.latencyWeight} jitter=${settings.jitterWeight} " +
                "failure=${settings.failureWeight} recency=${settings.recencyWeight} " +
                "protocol=${settings.protocolWeight}")
            appendLine("manualOperatorId=${settings.manualOperatorId ?: "-"}")
            appendLine()
            appendLine("NOTE: secrets (UUIDs/passwords/keys) are never included in this report.")
        }
    }

    suspend fun exportToFile(): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "confighub-diagnostics-${System.currentTimeMillis()}.txt")
        file.writeText(buildReport())
        return file
    }
}
