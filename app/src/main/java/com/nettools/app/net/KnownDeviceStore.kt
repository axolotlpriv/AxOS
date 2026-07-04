package com.nettools.app.net

import android.content.Context

/** Persists the set of "known" devices so newcomers can be flagged as intruders. */
class KnownDeviceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("known_devices", Context.MODE_PRIVATE)

    private fun key(device: DeviceId): String = device.mac ?: "ip:${device.ip}"

    fun isKnown(device: DeviceId): Boolean = prefs.contains(key(device))

    fun markKnown(device: DeviceId) {
        prefs.edit().putLong(key(device), System.currentTimeMillis()).apply()
    }

    fun forget(device: DeviceId) {
        prefs.edit().remove(key(device)).apply()
    }

    fun trustAll(devices: List<DeviceId>) {
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        devices.forEach { editor.putLong(key(it), now) }
        editor.apply()
    }

    fun clear() = prefs.edit().clear().apply()

    data class DeviceId(val ip: String, val mac: String?)
}
