package com.confighub.app.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confighub.app.data.db.SourceEntity
import com.confighub.app.data.db.SourceType
import com.confighub.app.data.repo.SourceRepository
import com.confighub.app.data.repo.UrlPolicy
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.data.sources.SampleSources
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SourcesUiState(
    val sources: List<SourceEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val addError: String? = null,
    val importing: Boolean = false,
)

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val showAddDialog = MutableStateFlow(false)
    private val addError = MutableStateFlow<String?>(null)
    private val importing = MutableStateFlow(false)

    val uiState: StateFlow<SourcesUiState> = combine(
        sourceRepository.observeSources(),
        showAddDialog,
        addError,
        importing,
    ) { sources, dialog, error, importingNow ->
        SourcesUiState(
            sources = sources,
            showAddDialog = dialog,
            addError = error,
            importing = importingNow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesUiState())

    fun openAddDialog() {
        addError.value = null
        showAddDialog.value = true
    }

    fun dismissAddDialog() {
        showAddDialog.value = false
        addError.value = null
    }

    fun addSource(name: String, url: String, type: SourceType, licenseNote: String, intervalHours: Int) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsOnce()
            val result = sourceRepository.addSource(
                name = name,
                url = url,
                type = type,
                licenseNote = licenseNote,
                refreshIntervalHours = intervalHours,
                urlPolicy = UrlPolicy.fromSettings(settings.allowInsecureHttpSources),
            )
            result.fold(
                onSuccess = {
                    showAddDialog.value = false
                    addError.value = null
                },
                onFailure = { failure ->
                    addError.value = failure.message ?: "invalid source"
                },
            )
        }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch { sourceRepository.deleteSource(id) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { sourceRepository.setEnabled(id, enabled) }
    }

    fun refreshNow(source: SourceEntity) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsOnce()
            runCatching {
                sourceRepository.refreshSource(
                    source = source,
                    settings = settings,
                    urlPolicy = UrlPolicy.fromSettings(settings.allowInsecureHttpSources),
                )
            }
        }
    }

    fun importSamples() {
        viewModelScope.launch {
            importing.value = true
            try {
                for (sample in SampleSources.all) {
                    val existing = uiState.value.sources.any { it.url == sample.url }
                    if (existing) continue
                    val result = sourceRepository.addSource(
                        name = sample.name,
                        url = sample.url,
                        type = sample.type,
                        licenseNote = sample.licenseNote,
                        refreshIntervalHours = 12,
                        urlPolicy = UrlPolicy { com.confighub.app.core.security.SafeUrlValidator.Result.Ok },
                    )
                    result.getOrNull()?.let { created ->
                        // Samples stay DISABLED until the user replaces the URL & enables them.
                        sourceRepository.setEnabled(created.id, enabled = false)
                    }
                }
            } finally {
                importing.value = false
            }
        }
    }
}
