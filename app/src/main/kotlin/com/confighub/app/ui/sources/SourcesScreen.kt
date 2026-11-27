package com.confighub.app.ui.sources

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.confighub.app.data.db.FetchStatus
import com.confighub.app.data.db.SourceType
import com.confighub.app.ui.components.EmptyState
import com.confighub.app.ui.components.RelativeTimeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onBack: () -> Unit,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sources_add)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.sources_policy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            if (state.sources.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    EmptyState(
                        title = stringResource(R.string.sources_empty_title),
                        body = stringResource(R.string.sources_empty_body),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { viewModel.importSamples() },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp),
                        enabled = !state.importing,
                    ) {
                        if (state.importing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.sources_import_samples))
                    }
                }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    items(state.sources, key = { it.id }) { source ->
                        SourceRow(
                            source = source,
                            onToggle = { viewModel.setEnabled(source.id, it) },
                            onDelete = { viewModel.deleteSource(source.id) },
                            onRefresh = { viewModel.refreshNow(source) },
                        )
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddSourceDialog(
                error = state.addError,
                onAdd = { name, url, type, license, interval ->
                    viewModel.addSource(name, url, type, license, interval)
                },
                onDismiss = { viewModel.dismissAddDialog() },
            )
        }
    }
}

@Composable
private fun SourceRow(
    source: com.confighub.app.data.db.SourceEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        source.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Switch(checked = source.enabled, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = statusLabel(source), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(8.dp))
                RelativeTimeLabel(source.lastFetchAt)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(18.dp))
                }
            }
            source.licenseNote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sources_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun statusLabel(source: com.confighub.app.data.db.SourceEntity): String = when (source.lastStatus) {
    FetchStatus.OK -> stringResource(R.string.sources_status_ok, source.lastItemCount)
    FetchStatus.NOT_MODIFIED -> stringResource(R.string.sources_status_not_modified)
    FetchStatus.NEVER -> stringResource(R.string.sources_status_never)
    FetchStatus.SKIPPED -> stringResource(R.string.sources_status_skipped)
    FetchStatus.ERROR_HTTP, FetchStatus.ERROR_NETWORK ->
        stringResource(R.string.sources_status_error) +
            (source.lastError?.let { ": $it" } ?: "")
}

@Composable
private fun AddSourceDialog(
    error: String?,
    onAdd: (String, String, SourceType, String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("6") }
    var selectedType by remember { mutableStateOf(SourceType.SUBSCRIPTION_URL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sources_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.sources_name)) }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.sources_url)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    for (type in SourceType.entries) {
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(typeLabel(type), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                OutlinedTextField(value = license, onValueChange = { license = it }, label = { Text(stringResource(R.string.sources_license_note)) }, supportingText = { Text(stringResource(R.string.sources_license_hint)) })
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.sources_interval)) },
                    singleLine = true,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url, selectedType, license, interval.toIntOrNull() ?: 6) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text(stringResource(R.string.sources_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sources_cancel)) }
        },
    )
}

@Composable
private fun typeLabel(type: SourceType): String = when (type) {
    SourceType.GITHUB_RAW -> stringResource(R.string.source_type_github_raw)
    SourceType.SUBSCRIPTION_URL -> stringResource(R.string.source_type_subscription)
    SourceType.JSON_API -> stringResource(R.string.source_type_json_api)
    SourceType.CUSTOM -> stringResource(R.string.source_type_custom)
}
