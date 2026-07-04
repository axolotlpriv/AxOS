package com.nettools.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nettools.app.NetViewModel
import com.nettools.app.data.NetDevice
import com.nettools.app.net.PortScanner

@Composable
fun ScannerScreen(vm: NetViewModel, modifier: Modifier = Modifier) {
    val devices by vm.devices.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val progress by vm.scanProgress.collectAsState()
    var selected by remember { mutableStateOf<NetDevice?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Devices", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (devices.isNotEmpty()) {
                    TextButton(onClick = { vm.trustAllVisible() }) { Text("Trust all") }
                }
                Button(onClick = { vm.rescan() }, enabled = !scanning) {
                    Icon(Icons.Filled.Radar, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (scanning) "Scanning" else "Scan")
                }
            }
        }

        if (scanning) {
            Spacer(Modifier.height(12.dp))
            val (done, total) = progress
            LinearProgressIndicator(
                progress = { if (total > 0) done / total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Probing $done / $total hosts…", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(12.dp))

        if (devices.isEmpty() && !scanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tap Scan to discover every device on your Wi-Fi.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.ip }) { device ->
                    DeviceRow(device) { selected = device }
                }
            }
        }
    }

    selected?.let { device ->
        DeviceDialog(
            device = device,
            onDismiss = { selected = null },
            onScanPorts = { vm.scanPorts(device) },
            onTrust = { vm.markKnown(device); selected = null },
            onWake = { device.mac?.let { vm.sendWol(it) } },
            onBlock = { vm.manageOnRouter(device, NetViewModel.RouterAction.BLOCK); selected = null },
            onThrottle = { vm.manageOnRouter(device, NetViewModel.RouterAction.THROTTLE); selected = null },
            // keep the dialog's device in sync as ports come back
            live = devices.firstOrNull { it.ip == device.ip } ?: device,
        )
    }
}

@Composable
private fun DeviceRow(device: NetDevice, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotColor = when {
                device.isSelf -> MaterialTheme.colorScheme.primary
                device.isGateway -> Color(0xFF34D399)
                !device.known -> Color(0xFFFBBF24)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            }
            Box(Modifier.size(10.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.hostname ?: device.vendor ?: device.ip,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    device.ip + (device.mac?.let { "  •  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                when {
                    device.isSelf -> Tag("You", MaterialTheme.colorScheme.primary)
                    device.isGateway -> Tag("Router", Color(0xFF34D399))
                    !device.known -> Tag("New", Color(0xFFFBBF24))
                    else -> Tag("Trusted", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DeviceDialog(
    device: NetDevice,
    live: NetDevice,
    onDismiss: () -> Unit,
    onScanPorts: () -> Unit,
    onTrust: () -> Unit,
    onWake: () -> Unit,
    onBlock: () -> Unit,
    onThrottle: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(device.hostname ?: device.vendor ?: device.ip) },
        text = {
            Column {
                InfoRow("IP", device.ip)
                InfoRow("MAC", device.mac)
                InfoRow("Vendor", device.vendor)
                InfoRow("Hostname", device.hostname)
                InfoRow("Role", when {
                    device.isSelf -> "This device"
                    device.isGateway -> "Router / gateway"
                    else -> "Client"
                })
                Spacer(Modifier.height(8.dp))
                if (live.openPorts.isNotEmpty()) {
                    Text("Open ports", fontWeight = FontWeight.SemiBold)
                    live.openPorts.forEach { port ->
                        InfoRow(PortScanner.PORT_NAMES[port] ?: "port", port.toString())
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onScanPorts, modifier = Modifier.weight(1f)) {
                        Text("Scan ports")
                    }
                    if (!device.known && !device.isSelf) {
                        OutlinedButton(onClick = onTrust, modifier = Modifier.weight(1f)) {
                            Text("Trust")
                        }
                    }
                }
                if (device.mac != null && !device.isSelf) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = onWake, modifier = Modifier.fillMaxWidth()) {
                        Text("Wake-on-LAN")
                    }
                }
                if (!device.isSelf) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Manage on router",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBlock, modifier = Modifier.weight(1f)) { Text("Block") }
                        Button(onClick = onThrottle, modifier = Modifier.weight(1f)) { Text("Throttle") }
                    }
                }
            }
        },
    )
}
