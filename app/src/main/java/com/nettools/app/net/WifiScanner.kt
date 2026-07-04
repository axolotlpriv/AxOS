package com.nettools.app.net

import android.content.Context
import android.net.wifi.WifiManager
import com.nettools.app.data.AccessPoint

/** Scans for nearby Wi-Fi access points. */
object WifiScanner {

    @Suppress("DEPRECATION")
    fun scan(context: Context): List<AccessPoint> {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
        return try {
            wifi.startScan()
            wifi.scanResults.map { r ->
                val freq = r.frequency
                AccessPoint(
                    ssid = r.SSID?.takeIf { it.isNotBlank() } ?: "(hidden)",
                    bssid = r.BSSID ?: "",
                    rssi = r.level,
                    frequencyMhz = freq,
                    channel = frequencyToChannel(freq),
                    band = bandOf(freq),
                    security = securityOf(r.capabilities ?: ""),
                )
            }.sortedByDescending { it.rssi }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5000) / 5
        freq in 5955..7115 -> (freq - 5950) / 5
        else -> 0
    }

    private fun bandOf(freq: Int): String = when {
        freq in 2400..2500 -> "2.4 GHz"
        freq in 4900..5900 -> "5 GHz"
        freq in 5925..7125 -> "6 GHz"
        else -> "?"
    }

    private fun securityOf(caps: String): String = when {
        caps.contains("WPA3") || caps.contains("SAE") -> "WPA3"
        caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        else -> "Open"
    }
}
