package com.confighub.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.confighub.app.R
import com.confighub.app.core.model.Protocol
import com.confighub.app.data.db.HealthSampleEntity
import com.confighub.app.ui.theme.DegradedAmber
import com.confighub.app.ui.theme.FailingRed
import com.confighub.app.ui.theme.HealthyGreen

/** Latency color band, derived from the CURRENT operator profile thresholds. */
enum class LatencyBand(val labelRes: Int) {
    GOOD(R.string.latency_band_good),
    ACCEPTABLE(R.string.latency_band_acceptable),
    POOR(R.string.latency_band_poor),
}

fun latencyBand(tcpMeanMs: Long?, goodBelowMs: Int, acceptableBelowMs: Int): LatencyBand? = when {
    tcpMeanMs == null -> null
    tcpMeanMs <= goodBelowMs -> LatencyBand.GOOD
    tcpMeanMs <= acceptableBelowMs -> LatencyBand.ACCEPTABLE
    else -> LatencyBand.POOR
}

private fun bandColor(band: LatencyBand) = when (band) {
    LatencyBand.GOOD -> HealthyGreen
    LatencyBand.ACCEPTABLE -> DegradedAmber
    LatencyBand.POOR -> FailingRed
}

@Composable
fun ProtocolBadge(protocol: Protocol, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = protocol.wireName.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun ScoreBadge(score: Int?, modifier: Modifier = Modifier) {
    val label = score?.toString() ?: "–"
    val color = when {
        score == null -> MaterialTheme.colorScheme.surfaceVariant
        score >= 70 -> HealthyGreen
        score >= 40 -> DegradedAmber
        else -> FailingRed
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
fun LatencyLabel(
    sample: HealthSampleEntity?,
    goodBelowMs: Int,
    acceptableBelowMs: Int,
    modifier: Modifier = Modifier,
) {
    if (sample == null || !sample.ok || sample.tcpMeanMs == null) {
        Text(
            text = stringResource(R.string.home_not_tested),
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val band = latencyBand(sample.tcpMeanMs, goodBelowMs, acceptableBelowMs) ?: LatencyBand.POOR
    val color = bandColor(band)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = stringResource(R.string.latency_ms, sample.tcpMeanMs) + " · " +
                stringResource(band.labelRes),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Operator + network + honesty label for a measurement. */
@Composable
fun MeasurementMetaLabel(sample: HealthSampleEntity?, modifier: Modifier = Modifier) {
    if (sample == null) return
    val operator = sample.operatorCode
        ?.let { com.confighub.app.domain.operator.OperatorCatalog.fromPlmn(it) }
        ?.displayName
        ?: stringResource(R.string.home_operator_unknown)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$operator · ${sample.networkType} · ${stringResource(R.string.home_measured_label)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RelativeTimeLabel(epochMs: Long?, modifier: Modifier = Modifier) {
    val text = when {
        epochMs == null -> stringResource(R.string.time_never)
        else -> {
            val diffMinutes = ((System.currentTimeMillis() - epochMs) / 60_000L).toInt()
            when {
                diffMinutes < 1 -> stringResource(R.string.time_just_now)
                diffMinutes < 60 -> stringResource(R.string.time_minutes_ago, diffMinutes)
                diffMinutes < 60 * 24 -> stringResource(R.string.time_hours_ago, diffMinutes / 60)
                else -> stringResource(R.string.time_days_ago, diffMinutes / (60 * 24))
            }
        }
    }
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
