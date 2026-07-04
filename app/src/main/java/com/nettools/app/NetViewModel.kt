package com.nettools.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nettools.app.data.AccessPoint
import com.nettools.app.data.NetDevice
import com.nettools.app.data.PingSample
import com.nettools.app.data.RouterInfo
import com.nettools.app.data.SpeedResult
import com.nettools.app.data.WifiStatus
import com.nettools.app.net.KnownDeviceStore
import com.nettools.app.net.NetworkScanner
import com.nettools.app.net.Pinger
import com.nettools.app.net.PortScanner
import com.nettools.app.net.RouterDetector
import com.nettools.app.net.SpeedTest
import com.nettools.app.net.WakeOnLan
import com.nettools.app.net.WifiInfoProvider
import com.nettools.app.net.WifiScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NetViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = NetworkScanner(app)
    private val knownStore = KnownDeviceStore(app)

    private val _wifi = MutableStateFlow(WifiStatus())
    val wifi: StateFlow<WifiStatus> = _wifi.asStateFlow()

    private val _devices = MutableStateFlow<List<NetDevice>>(emptyList())
    val devices: StateFlow<List<NetDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0 to 0)
    val scanProgress: StateFlow<Pair<Int, Int>> = _scanProgress.asStateFlow()

    private val _aps = MutableStateFlow<List<AccessPoint>>(emptyList())
    val aps: StateFlow<List<AccessPoint>> = _aps.asStateFlow()

    private val _speed = MutableStateFlow<SpeedResult?>(null)
    val speed: StateFlow<SpeedResult?> = _speed.asStateFlow()

    private val _speedRunning = MutableStateFlow(false)
    val speedRunning: StateFlow<Boolean> = _speedRunning.asStateFlow()

    private val _pings = MutableStateFlow<List<PingSample>>(emptyList())
    val pings: StateFlow<List<PingSample>> = _pings.asStateFlow()

    private val _pingRunning = MutableStateFlow(false)
    val pingRunning: StateFlow<Boolean> = _pingRunning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _router = MutableStateFlow(RouterInfo())
    val router: StateFlow<RouterInfo> = _router.asStateFlow()

    private val _routerDetecting = MutableStateFlow(false)
    val routerDetecting: StateFlow<Boolean> = _routerDetecting.asStateFlow()

    // When set, the Router tab should open and pre-fill this device for block/throttle.
    private val _routerTarget = MutableStateFlow<RouterTarget?>(null)
    val routerTarget: StateFlow<RouterTarget?> = _routerTarget.asStateFlow()

    private var pingJob: Job? = null

    fun refreshWifi() {
        _wifi.value = WifiInfoProvider.read(getApplication())
    }

    fun detectRouter() {
        if (_routerDetecting.value) return
        val status = _wifi.value
        // The AP's BSSID is the router's Wi-Fi MAC — the most reliable vendor hint,
        // since /proc/net/arp is often unreadable on Android 10+.
        val gatewayMac = _devices.value.firstOrNull { it.isGateway }?.mac ?: status.bssid
        _routerDetecting.value = true
        viewModelScope.launch {
            val info = RouterDetector.detect(getApplication(), status.gateway, gatewayMac)
            _router.value = info
            _routerDetecting.value = false
            _message.value = when {
                info.model != null -> "Detected ${info.vendor ?: "router"} ${info.model}"
                info.vendor != null -> "Detected ${info.vendor} router"
                else -> "Could not identify the router model."
            }
        }
    }

    /** Manually pick the router family when auto-detect can't (e.g. app-managed Deco mesh). */
    fun setRouterFamily(family: String, model: String? = null) {
        val current = _router.value
        val vendor = if (family == "generic") current.vendor else "TP-Link"
        _router.value = current.copy(
            vendor = vendor,
            model = model ?: current.model,
            family = family,
            detected = true,
        )
    }

    /** Ask the Router tab to open with this device queued for a block/throttle action. */
    fun manageOnRouter(device: NetDevice, action: RouterAction) {
        _routerTarget.value = RouterTarget(device.ip, device.mac, action)
    }

    fun consumeRouterTarget() {
        _routerTarget.value = null
    }

    data class RouterTarget(val ip: String, val mac: String?, val action: RouterAction)
    enum class RouterAction { BLOCK, THROTTLE }

    fun rescan() {
        if (_scanning.value) return
        refreshWifi()
        val status = _wifi.value
        if (status.subnet == null) {
            _message.value = "Connect to Wi-Fi first (no subnet detected)."
            return
        }
        _scanning.value = true
        _scanProgress.value = 0 to 254
        viewModelScope.launch {
            val found = scanner.scan(status) { done, total -> _scanProgress.value = done to total }
            val annotated = found.map { d ->
                d.copy(known = knownStore.isKnown(KnownDeviceStore.DeviceId(d.ip, d.mac)))
            }
            _devices.value = annotated
            _scanning.value = false
            val newcomers = annotated.count { !it.known && !it.isSelf }
            _message.value = "Found ${annotated.size} devices" +
                if (newcomers > 0) " ($newcomers new)" else ""
        }
    }

    fun scanPorts(device: NetDevice) {
        viewModelScope.launch {
            _message.value = "Scanning ports on ${device.ip}…"
            val open = PortScanner.scan(device.ip)
            _devices.update { list ->
                list.map { if (it.ip == device.ip) it.copy(openPorts = open) else it }
            }
            _message.value = if (open.isEmpty()) "No common ports open on ${device.ip}"
            else "${device.ip}: ${open.size} open port(s)"
        }
    }

    fun scanWifiAps() {
        viewModelScope.launch {
            val results = WifiScanner.scan(getApplication())
            _aps.value = results
            if (results.isEmpty()) _message.value = "No APs found (grant Location & enable Wi-Fi scanning)."
        }
    }

    fun runSpeedTest() {
        if (_speedRunning.value) return
        _speedRunning.value = true
        viewModelScope.launch {
            val result = runCatching { SpeedTest.run() }.getOrNull()
            _speed.value = result
            _speedRunning.value = false
            if (result == null) _message.value = "Speed test failed (network blocked?)."
        }
    }

    fun togglePing(host: String) {
        if (_pingRunning.value) {
            pingJob?.cancel()
            _pingRunning.value = false
            return
        }
        _pings.value = emptyList()
        _pingRunning.value = true
        pingJob = viewModelScope.launch {
            while (true) {
                val sample = Pinger.probe(host)
                _pings.update { (it + sample).takeLast(60) }
                delay(1000)
            }
        }
    }

    fun sendWol(mac: String) {
        viewModelScope.launch {
            val ok = WakeOnLan.send(mac)
            _message.value = if (ok) "Magic packet sent to $mac" else "Invalid MAC or send failed."
        }
    }

    fun markKnown(device: NetDevice) {
        knownStore.markKnown(KnownDeviceStore.DeviceId(device.ip, device.mac))
        _devices.update { list ->
            list.map { if (it.ip == device.ip) it.copy(known = true) else it }
        }
    }

    fun trustAllVisible() {
        knownStore.trustAll(_devices.value.map { KnownDeviceStore.DeviceId(it.ip, it.mac) })
        _devices.update { list -> list.map { it.copy(known = true) } }
        _message.value = "All current devices marked as trusted."
    }

    fun consumeMessage() {
        _message.value = null
    }
}
