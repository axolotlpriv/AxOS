package com.nettools.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.nettools.app.ui.DashboardScreen
import com.nettools.app.ui.RouterScreen
import com.nettools.app.ui.ScannerScreen
import com.nettools.app.ui.ToolsScreen
import com.nettools.app.ui.WifiScreen
import com.nettools.app.ui.theme.NetToolsTheme

enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Home", Icons.Filled.Dashboard),
    Scanner("Devices", Icons.Filled.Radar),
    Wifi("Wi-Fi", Icons.Filled.Wifi),
    Tools("Tools", Icons.Filled.Speed),
    Router("Router", Icons.Filled.Router),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetToolsTheme {
                Surface { AppRoot() }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val vm: NetViewModel = viewModel()
    var tab by remember { mutableStateOf(Tab.Dashboard) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshWifi() }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
        vm.refreshWifi()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.Dashboard -> DashboardScreen(vm, modifier) { tab = it }
            Tab.Scanner -> ScannerScreen(vm, modifier)
            Tab.Wifi -> WifiScreen(vm, modifier)
            Tab.Tools -> ToolsScreen(vm, modifier)
            Tab.Router -> RouterScreen(vm, modifier)
        }
    }
}
