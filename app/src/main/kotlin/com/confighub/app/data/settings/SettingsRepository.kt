package com.confighub.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "confighub_settings")

/** Snapshot of user-adjustable settings. Defaults are privacy-respecting. */
data class AppSettings(
    val refreshIntervalHours: Int = 6,
    val healthCheckIntervalHours: Int = 12,
    /** Hysteria2/TUIC gate — experimental protocols are OFF by default. */
    val allowExperimentalProtocols: Boolean = false,
    /** Plain-http sources gate — OFF by default. */
    val allowInsecureHttpSources: Boolean = false,
    // Score weights (normalized at scoring time)
    val latencyWeight: Float = 0.45f,
    val jitterWeight: Float = 0.15f,
    val failureWeight: Float = 0.25f,
    val recencyWeight: Float = 0.15f,
    val protocolWeight: Float = 0.0f,
    val maxConfigsPerSource: Int = 500,
    val keepHistoryDays: Int = 30,
    /** Operator profile chosen manually when auto-detection is unavailable. */
    val manualOperatorId: String? = null,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val REFRESH_INTERVAL = intPreferencesKey("refresh_interval_hours")
        val HEALTH_INTERVAL = intPreferencesKey("health_interval_hours")
        val ALLOW_EXPERIMENTAL = booleanPreferencesKey("allow_experimental_protocols")
        val ALLOW_INSECURE_HTTP = booleanPreferencesKey("allow_insecure_http_sources")
        val LATENCY_WEIGHT = stringPreferencesKey("latency_weight")
        val JITTER_WEIGHT = stringPreferencesKey("jitter_weight")
        val FAILURE_WEIGHT = stringPreferencesKey("failure_weight")
        val RECENCY_WEIGHT = stringPreferencesKey("recency_weight")
        val PROTOCOL_WEIGHT = stringPreferencesKey("protocol_weight")
        val MAX_CONFIGS = intPreferencesKey("max_configs_per_source")
        val KEEP_HISTORY_DAYS = intPreferencesKey("keep_history_days")
        val MANUAL_OPERATOR = stringPreferencesKey("manual_operator_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            refreshIntervalHours = prefs[Keys.REFRESH_INTERVAL] ?: 6,
            healthCheckIntervalHours = prefs[Keys.HEALTH_INTERVAL] ?: 12,
            allowExperimentalProtocols = prefs[Keys.ALLOW_EXPERIMENTAL] ?: false,
            allowInsecureHttpSources = prefs[Keys.ALLOW_INSECURE_HTTP] ?: false,
            latencyWeight = prefs[Keys.LATENCY_WEIGHT]?.toFloatOrNull() ?: 0.45f,
            jitterWeight = prefs[Keys.JITTER_WEIGHT]?.toFloatOrNull() ?: 0.15f,
            failureWeight = prefs[Keys.FAILURE_WEIGHT]?.toFloatOrNull() ?: 0.25f,
            recencyWeight = prefs[Keys.RECENCY_WEIGHT]?.toFloatOrNull() ?: 0.15f,
            protocolWeight = prefs[Keys.PROTOCOL_WEIGHT]?.toFloatOrNull() ?: 0.0f,
            maxConfigsPerSource = prefs[Keys.MAX_CONFIGS] ?: 500,
            keepHistoryDays = prefs[Keys.KEEP_HISTORY_DAYS] ?: 30,
            manualOperatorId = prefs[Keys.MANUAL_OPERATOR],
        )
    }

    suspend fun settingsOnce(): AppSettings = settings.first()

    suspend fun setRefreshIntervalHours(value: Int) = edit { it[Keys.REFRESH_INTERVAL] = value.coerceIn(1, 168) }

    suspend fun setHealthCheckIntervalHours(value: Int) = edit { it[Keys.HEALTH_INTERVAL] = value.coerceIn(1, 168) }

    suspend fun setAllowExperimentalProtocols(value: Boolean) = edit { it[Keys.ALLOW_EXPERIMENTAL] = value }

    suspend fun setAllowInsecureHttpSources(value: Boolean) = edit { it[Keys.ALLOW_INSECURE_HTTP] = value }

    suspend fun setWeights(latency: Float, jitter: Float, failure: Float, recency: Float) = edit {
        it[Keys.LATENCY_WEIGHT] = latency.toString()
        it[Keys.JITTER_WEIGHT] = jitter.toString()
        it[Keys.FAILURE_WEIGHT] = failure.toString()
        it[Keys.RECENCY_WEIGHT] = recency.toString()
    }

    suspend fun setLatencyWeight(value: Float) = edit { it[Keys.LATENCY_WEIGHT] = value.toString() }

    suspend fun setJitterWeight(value: Float) = edit { it[Keys.JITTER_WEIGHT] = value.toString() }

    suspend fun setFailureWeight(value: Float) = edit { it[Keys.FAILURE_WEIGHT] = value.toString() }

    suspend fun setRecencyWeight(value: Float) = edit { it[Keys.RECENCY_WEIGHT] = value.toString() }

    suspend fun setProtocolWeight(value: Float) = edit { it[Keys.PROTOCOL_WEIGHT] = value.toString() }

    suspend fun setMaxConfigsPerSource(value: Int) = edit { it[Keys.MAX_CONFIGS] = value.coerceIn(50, 5000) }

    suspend fun setKeepHistoryDays(value: Int) = edit { it[Keys.KEEP_HISTORY_DAYS] = value.coerceIn(1, 365) }

    suspend fun setManualOperatorId(value: String?) = edit {
        if (value == null) it.remove(Keys.MANUAL_OPERATOR) else it[Keys.MANUAL_OPERATOR] = value
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
