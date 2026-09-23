package com.checkscam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.checkscam.app.observability.LogEntry
import com.checkscam.app.observability.LogStage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stageColors = mapOf(
    LogStage.AUDIO to Color(0xFF00BCD4),
    LogStage.STT to Color(0xFF66BB6A),
    LogStage.DIARIZATION to Color(0xFFAB47BC),
    LogStage.CLASSIFIER to Color(0xFFFDD835),
    LogStage.ALERTS to Color(0xFFFF7043),
    LogStage.NOTIFICATIONS to Color(0xFF29B6F6),
    LogStage.SYSTEM to Color(0xFF90A4AE)
)

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(timeFormatter)

/**
 * Live pipeline diagnostic terminal (AGENTS.md §5.6). Monospaced, auto-scrolling,
 * color-coded per pipeline stage. Purely presentational; reads [entries] from the
 * shared [com.checkscam.app.observability.AppLogBuffer].
 */
@Composable
fun DiagnosticTerminalView(
    entries: List<LogEntry>,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    onToggleAutoScroll: () -> Unit = {},
    onClear: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size, autoScroll) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.scrollToItem(entries.lastIndex)
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pipeline log ${if (entries.isEmpty()) "" else "(${entries.size})"}",
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleAutoScroll) {
                    Text(if (autoScroll) "Autoscroll ON" else "Autoscroll OFF")
                }
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(entries.size) { index ->
                    val entry = entries[index]
                    TerminalLine(entry)
                }
            }
        }
    }
}

@Composable
private fun TerminalLine(entry: LogEntry) {
    val color = stageColors[entry.stage] ?: Color.White
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    Row {
        Text(
            text = "[${entry.stage.tag}]",
            style = mono,
            color = color
        )
        Text(
            text = " ${formatTime(entry.timestampEpochMs)}",
            style = mono,
            color = Color(0xFF78909C)
        )
        Text(
            text = "  ${entry.message}",
            style = mono,
            color = Color(0xFFECEFF1)
        )
    }
}