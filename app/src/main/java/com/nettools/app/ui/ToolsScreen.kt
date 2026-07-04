package com.nettools.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nettools.app.NetViewModel

@Composable
fun ToolsScreen(vm: NetViewModel, modifier: Modifier = Modifier) {
    val speed by vm.speed.collectAsState()
    val speedRunning by vm.speedRunning.collectAsState()
    val pings by vm.pings.collectAsState()
    val pingRunning by vm.pingRunning.collectAsState()
    val wifi by vm.wifi.collectAsState()

    var pingHost by remember { mutableStateOf("1.1.1.1") }
    var wolMac by remember { mutableStateOf("") }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(title = "Internet speed test") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        speed?.let { String.format("%.1f", it.downMbps) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Mbps download", style = MaterialTheme.typography.bodySmall)
                    speed?.let {
                        Text(
                            "${it.bytes / 1_000_000} MB in ${String.format("%.1f", it.seconds)}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                if (speedRunning) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = { vm.runSpeedTest() }) { Text("Start") }
                }
            }
        }

        SectionCard(title = "Latency / ping monitor") {
            OutlinedTextField(
                value = pingHost,
                onValueChange = { pingHost = it },
                label = { Text("Host or IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.togglePing(pingHost.trim()) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (pingRunning) "Stop" else "Start") }
                OutlinedButton(
                    onClick = { wifi.gateway?.let { pingHost = it } },
                    modifier = Modifier.weight(1f),
                ) { Text("Ping router") }
            }
            if (pings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LatencyChart(pings.map { it.latencyMs })
                val valid = pings.mapNotNull { it.latencyMs }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("last", pings.last().latencyMs?.let { "$it ms" } ?: "timeout")
                    Stat("min", valid.minOrNull()?.let { "$it ms" } ?: "—")
                    Stat("avg", valid.takeIf { it.isNotEmpty() }?.average()?.let { "${it.toInt()} ms" } ?: "—")
                    Stat("max", valid.maxOrNull()?.let { "$it ms" } ?: "—")
                }
            }
        }

        SectionCard(title = "Wake-on-LAN") {
            Text(
                "Power on one of your own machines by its MAC address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = wolMac,
                onValueChange = { wolMac = it },
                label = { Text("MAC (AA:BB:CC:DD:EE:FF)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.sendWol(wolMac.trim()) },
                enabled = wolMac.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send magic packet") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun LatencyChart(samples: List<Long?>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val maxVal = (samples.mapNotNull { it }.maxOrNull() ?: 100L).coerceAtLeast(20L).toFloat()
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width
        val h = size.height
        // baseline grid
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        if (samples.size < 2) return@Canvas
        val step = w / (samples.size - 1).coerceAtLeast(1)
        var prev: Offset? = null
        samples.forEachIndexed { i, v ->
            if (v == null) { prev = null; return@forEachIndexed }
            val x = step * i
            val y = h - (v / maxVal).coerceIn(0f, 1f) * h
            val point = Offset(x, y)
            prev?.let { drawLine(line, it, point, strokeWidth = 4f) }
            prev = point
        }
    }
    Text(
        "scale: 0–${maxVal.toInt()} ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
}
