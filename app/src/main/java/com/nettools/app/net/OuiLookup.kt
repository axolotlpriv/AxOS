package com.nettools.app.net

import android.content.Context

/** Resolves a MAC address to a vendor name using a bundled OUI prefix table. */
object OuiLookup {
    private var table: Map<String, String>? = null

    private fun ensureLoaded(context: Context) {
        if (table != null) return
        val map = HashMap<String, String>(512)
        runCatching {
            context.assets.open("oui.txt").bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val comma = line.indexOf(',')
                    if (comma <= 0) continue
                    val prefix = line.substring(0, comma).uppercase().filter { it.isLetterOrDigit() }
                    val vendor = line.substring(comma + 1).trim()
                    if (prefix.length >= 6) map[prefix.substring(0, 6)] = vendor
                }
            }
        }
        table = map
    }

    fun lookup(context: Context, mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        ensureLoaded(context)
        val normalized = mac.uppercase().filter { it.isLetterOrDigit() }
        if (normalized.length < 6) return null
        return table?.get(normalized.substring(0, 6))
    }
}
