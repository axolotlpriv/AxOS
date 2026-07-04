package com.nettools.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nettools.app.NetViewModel
import com.nettools.app.Tab

@Composable
fun DashboardScreen(vm: NetViewModel, modifier: Modifier = Modifier, onNavigate: (Tab) -> Unit) {
    val wifi by vm.wifi.collectAsState()
    val devices by vm.devices.collectAsState()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "NetTools",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (wifi.connected) "Connected to ${wifi.ssid ?: "Wi-Fi"}" else "Not connected to Wi-Fi",
            color = MaterialTheme.colorScheme.primary,
        )

        SectionCard(title = "Connection") {
            InfoRow("SSID", wifi.ssid)
            InfoRow("Your IP", wifi.ipAddress)
            InfoRow("Gateway", wifi.gateway)
            InfoRow("Subnet", wifi.subnet?.let { "$it.0/24" })
            InfoRow("Netmask", wifi.netmask)
            InfoRow("DNS", wifi.dns.joinToString(", ").ifBlank { null })
            InfoRow("BSSID (AP)", wifi.bssid)
            InfoRow("Link speed", wifi.linkSpeedMbps?.let { "$it Mbps" })
            InfoRow("Signal", wifi.rssi?.let { "$it dBm" })
            InfoRow("Frequency", wifi.frequencyMhz?.let { "$it MHz" })
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.refreshWifi() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Refresh")
            }
        }

        SectionCard(title = "Network overview") {
            val newCount = devices.count { !it.known && !it.isSelf }
            InfoRow("Devices seen", if (devices.isEmpty()) null else devices.size.toString())
            InfoRow("New / untrusted", if (devices.isEmpty()) null else newCount.toString())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate(Tab.Scanner) }, modifier = Modifier.fillMaxWidth()) {
                Text("Scan my network")
            }
        }

        SectionCard(title = "Quick actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onNavigate(Tab.Wifi) }, modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi analyzer")
                }
                OutlinedButton(onClick = { onNavigate(Tab.Tools) }, modifier = Modifier.weight(1f)) {
                    Text("Speed & ping")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onNavigate(Tab.Router) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open router controls")
            }
        }
    }
}
