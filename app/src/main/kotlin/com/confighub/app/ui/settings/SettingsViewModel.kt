package com.confighub.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confighub.app.data.diagnostics.DiagnosticsExporter
import com.confighub.app.data.security.SecretCrypto
import com.confighub.app.data.settings.AppSettings
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.data.work.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val storageSecure: Boolean = true,
    val version: String = "",
    val exportedFile: File? = null,
    val exportFailed: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler,
    private val diagnosticsExporter: DiagnosticsExporter,
    secretCrypto: SecretCrypto,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val exportedFile = MutableStateFlow<File?>(null)
    private val exportFailed = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        exportedFile,
        exportFailed,
    ) { settings, file, failed ->
        SettingsUiState(
            settings = settings,
            storageSecure = secretCrypto.secure,
            version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            }.getOrDefault("?"),
            exportedFile = file,
            exportFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setRefreshInterval(hours: Int) {
        viewModelScope.launch {
            settingsRepository.setRefreshIntervalHours(hours)
            workScheduler.schedulePeriodicRefresh(hours)
        }
    }

    fun setHealthInterval(hours: Int) {
        viewModelScope.launch {
            settingsRepository.setHealthCheckIntervalHours(hours)
            workScheduler.schedulePeriodicHealthCheck(hours)
        }
    }

    fun setAllowExperimental(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowExperimentalProtocols(enabled) }
    }

    fun setAllowInsecureHttp(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowInsecureHttpSources(enabled) }
    }

    fun setWeight(kind: WeightKind, value: Float) {
        viewModelScope.launch {
            when (kind) {
                WeightKind.LATENCY -> settingsRepository.setLatencyWeight(value)
                WeightKind.JITTER -> settingsRepository.setJitterWeight(value)
                WeightKind.FAILURE -> settingsRepository.setFailureWeight(value)
                WeightKind.RECENCY -> settingsRepository.setRecencyWeight(value)
                WeightKind.PROTOCOL -> settingsRepository.setProtocolWeight(value)
            }
        }
    }

    fun setKeepHistory(days: Int) {
        viewModelScope.launch { settingsRepository.setKeepHistoryDays(days) }
    }

    fun setOperator(manualOperatorId: String?) {
        viewModelScope.launch { settingsRepository.setManualOperatorId(manualOperatorId) }
    }

    fun setMaxConfigs(count: Int) {
        viewModelScope.launch { settingsRepository.setMaxConfigsPerSource(count) }
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            try {
                exportedFile.value = diagnosticsExporter.exportToFile()
                exportFailed.value = false
            } catch (failure: Exception) {
                exportFailed.value = true
            }
        }
    }

    fun onExportConsumed() {
        exportedFile.value = null
    }

    enum class WeightKind { LATENCY, JITTER, FAILURE, RECENCY, PROTOCOL }
}
