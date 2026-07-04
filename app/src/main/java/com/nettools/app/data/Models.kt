package com.nettools.app.data

/** A device discovered on the local network. */
data class NetDevice(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val vendor: String? = null,
    val reachable: Boolean = true,
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    val latencyMs: Long? = null,
    val openPorts: List<Int> = emptyList(),
    val known: Boolean = false,
) {
    val ipLastOctet: Int
        get() = ip.substringAfterLast('.').toIntOrNull() ?: 0
}

/** Snapshot of the current Wi-Fi connection. */
data class WifiStatus(
    val connected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val ipAddress: String? = null,
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
    val netmask: String? = null,
    val linkSpeedMbps: Int? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
    val subnet: String? = null,
)

/** A nearby Wi-Fi access point from a scan. */
data class AccessPoint(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val security: String,
)

data class SpeedResult(
    val downMbps: Double,
    val bytes: Long,
    val seconds: Double,
)

data class PingSample(
    val timestamp: Long,
    val latencyMs: Long?, // null = timeout / unreachable
)

/** Result of fingerprinting the gateway/router. */
data class RouterInfo(
    val vendor: String? = null,
    val model: String? = null,
    val family: String = "generic", // archer | legacy | deco | tplink | generic
    val server: String? = null,
    val adminUrl: String? = null,
    val detected: Boolean = false,
) {
    val isTpLink: Boolean get() = vendor?.contains("TP-Link", ignoreCase = true) == true
}
