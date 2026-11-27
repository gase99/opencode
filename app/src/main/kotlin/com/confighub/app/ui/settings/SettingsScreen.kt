package com.confighub.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confighub.app.R
import com.confighub.app.domain.operator.OperatorCatalog
import com.confighub.app.domain.operator.OperatorId
import com.confighub.app.ui.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSources: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.exportedFile) {
        state.exportedFile?.let { file ->
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, null))
            viewModel.onExportConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.storageSecure) {
                item {
                    Text(
                        text = stringResource(R.string.settings_insecure_storage_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_refresh)) }
            item { IntervalRow(stringResource(R.string.settings_refresh_interval), state.settings.refreshIntervalHours, viewModel::setRefreshInterval) }
            item { IntervalRow(stringResource(R.string.settings_health_interval), state.settings.healthCheckIntervalHours, viewModel::setHealthInterval) }

            item { SectionHeader(stringResource(R.string.settings_section_operator)) }
            item { OperatorRow(state.settings.manualOperatorId, viewModel) }
            item {
                Text(
                    text = stringResource(R.string.settings_operator_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_scoring)) }
            item { WeightRow(stringResource(R.string.settings_weight_latency), state.settings.latencyWeight, SettingsViewModel.WeightKind.LATENCY, viewModel) }
            item { WeightRow(stringResource(R.string.settings_weight_jitter), state.settings.jitterWeight, SettingsViewModel.WeightKind.JITTER, viewModel) }
            item { WeightRow(stringResource(R.string.settings_weight_failure), state.settings.failureWeight, SettingsViewModel.WeightKind.FAILURE, viewModel) }
            item { WeightRow(stringResource(R.string.settings_weight_recency), state.settings.recencyWeight, SettingsViewModel.WeightKind.RECENCY, viewModel) }
            item { WeightRow(stringResource(R.string.settings_weight_protocol), state.settings.protocolWeight, SettingsViewModel.WeightKind.PROTOCOL, viewModel) }

            item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
            item {
                ToggleRow(
                    title = stringResource(R.string.settings_allow_experimental),
                    description = stringResource(R.string.settings_allow_experimental_desc),
                    checked = state.settings.allowExperimentalProtocols,
                    onChange = viewModel::setAllowExperimental,
                )
            }
            item {
                ToggleRow(
                    title = stringResource(R.string.settings_allow_http),
                    description = stringResource(R.string.settings_allow_http_desc),
                    checked = state.settings.allowInsecureHttpSources,
                    onChange = viewModel::setAllowInsecureHttp,
                )
            }
            item {
                Column {
                    Text(stringResource(R.string.settings_keep_history) + ": " + stringResource(R.string.days_value, state.settings.keepHistoryDays))
                    Slider(
                        value = state.settings.keepHistoryDays.toFloat(),
                        onValueChange = { viewModel.setKeepHistory(it.toInt().coerceIn(1, 90)) },
                        valueRange = 1f..90f,
                    )
                }
            }
            item {
                Column {
                    Text(stringResource(R.string.settings_max_configs) + ": ${state.settings.maxConfigsPerSource}")
                    Slider(
                        value = state.settings.maxConfigsPerSource.toFloat(),
                        onValueChange = { viewModel.setMaxConfigs((it / 50).toInt() * 50) },
                        valueRange = 100f..2000f,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.settings_vpn_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_diagnostics)) }
            item {
                Button(onClick = { viewModel.exportDiagnostics() }) {
                    Text(stringResource(R.string.settings_export_diagnostics))
                }
            }
            item {
                Text(
                    text = stringResource(R.string.settings_export_redaction_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.exportFailed) {
                item {
                    Text(
                        text = stringResource(R.string.diag_export_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                Column {
                    InfoRow(stringResource(R.string.settings_title), stringResource(R.string.settings_version, state.version))
                    TextButton(onClick = onOpenSources) {
                        Text(stringResource(R.string.nav_sources))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider()
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun IntervalRow(label: String, current: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (hours in listOf(1, 3, 6, 12, 24)) {
                item(key = hours) {
                    FilterChip(
                        selected = current == hours,
                        onClick = { onSelect(hours) },
                        label = { Text(stringResource(R.string.hours_value, hours)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OperatorRow(manualOperatorId: String?, viewModel: SettingsViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "auto") {
            FilterChip(
                selected = manualOperatorId == null,
                onClick = { viewModel.setOperator(null) },
                label = { Text(stringResource(R.string.settings_operator_auto)) },
            )
        }
        for (operator in OperatorId.entries) {
            item(key = operator.name) {
                FilterChip(
                    selected = manualOperatorId == operator.name,
                    onClick = { viewModel.setOperator(operator.name) },
                    label = { Text(operator.displayName) },
                )
            }
        }
    }
}

@Composable
private fun WeightRow(label: String, value: Float, kind: SettingsViewModel.WeightKind, viewModel: SettingsViewModel) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${(sliderValue * 100).toInt()}%")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { viewModel.setWeight(kind, sliderValue) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.width(56.dp))
    }
}
