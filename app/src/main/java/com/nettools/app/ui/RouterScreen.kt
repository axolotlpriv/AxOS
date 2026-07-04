package com.nettools.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nettools.app.NetViewModel
import com.nettools.app.NetViewModel.RouterAction

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RouterScreen(vm: NetViewModel, modifier: Modifier = Modifier) {
    val wifi by vm.wifi.collectAsState()
    val router by vm.router.collectAsState()
    val detecting by vm.routerDetecting.collectAsState()
    val target by vm.routerTarget.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val gateway = wifi.gateway
    var url by remember(gateway) { mutableStateOf(gateway?.let { "http://$it" } ?: "http://192.168.1.1") }
    var targetMac by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RouterAction.BLOCK) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }

    // Auto-detect the router when first shown / when gateway changes.
    LaunchedEffect(gateway) {
        if (gateway != null && !router.detected) vm.detectRouter()
    }
    // A device was queued from the scanner: pre-fill it and pick the action.
    LaunchedEffect(target) {
        target?.let {
            targetMac = it.mac ?: ""
            action = it.action
            webView?.loadUrl(normalize(url))
            vm.consumeRouterTarget()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text("Router controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionCard(title = "Detected router") {
            if (detecting) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("  Identifying…")
                }
            } else {
                InfoRow("Vendor", router.vendor)
                InfoRow("Model", router.model)
                InfoRow("Firmware", router.server)
                InfoRow("Admin", router.adminUrl)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Wrong or blank? Set it manually:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FamilyChip("Archer", "archer", router.family) { vm.setRouterFamily("archer") }
                FamilyChip("Deco", "deco", router.family) { vm.setRouterFamily("deco") }
                FamilyChip("TL-…", "legacy", router.family) { vm.setRouterFamily("legacy") }
                FamilyChip("Other", "generic", router.family) { vm.setRouterFamily("generic") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.detectRouter() }) { Text("Re-detect") }
        }

        Spacer(Modifier.height(10.dp))

        SectionCard(title = "Block or throttle a device") {
            OutlinedTextField(
                value = targetMac,
                onValueChange = { targetMac = it },
                label = { Text("Device MAC") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(targetMac)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Copy MAC") }
                Button(
                    onClick = { action = RouterAction.BLOCK; webView?.loadUrl(normalize(url)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Block") }
                Button(
                    onClick = { action = RouterAction.THROTTLE; webView?.loadUrl(normalize(url)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Throttle") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stepsFor(router.family, action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            if (router.family == "deco") {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { openDecoApp(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Deco app")
                }
            }
        }

        if (router.family == "deco" || router.family == "generic") {
            Spacer(Modifier.height(10.dp))
            DecoControlCard(vm, gateway)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Router admin URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { webView?.loadUrl(normalize(url)) }, modifier = Modifier.weight(1f)) {
                Text("Open")
            }
            Button(onClick = { webView?.reload() }, modifier = Modifier.weight(1f)) { Text("Reload") }
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
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp),
        )
    }
}

private fun stepsFor(family: String, action: RouterAction): String = when (family) {
    "archer" -> when (action) {
        RouterAction.BLOCK ->
            "TP-Link Archer: Advanced → Security → Access Control → turn it On, set the mode " +
                "to Blacklist, then Add the device's MAC. It's dropped from the network immediately."
        RouterAction.THROTTLE ->
            "TP-Link Archer: Advanced → QoS to prioritise/limit, or Advanced → Bandwidth Control / " +
                "Parental Controls to cap this device's speed. Add a rule targeting its MAC or IP."
    }
    "legacy" -> when (action) {
        RouterAction.BLOCK ->
            "TP-Link (TL-… firmware): Access Control → Host: add the device MAC, then Access " +
                "Control → Rule: create a Deny rule for that host."
        RouterAction.THROTTLE ->
            "TP-Link (TL-… firmware): Bandwidth Control → enable it, then add an entry for the " +
                "device's IP with a max egress/ingress bandwidth to throttle it."
    }
    "deco" -> when (action) {
        RouterAction.BLOCK ->
            "TP-Link Deco is managed by the Deco app: Clients → pick the device → Block. (The web " +
                "page here has limited controls on Deco.)"
        RouterAction.THROTTLE ->
            "TP-Link Deco: create a profile in the Deco app, add this device, and use QoS / time " +
                "limits to throttle it."
    }
    "tplink" -> when (action) {
        RouterAction.BLOCK -> "TP-Link: look for Access Control / Parental Controls and add this MAC to the block list."
        RouterAction.THROTTLE -> "TP-Link: look for QoS or Bandwidth Control and cap this device by MAC/IP."
    }
    else -> when (action) {
        RouterAction.BLOCK -> "Open your router's Access Control / MAC filter / device list and block this MAC."
        RouterAction.THROTTLE -> "Open your router's QoS / Bandwidth Control and set a speed cap for this device."
    }
}

private fun normalize(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "http://$trimmed"
}

@Composable
private fun FamilyChip(label: String, value: String, current: String, onClick: () -> Unit) {
    FilterChip(selected = current == value, onClick = onClick, label = { Text(label) })
}

@Composable
private fun DecoControlCard(vm: NetViewModel, gateway: String?) {
    val connected by vm.decoConnected.collectAsState()
    val busy by vm.decoBusy.collectAsState()
    val devices by vm.decoDevices.collectAsState()
    var host by remember(gateway) { mutableStateOf(gateway ?: "192.168.68.1") }
    var password by remember { mutableStateOf("") }

    SectionCard(title = "Deco direct control (experimental)") {
        Text(
            "Log in with your Deco admin password to block or throttle devices from " +
                "inside this app — no Deco app needed. Uses the Deco's local encrypted API.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(8.dp))
        if (!connected) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Deco IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Deco admin password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.decoConnect(host.trim(), password) },
                enabled = !busy && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Connecting…" else "Connect") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Connected", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { vm.decoRefresh() }, enabled = !busy) { Text("Refresh") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.decoDisconnect() }) { Text("Log out") }
            }
            Spacer(Modifier.height(8.dp))
            if (busy && devices.isEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            devices.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${d.ip}  •  ${d.mac}" + if (d.blocked) "  •  BLOCKED" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.decoThrottle(d, 512, 256) },
                        enabled = !busy,
                    ) { Text("Lag") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { vm.decoSetBlocked(d, !d.blocked) },
                        enabled = !busy,
                    ) { Text(if (d.blocked) "Unblock" else "Block") }
                }
            }
        }
    }
}

/** Launch the TP-Link Deco app if installed, otherwise open its Play Store page. */
private fun openDecoApp(context: Context) {
    val pkg = "com.tplink.tether" // TP-Link Tether / Deco
    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
    if (launch != null) {
        context.startActivity(launch)
    } else {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
