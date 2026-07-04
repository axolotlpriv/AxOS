package com.nettools.app.net

import com.nettools.app.data.PingSample
import com.nettools.app.data.SpeedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** Internet download throughput test. */
object SpeedTest {
    // Cloudflare's public speed endpoint returns arbitrary-sized payloads.
    private const val URL_TEMPLATE = "https://speed.cloudflare.com/__down?bytes=%d"

    suspend fun run(bytes: Long = 20_000_000): SpeedResult = withContext(Dispatchers.IO) {
        val url = URL(URL_TEMPLATE.format(bytes))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        val start = System.nanoTime()
        var total = 0L
        conn.inputStream.use { stream ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
            }
        }
        conn.disconnect()
        val seconds = (System.nanoTime() - start) / 1_000_000_000.0
        val mbps = if (seconds > 0) (total * 8.0) / (seconds * 1_000_000.0) else 0.0
        SpeedResult(downMbps = mbps, bytes = total, seconds = seconds)
    }
}

/** Latency probe using a TCP connect timing (works without root, unlike raw ICMP). */
object Pinger {
    suspend fun probe(host: String, port: Int = 443, timeoutMs: Int = 1500): PingSample =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val start = System.nanoTime()
            val latency: Long? = try {
                Socket().use { s -> s.connect(InetSocketAddress(host, port), timeoutMs) }
                (System.nanoTime() - start) / 1_000_000
            } catch (e: java.net.ConnectException) {
                // Connection refused: the round-trip still completed, so time it.
                (System.nanoTime() - start) / 1_000_000
            } catch (_: Exception) {
                // Timeout / unreachable: fall back to ICMP-style reachability once.
                runCatching {
                    val addr = InetAddress.getByName(host)
                    val s2 = System.nanoTime()
                    if (addr.isReachable(timeoutMs)) (System.nanoTime() - s2) / 1_000_000 else null
                }.getOrNull()
            }
            PingSample(timestamp = now, latencyMs = latency)
        }
}

/** Wake-on-LAN magic packet sender (for waking your own machines). */
object WakeOnLan {
    suspend fun send(mac: String, broadcast: String = "255.255.255.255", port: Int = 9): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val macBytes = parseMac(mac) ?: return@withContext false
                val payload = ByteArray(6 + 16 * 6)
                for (i in 0 until 6) payload[i] = 0xFF.toByte()
                for (i in 0 until 16) System.arraycopy(macBytes, 0, payload, 6 + i * 6, 6)
                val address = InetAddress.getByName(broadcast)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(DatagramPacket(payload, payload.size, address, port))
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun parseMac(mac: String): ByteArray? {
        val hex = mac.split(':', '-').mapNotNull { it.trim().takeIf { s -> s.length == 2 } }
        if (hex.size != 6) return null
        return try {
            ByteArray(6) { hex[it].toInt(16).toByte() }
        } catch (_: Exception) {
            null
        }
    }
}
