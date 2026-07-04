package com.nettools.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nettools.app.NetViewModel
import com.nettools.app.data.AccessPoint

@Composable
fun WifiScreen(vm: NetViewModel, modifier: Modifier = Modifier) {
    val aps by vm.aps.collectAsState()

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Wi-Fi analyzer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { vm.scanWifiAps() }) {
                Icon(Icons.Filled.Wifi, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Scan")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Nearby access points, sorted by signal. Use it to find the least crowded channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))

        if (aps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap Scan. (Requires Location permission and Wi-Fi enabled.)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aps, key = { it.bssid + it.ssid }) { ap -> ApRow(ap) }
            }
        }
    }
}

@Composable
private fun ApRow(ap: AccessPoint) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ap.ssid, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text("${ap.rssi} dBm", color = signalColor(ap.rssi), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            SignalBar(ap.rssi)
            Spacer(Modifier.height(6.dp))
            Text(
                "${ap.band} • ch ${ap.channel} • ${ap.security} • ${ap.bssid}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SignalBar(rssi: Int) {
    // Map -90..-30 dBm to 0..1.
    val fraction = ((rssi + 90) / 60f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .background(signalColor(rssi), RoundedCornerShape(4.dp)),
        )
    }
}

private fun signalColor(rssi: Int): Color = when {
    rssi >= -55 -> Color(0xFF34D399)
    rssi >= -70 -> Color(0xFFFBBF24)
    else -> Color(0xFFF87171)
}
