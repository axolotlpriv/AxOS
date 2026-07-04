package com.nettools.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nettools.app.NetViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouterScreen(vm: NetViewModel, modifier: Modifier = Modifier) {
    val wifi by vm.wifi.collectAsState()
    val gateway = wifi.gateway
    var url by remember(gateway) { mutableStateOf(gateway?.let { "http://$it" } ?: "http://192.168.1.1") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("Router controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Log into your router to block a device, set MAC filters, or cap a device's " +
                "bandwidth (QoS) — the router enforces these on the network you own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 6.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Router admin URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { webView?.loadUrl(normalize(url)); loaded = true },
                modifier = Modifier.weight(1f),
            ) { Text("Open") }
            Button(
                onClick = { webView?.reload() },
                modifier = Modifier.weight(1f),
            ) { Text("Reload") }
        }
        Spacer(Modifier.height(10.dp))

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = WebViewClient()
                    webView = this
                    if (!loaded) { loadUrl(normalize(url)); loaded = true }
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Text(
            "Tip: common admin pages are 192.168.0.1 / 192.168.1.1 / 10.0.0.1. " +
                "The label under the router usually lists the address and default login.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun normalize(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "http://$trimmed"
}
