package com.confighub.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confighub.app.R
import com.confighub.app.core.model.Protocol
import com.confighub.app.ui.components.EmptyState
import com.confighub.app.ui.components.LatencyLabel
import com.confighub.app.ui.components.MeasurementMetaLabel
import com.confighub.app.ui.components.ProtocolBadge
import com.confighub.app.ui.components.RelativeTimeLabel
import com.confighub.app.ui.components.ScoreBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSources) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.nav_sources))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            FilterBar(state = state, viewModel = viewModel)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.items.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        state.message?.let { message ->
                            item {
                                Text(
                                    text = stringResource(R.string.home_error_prefix) + ": " + message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                        items(state.items, key = { it.config.hash }) { item ->
                            HomeRow(
                                item = item,
                                goodBelowMs = state.goodBelowMs,
                                acceptableBelowMs = state.acceptableBelowMs,
                                isTesting = state.testingHash == item.config.hash,
                                onOpen = { onOpenDetail(item.config.hash) },
                                onTest = { viewModel.testNow(item.config.hash) },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(item.config.hash, !item.config.isFavorite)
                                },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(state: HomeUiState, viewModel: HomeViewModel) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.home_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            item { ProtocolFilterChip(state, viewModel) }
            item {
                FilterChip(
                    selected = state.filters.favoritesOnly,
                    onClick = { viewModel.setFavoritesOnly(!state.filters.favoritesOnly) },
                    label = { Text(stringResource(R.string.home_filter_favorites)) },
                )
            }
            item { SortFilterChip(state, viewModel) }
            item { LatencyFilterChip(state, viewModel) }
        }
    }
}

@Composable
private fun ProtocolFilterChip(state: HomeUiState, viewModel: HomeViewModel) {
    var expanded by remember { mutableStateOf(false) }
    FilterChip(
        selected = state.filters.protocol != null,
        onClick = { expanded = true },
        label = {
            Text(
                state.filters.protocol?.wireName?.uppercase()
                    ?: stringResource(R.string.home_filter_protocol),
            )
        },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.latency_any)) },
            onClick = {
                viewModel.setProtocol(null)
                expanded = false
            },
        )
        for (protocol in listOf(Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN, Protocol.SHADOWSOCKS)) {
            DropdownMenuItem(
                text = { Text(protocol.wireName.uppercase()) },
                onClick = {
                    viewModel.setProtocol(protocol)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun SortFilterChip(state: HomeUiState, viewModel: HomeViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        SortMode.SCORE to stringResource(R.string.sort_score),
        SortMode.LATENCY to stringResource(R.string.sort_latency),
        SortMode.RECENT to stringResource(R.string.sort_recent),
    )
    FilterChip(
        selected = false,
        onClick = { expanded = true },
        label = { Text(stringResource(R.string.home_filter_sort) + ": " + labels[state.filters.sort]) },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(labels.getValue(mode)) },
                onClick = {
                    viewModel.setSort(mode)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun LatencyFilterChip(state: HomeUiState, viewModel: HomeViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val label = state.filters.maxLatencyMs?.let { stringResource(R.string.latency_ms, it) }
        ?: stringResource(R.string.home_filter_latency)
    FilterChip(
        selected = state.filters.maxLatencyMs != null,
        onClick = { expanded = true },
        label = { Text(label) },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf(null, 300, 600, 1000).forEach { limit ->
            DropdownMenuItem(
                text = {
                    Text(
                        limit?.let { stringResource(R.string.latency_ms, it) }
                            ?: stringResource(R.string.latency_any),
                    )
                },
                onClick = {
                    viewModel.setMaxLatency(limit)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun HomeRow(
    item: HomeItem,
    goodBelowMs: Int,
    acceptableBelowMs: Int,
    isTesting: Boolean,
    onOpen: () -> Unit,
    onTest: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProtocolBadge(item.config.protocol)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.config.name ?: item.config.host,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = "${item.config.host}:${item.config.port}" +
                            (item.sourceName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                ScoreBadge(item.score)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LatencyLabel(item.latest, goodBelowMs, acceptableBelowMs)
                Spacer(modifier = Modifier.width(8.dp))
                MeasurementMetaLabel(item.latest, modifier = Modifier.weight(1f))
                RelativeTimeLabel(item.latest?.testedAt)
                Spacer(modifier = Modifier.width(4.dp))
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onTest, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.home_test_now),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (item.config.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
