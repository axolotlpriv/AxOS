package com.nettools.app.net

import android.content.Context
import com.nettools.app.data.RouterInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fingerprints the gateway to identify the router vendor / model.
 * Combines the gateway MAC's OUI vendor with an HTTP probe of the admin page
 * (Server header, <title>, and page markers).
 */
object RouterDetector {

    private val MODEL_REGEX = Regex(
        "(Archer[ _-]?[A-Z]{1,3}\\d{1,4}[A-Z]?(?:\\s?[Vv]\\d)?|TL-[A-Z0-9]+|Deco[ _-]?[A-Z0-9]+)"
    )

    suspend fun detect(context: Context, gateway: String?, gatewayMac: String?): RouterInfo =
        withContext(Dispatchers.IO) {
            var vendor = OuiLookup.lookup(context, gatewayMac)
            var model: String? = null
            var server: String? = null
            var reached = false

            if (gateway != null) {
                runCatching {
                    val conn = (URL("http://$gateway/").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2500
                        readTimeout = 2500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "NetTools")
                    }
                    server = conn.getHeaderField("Server")
                    val body = conn.inputStream.bufferedReader().use { it.readText().take(30000) }
                    conn.disconnect()
                    reached = true

                    val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                        .find(body)?.groupValues?.getOrNull(1)?.trim()
                    val haystack = (body + " " + (server ?: "") + " " + (title ?: ""))

                    if (vendor == null && Regex("TP[- ]?LINK|Archer|TL-WR|TL-WDR|Deco", RegexOption.IGNORE_CASE)
                            .containsMatchIn(haystack)
                    ) {
                        vendor = "TP-Link"
                    }
                    model = MODEL_REGEX.find(haystack)?.value?.trim()
                        ?.replace(Regex("\\s+"), " ")
                }
            }

            val isTpLink = vendor?.contains("TP-Link", ignoreCase = true) == true
            val family = when {
                model?.contains("Deco", ignoreCase = true) == true -> "deco"
                model?.startsWith("Archer", ignoreCase = true) == true -> "archer"
                model?.startsWith("TL-", ignoreCase = true) == true -> "legacy"
                isTpLink -> "tplink"
                else -> "generic"
            }

            RouterInfo(
                vendor = vendor,
                model = model,
                family = family,
                server = server,
                adminUrl = gateway?.let { "http://$it" },
                detected = reached || vendor != null,
            )
        }
}
