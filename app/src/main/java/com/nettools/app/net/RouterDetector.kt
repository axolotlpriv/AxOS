package com.nettools.app.net

import android.content.Context
import com.nettools.app.data.RouterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

/**
 * Fingerprints the gateway to identify the router vendor / model.
 * Order of evidence: gateway MAC OUI (bundled table) -> HTTP admin-page probe
 * -> online MAC-vendor lookup as a fallback.
 */
object RouterDetector {

    private val MODEL_REGEX = Regex(
        "(Archer[ _-]?[A-Z]{1,4}\\d{1,4}[A-Z]?(?:\\s?[Vv]\\d)?" +
            "|Deco[ _-]?[A-Z]{0,3}\\d{1,4}[A-Z]?" +
            "|TL-[A-Z0-9]+" +
            "|\\b(?:AXE|AX|XE|PX)\\d{3,4}[A-Z]?\\b)"
    )

    // Deco mesh model shapes: "XE5300", "X60", "PX50", "M9", "P9", "S7", "Deco X20".
    private val DECO_MODEL = Regex("^(Deco[ _-]?)?(X|XE|PX|M|P|S|W)\\d{1,4}[A-Z]?$", RegexOption.IGNORE_CASE)

    suspend fun detect(context: Context, gateway: String?, gatewayMacHint: String?): RouterInfo =
        withContext(Dispatchers.IO) {
            val mac = gatewayMacHint ?: gateway?.let { resolveGatewayMac(it) }
            var vendor = OuiLookup.lookup(context, mac)
            var model: String? = null
            var server: String? = null
            var reached = false

            if (gateway != null) {
                for (base in listOf("http://$gateway/", "https://$gateway/")) {
                    val probe = runCatching { probe(base) }.getOrNull() ?: continue
                    reached = true
                    server = server ?: probe.server
                    val haystack = probe.body + " " + (probe.server ?: "") + " " + (probe.title ?: "")
                    if (vendor == null && TPLINK_MARKER.containsMatchIn(haystack)) vendor = "TP-Link"
                    if (model == null) {
                        model = MODEL_REGEX.find(haystack)?.value?.trim()?.replace(Regex("\\s+"), " ")
                    }
                    if (model != null) break
                }
            }

            // Online fallback: resolve the vendor from the MAC when the bundled table missed.
            if (vendor == null && mac != null) {
                vendor = queryMacVendor(mac)
            }

            val isTpLink = vendor?.contains("TP-Link", ignoreCase = true) == true ||
                vendor?.contains("TPLINK", ignoreCase = true) == true
            if (isTpLink) vendor = "TP-Link"

            RouterInfo(
                vendor = vendor,
                model = model,
                family = classifyFamily(model, isTpLink),
                server = server,
                adminUrl = gateway?.let { "http://$it" },
                detected = reached || vendor != null || mac != null,
            )
        }

    fun classifyFamily(model: String?, isTpLink: Boolean): String = when {
        model != null && (DECO_MODEL.matches(model) || model.contains("Deco", ignoreCase = true)) -> "deco"
        model?.startsWith("Archer", ignoreCase = true) == true -> "archer"
        model?.startsWith("TL-", ignoreCase = true) == true -> "legacy"
        isTpLink -> "tplink"
        else -> "generic"
    }

    private val TPLINK_MARKER = Regex("TP[- ]?LINK|Archer|TL-WR|TL-WDR|Deco", RegexOption.IGNORE_CASE)

    private data class Probe(val body: String, val server: String?, val title: String?)

    private fun probe(urlStr: String): Probe {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 2500
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NetTools")
        }
        val server = conn.getHeaderField("Server")
        val body = conn.inputStream.bufferedReader().use { it.readText().take(30000) }
        conn.disconnect()
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.trim()
        return Probe(body, server, title)
    }

    /** Ping the gateway to populate the ARP cache, then read its MAC from /proc/net/arp. */
    private fun resolveGatewayMac(gateway: String): String? {
        runCatching { InetAddress.getByName(gateway).isReachable(500) }
        runCatching {
            val file = File("/proc/net/arp")
            if (!file.canRead()) return null
            file.readLines().drop(1).forEach { line ->
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 4 && cols[0] == gateway) {
                    val macAddr = cols[3]
                    if (macAddr.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) &&
                        macAddr != "00:00:00:00:00:00"
                    ) {
                        return macAddr.uppercase()
                    }
                }
            }
        }
        return null
    }

    /** Free MAC-vendor API used only when the bundled OUI table has no match. */
    private fun queryMacVendor(mac: String): String? {
        return runCatching {
            val q = URLEncoder.encode(mac, "UTF-8")
            val conn = (URL("https://api.macvendors.com/$q").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "NetTools")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { it.isNotBlank() }
            } else {
                conn.disconnect(); null
            }
        }.getOrNull()
    }
}
