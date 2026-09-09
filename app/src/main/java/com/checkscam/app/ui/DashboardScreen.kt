package com.checkscam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkscam.alerts.AlertRecord
import com.checkscam.alerts.RiskVisual
import com.checkscam.app.PipelineStatus
import com.checkscam.app.ui.theme.CheckScamTheme
import com.checkscam.classifier.FraudSynthesized
import com.checkscam.classifier.RiskLevel
import com.checkscam.classifier.ScamType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Live dashboard: pipeline status, most recent alert and persisted alert
 * history (AGENTS.md §5.5, §6). Purely presentational.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    status: PipelineStatus = PipelineStatus.IDLE,
    history: List<AlertRecord> = emptyList(),
    onClearHistory: () -> Unit = {},
    onDisable: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Scam Detection",
            style = MaterialTheme.typography.headlineSmall
        )
        StatusRow(status = status, modifier = Modifier.padding(top = 8.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Latest alert",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (history.isEmpty()) {
            EmptyAlertCard(onDisable = onDisable, modifier = Modifier.fillMaxWidth())
        } else {
            AlertCard(record = history.first(), modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = onClearHistory) {
                    Text("Clear history")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(history, key = { it.occurredAtEpochMs }) { record ->
                    HistoryItem(record = record)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(status: PipelineStatus, modifier: Modifier = Modifier) {
    val (color, label, busy) = when (status) {
        PipelineStatus.IDLE -> Triple(Color(0xFF757575), "Idle", false)
        PipelineStatus.LISTENING -> Triple(Color(0xFF1E88E5), "Listening", false)
        PipelineStatus.ANALYZING -> Triple(Color(0xFFF57C00), "Analyzing", true)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        if (busy) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun EmptyAlertCard(onDisable: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "No alerts yet. CheckScam will notify you here and in a "
                    + "heads-up notification whenever a call matches a scam pattern.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onDisable) {
                Text("Disable detection")
            }
        }
    }
}

@Composable
private fun AlertCard(record: AlertRecord, modifier: Modifier = Modifier) {
    val riskColor = Color(RiskVisual.argb(record.result.riskLevel))
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = riskColor.copy(alpha = 0.10f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color = riskColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${RiskVisual.label(record.result.riskLevel)} · "
                        + record.result.scamType.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = riskColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = record.result.rationale.ifBlank { "No rationale provided." },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun HistoryItem(record: AlertRecord, modifier: Modifier = Modifier) {
    val riskColor = Color(RiskVisual.argb(record.result.riskLevel))
    val label = RiskVisual.label(record.result.riskLevel)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(top = 2.dp)
                    .background(color = riskColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = riskColor
                    )
                    Text(
                        text = formatTimestamp(record.occurredAtEpochMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = record.result.scamType.label,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = record.result.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    CheckScamTheme {
        DashboardScreen(
            status = PipelineStatus.ANALYZING,
            history = listOf(
                AlertRecord(
                    occurredAtEpochMs = System.currentTimeMillis(),
                    result = FraudSynthesized(
                        riskLevel = RiskLevel.HIGH,
                        scamType = ScamType.TELECOM_FRAUD,
                        rationale = "El estafador usa urgencia y miedo a perder " +
                            "el servicio para obtener datos personales."
                    )
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenEmptyPreview() {
    CheckScamTheme {
        DashboardScreen(status = PipelineStatus.IDLE)
    }
}