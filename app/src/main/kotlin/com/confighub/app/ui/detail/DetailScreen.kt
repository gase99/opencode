package com.confighub.app.ui.detail

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confighub.app.R
import com.confighub.app.ui.components.InfoRow
import com.confighub.app.ui.components.ProtocolBadge
import com.confighub.app.ui.components.RelativeTimeLabel
import com.confighub.app.ui.components.ScoreBadge
import com.confighub.app.ui.theme.FailingRed
import com.confighub.app.ui.theme.HealthyGreen
import com.confighub.app.ui.vpn.VpnViewModel
import com.confighub.app.vpn.VpnState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
    vpnViewModel: VpnViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vpnState by vpnViewModel.state.collectAsStateWithLifecycle()
    val pendingConsent by vpnViewModel.pendingConsent.collectAsStateWithLifecycle()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnViewModel.onConsentGranted()
        } else {
            vpnViewModel.onConsentDenied()
        }
    }
    LaunchedEffect(pendingConsent) {
        pendingConsent?.let { intent ->
            consentLauncher.launch(intent)
            vpnViewModel.onConsentLaunched()
        }
    }
    val config = state.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    config?.let { entity ->
                        IconButton(onClick = { viewModel.toggleFavorite(!entity.isFavorite) }) {
                            Icon(
                                imageVector = if (entity.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(
                                    if (entity.isFavorite) R.string.detail_favorite_remove else R.string.detail_favorite_add,
                                ),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (config == null) {
            Text(
                text = stringResource(R.string.detail_not_found),
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProtocolBadge(config.protocol)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = config.name ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            ScoreBadge(config.lastScore?.toInt())
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow(stringResource(R.string.detail_host), "${config.host}:${config.port}")
                        InfoRow(stringResource(R.string.detail_protocol), config.protocol.name)
                        InfoRow(stringResource(R.string.detail_network), config.network.name)
                        InfoRow(stringResource(R.string.detail_tls), config.tls.name)
                        config.sni?.let { InfoRow(stringResource(R.string.detail_sni), it) }
                        config.alpnText?.let { InfoRow(stringResource(R.string.detail_alpn), it) }
                        config.fingerprint?.let { InfoRow(stringResource(R.string.detail_fingerprint), it) }
                        config.plugin?.let { InfoRow(stringResource(R.string.detail_plugin), it) }
                        InfoRow(stringResource(R.string.detail_source), state.sourceName ?: "—")
                        RelativeTimeLabel(config.firstSeenAt)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_secret_masked),
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.detail_secret_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { viewModel.testNow() }, enabled = !state.isTesting) {
                                if (state.isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.home_test_now))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ConnectionSection(
                            state = vpnState,
                            onConnect = { vpnViewModel.connect(config.hash) },
                            onDisconnect = { vpnViewModel.disconnect() },
                        )
                    }
                }
            }

            state.score?.let { score ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.detail_score_explanation),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            for (component in score.components) {
                                if (component.weight <= 0.0) continue
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = componentLabel(component.name),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(110.dp),
                                    )
                                    Text(
                                        text = "${component.score.toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(40.dp),
                                    )
                                    Text(
                                        text = "× ${(component.weight * 100).toInt()}% → " +
                                            "${(component.contribution * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.detail_history_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (state.history.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.detail_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.history) { sample ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (sample.ok) Icons.Filled.CheckCircle else Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (sample.ok) {
                                    com.confighub.app.ui.theme.HealthyGreen
                                } else {
                                    com.confighub.app.ui.theme.FailingRed
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (sample.ok) {
                                    Text(
                                        text = sample.tcpMeanMs?.let { stringResource(R.string.latency_ms, it) } ?: "—",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = "dns=${sample.dnsMs ?: "-"} tls=${sample.tlsMs ?: "-"} " +
                                            "jitter=${sample.jitterMs ?: "-"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Text(
                                        text = sample.errorKind ?: "FAILED",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    text = "${sample.networkType} · ${sample.operatorCode ?: "-"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RelativeTimeLabel(sample.testedAt)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun componentLabel(name: String): String = when (name) {
    "latency" -> stringResource(R.string.score_latency)
    "jitter" -> stringResource(R.string.score_jitter)
    "failure" -> stringResource(R.string.score_failure)
    "recency" -> stringResource(R.string.score_recency)
    "protocol" -> stringResource(R.string.score_protocol)
    else -> name
}

/** One-tap connect/disconnect with an explicit, honest status line. */
@Composable
private fun ConnectionSection(
    state: VpnState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column {
        when (state) {
            is VpnState.Idle -> Button(onClick = onConnect) { Text(stringResource(R.string.vpn_connect)) }

            is VpnState.NeedsConsent -> Text(
                text = stringResource(R.string.vpn_consent_required),
                style = MaterialTheme.typography.bodySmall,
            )

            is VpnState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.vpn_state_connecting))
            }

            is VpnState.Connected -> Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDisconnect) { Text(stringResource(R.string.vpn_disconnect)) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.vpn_state_connected, state.displayName ?: state.hash.take(8)),
                    color = HealthyGreen,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is VpnState.Disconnecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.vpn_state_disconnecting))
            }

            is VpnState.Failed -> Column {
                Text(
                    text = stringResource(R.string.vpn_state_failed) + ": " + state.reason,
                    color = FailingRed,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onConnect) { Text(stringResource(R.string.common_retry)) }
            }
        }
        Text(
            text = stringResource(R.string.vpn_mock_core_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
