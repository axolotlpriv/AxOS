package com.nettools.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.nettools.app.data.WifiStatus

/** Reads the current Wi-Fi connection details. */
object WifiInfoProvider {

    @Suppress("DEPRECATION")
    fun read(context: Context): WifiStatus {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        val info: WifiInfo? = wifi?.connectionInfo
        val dhcp = wifi?.dhcpInfo

        val connected = info != null && info.networkId != -1 &&
            info.supplicantState?.name == "COMPLETED"

        var ip: String? = intToIp(dhcp?.ipAddress ?: 0)
        var gateway: String? = intToIp(dhcp?.gateway ?: 0)
        val netmask: String? = intToIp(dhcp?.netmask ?: 0)
        var dns = mutableListOf<String>()
        intToIp(dhcp?.dns1 ?: 0)?.let { if (it != "0.0.0.0") dns.add(it) }
        intToIp(dhcp?.dns2 ?: 0)?.let { if (it != "0.0.0.0") dns.add(it) }

        // Prefer modern LinkProperties for DNS / addresses when available.
        cm?.let { manager ->
            val active = manager.activeNetwork
            val lp: LinkProperties? = active?.let { manager.getLinkProperties(it) }
            lp?.let {
                val modernDns = it.dnsServers.mapNotNull { d -> d.hostAddress }
                    .filter { s -> s.contains('.') }
                if (modernDns.isNotEmpty()) dns = modernDns.toMutableList()
                val v4 = it.linkAddresses.firstOrNull { la ->
                    la.address.hostAddress?.contains('.') == true
                }
                v4?.address?.hostAddress?.let { addr -> if (addr != "0.0.0.0") ip = addr }
                it.routes.firstOrNull { r -> r.isDefaultRoute && r.gateway?.hostAddress?.contains('.') == true }
                    ?.gateway?.hostAddress?.let { g -> gateway = g }
            }
        }

        val ssidRaw = info?.ssid
        val ssid = ssidRaw?.trim('"')?.takeIf {
            it.isNotBlank() && it != "<unknown ssid>" && it != "0x"
        }

        val frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info?.frequency else null
        val subnet = ip?.let { it.substringBeforeLast('.', "") }.takeIf { !it.isNullOrEmpty() }

        return WifiStatus(
            connected = connected || (ip != null && ip != "0.0.0.0"),
            ssid = ssid,
            bssid = info?.bssid?.takeIf { it != "02:00:00:00:00:00" },
            ipAddress = ip?.takeIf { it != "0.0.0.0" },
            gateway = gateway?.takeIf { it != "0.0.0.0" },
            dns = dns.distinct(),
            netmask = netmask?.takeIf { it != "0.0.0.0" },
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            rssi = info?.rssi?.takeIf { it != -127 && it != 0 },
            frequencyMhz = frequency?.takeIf { it > 0 },
            subnet = subnet,
        )
    }

    private fun intToIp(value: Int): String? {
        if (value == 0) return null
        return (value and 0xFF).toString() + "." +
            ((value shr 8) and 0xFF) + "." +
            ((value shr 16) and 0xFF) + "." +
            ((value shr 24) and 0xFF)
    }
}
