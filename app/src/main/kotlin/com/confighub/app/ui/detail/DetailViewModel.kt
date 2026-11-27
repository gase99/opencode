package com.confighub.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.db.HealthSampleEntity
import com.confighub.app.data.repo.ConfigRepository
import com.confighub.app.data.repo.HealthRepository
import com.confighub.app.data.repo.SourceRepository
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.domain.operator.OperatorDetector
import com.confighub.app.domain.scoring.ScoreEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val configRepository: ConfigRepository,
    private val sourceRepository: SourceRepository,
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    private val operatorDetector: OperatorDetector,
) : ViewModel() {

    val hash: String = checkNotNull(savedStateHandle["hash"])

    data class DetailUiState(
        val config: ConfigEntity? = null,
        val sourceName: String? = null,
        val history: List<HealthSampleEntity> = emptyList(),
        val score: ScoreEngine.Result? = null,
        val isTesting: Boolean = false,
    )

    private val isTesting = MutableStateFlow(false)

    val uiState: StateFlow<DetailUiState> = combine(
        configRepository.observeConfig(hash),
        sourceRepository.observeSources(),
        healthRepository.observeHistory(hash),
        isTesting,
    ) { config, sources, history, testing ->
        val sourceName = sources.firstOrNull { it.id == config?.sourceId }?.name
        val score = config?.let { entity ->
            val settings = settingsRepository.settingsOnce()
            val detection = operatorDetector.detect(settings.manualOperatorId)
            healthRepository.scoreFor(entity, settings, detection.operatorId)
        }
        DetailUiState(
            config = config,
            sourceName = sourceName,
            history = history,
            score = score,
            isTesting = testing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun testNow() {
        viewModelScope.launch {
            isTesting.value = true
            try {
                healthRepository.testNow(hash)
            } finally {
                isTesting.value = false
            }
        }
    }

    fun toggleFavorite(favorite: Boolean) {
        viewModelScope.launch { configRepository.toggleFavorite(hash, favorite) }
    }
}
