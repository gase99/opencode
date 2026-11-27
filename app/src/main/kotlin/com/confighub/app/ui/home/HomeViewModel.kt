package com.confighub.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.security.SecretMasker
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.db.HealthSampleEntity
import com.confighub.app.data.repo.ConfigRepository
import com.confighub.app.data.repo.HealthRepository
import com.confighub.app.data.repo.SourceRepository
import com.confighub.app.data.repo.UrlPolicy
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.domain.operator.OperatorCatalog
import com.confighub.app.domain.operator.OperatorDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode { SCORE, LATENCY, RECENT }

data class HomeFilters(
    val query: String = "",
    val protocol: Protocol? = null,
    val favoritesOnly: Boolean = false,
    val maxLatencyMs: Int? = null,
    val sourceId: Long? = null,
    val sort: SortMode = SortMode.SCORE,
)

data class HomeItem(
    val config: ConfigEntity,
    val latest: HealthSampleEntity?,
    val score: Int?,
    val sourceName: String?,
)

data class HomeUiState(
    val items: List<HomeItem> = emptyList(),
    val filters: HomeFilters = HomeFilters(),
    val isRefreshing: Boolean = false,
    val testingHash: String? = null,
    val message: String? = null,
    val goodBelowMs: Int = 150,
    val acceptableBelowMs: Int = 350,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val sourceRepository: SourceRepository,
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    private val operatorDetector: OperatorDetector,
) : ViewModel() {

    private val filters = MutableStateFlow(HomeFilters())
    private val isRefreshing = MutableStateFlow(false)
    private val testingHash = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        configRepository.observeConfigs(),
        healthRepository.observeLatestSamples(),
        sourceRepository.observeSources(),
    ) { configs, samples, sources ->
        Triple(configs, samples.associateBy { it.configHash }, sources.associate { it.id to it.name })
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dataFlow,
        filters,
        isRefreshing,
        testingHash,
        message,
    ) { data, currentFilters, refreshing, testing, errorMessage ->
        val (configs, samples, sourceNames) = data
        val settings = runCatching { settingsRepository.settingsOnce() }.getOrNull()
        val detection = settings?.let { operatorDetector.detect(it.manualOperatorId) }
        val profile = detection?.let { OperatorCatalog.profile(it.operatorId) }

        val query = currentFilters.query.trim().lowercase()
        val items = configs.asSequence()
            .filter { config -> currentFilters.protocol == null || config.protocol == currentFilters.protocol }
            .filter { config -> !currentFilters.favoritesOnly || config.isFavorite }
            .filter { config -> currentFilters.sourceId == null || config.sourceId == currentFilters.sourceId }
            .filter { config ->
                query.isEmpty() ||
                    config.name?.lowercase()?.contains(query) == true ||
                    config.host.lowercase().contains(query) ||
                    config.protocol.wireName.contains(query)
            }
            .filter { config ->
                currentFilters.maxLatencyMs == null ||
                    samples[config.hash]?.tcpMeanMs?.let { it <= currentFilters.maxLatencyMs } == true
            }
            .map { config ->
                HomeItem(
                    config = config,
                    latest = samples[config.hash],
                    score = config.lastScore?.toInt(),
                    sourceName = sourceNames[config.sourceId],
                )
            }
            .toList()
            .let { list ->
                when (currentFilters.sort) {
                    SortMode.SCORE -> list.sortedByDescending { it.score ?: -1 }
                    SortMode.LATENCY -> list.sortedBy { it.latest?.tcpMeanMs ?: Long.MAX_VALUE }
                    SortMode.RECENT -> list.sortedByDescending { it.config.lastSeenAt }
                }
            }

        HomeUiState(
            items = items,
            filters = currentFilters,
            isRefreshing = refreshing,
            testingHash = testing,
            message = errorMessage,
            goodBelowMs = profile?.thresholds?.goodBelowMs ?: 150,
            acceptableBelowMs = profile?.thresholds?.acceptableBelowMs ?: 350,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                val settings = settingsRepository.settingsOnce()
                sourceRepository.refreshAll(
                    settings = settings,
                    urlPolicy = UrlPolicy.fromSettings(settings.allowInsecureHttpSources),
                    force = true,
                )
                message.value = null
            } catch (failure: Exception) {
                message.value = SecretMasker.sanitize(
                    failure.message ?: failure.javaClass.simpleName,
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun testNow(hash: String) {
        viewModelScope.launch {
            testingHash.value = hash
            try {
                healthRepository.testNow(hash)
            } finally {
                testingHash.value = null
            }
        }
    }

    fun toggleFavorite(hash: String, favorite: Boolean) {
        viewModelScope.launch { configRepository.toggleFavorite(hash, favorite) }
    }

    fun setQuery(value: String) {
        filters.value = filters.value.copy(query = value)
    }

    fun setProtocol(protocol: Protocol?) {
        filters.value = filters.value.copy(protocol = protocol)
    }

    fun setFavoritesOnly(enabled: Boolean) {
        filters.value = filters.value.copy(favoritesOnly = enabled)
    }

    fun setMaxLatency(maxLatencyMs: Int?) {
        filters.value = filters.value.copy(maxLatencyMs = maxLatencyMs)
    }

    fun setSourceId(sourceId: Long?) {
        filters.value = filters.value.copy(sourceId = sourceId)
    }

    fun setSort(sort: SortMode) {
        filters.value = filters.value.copy(sort = sort)
    }

    fun dismissMessage() {
        message.value = null
    }
}
