package com.nettools.app.net

import android.content.Context
import com.nettools.app.data.NetDevice
import com.nettools.app.data.WifiStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress

/** Discovers hosts on the local /24 subnet via a parallel reachability sweep + ARP table. */
class NetworkScanner(private val context: Context) {

    /**
     * @param onProgress called with (scanned, total) as the sweep advances.
     */
    suspend fun scan(
        status: WifiStatus,
        resolveHostnames: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<NetDevice> = withContext(Dispatchers.IO) {
        val subnet = status.subnet ?: return@withContext emptyList()
        val self = status.ipAddress
        val gateway = status.gateway
        val total = 254
        val semaphore = Semaphore(64)
        var done = 0

        val liveIps = coroutineScope {
            (1..254).map { host ->
                async {
                    val ip = "$subnet.$host"
                    val reachable = semaphore.withPermit { probe(ip) }
                    synchronized(this@NetworkScanner) {
                        done++
                        onProgress(done, total)
                    }
                    if (reachable) ip else null
                }
            }.awaitAll().filterNotNull().toMutableSet()
        }

        // ARP cache picks up hosts that answered at L2 even if L3 probe was refused.
        val arp = readArpTable(subnet)
        liveIps.addAll(arp.keys)
        self?.let { liveIps.add(it) }
        gateway?.let { liveIps.add(it) }

        coroutineScope {
            liveIps.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }.map { ip ->
                async {
                    val mac = arp[ip]
                    val hostname = if (resolveHostnames) reverseDns(ip) else null
                    NetDevice(
                        ip = ip,
                        mac = mac,
                        hostname = hostname,
                        vendor = OuiLookup.lookup(context, mac),
                        reachable = true,
                        isGateway = ip == gateway,
                        isSelf = ip == self,
                    )
                }
            }.awaitAll()
        }
    }

    /** Probe a single host: ICMP-style reachability, then a few common TCP ports. */
    private fun probe(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            if (addr.isReachable(350)) return true
            // Fall back to TCP: an open OR refused port both prove the host exists.
            for (port in intArrayOf(80, 443, 445, 22, 53, 8080)) {
                if (tcpTouch(ip, port, 220)) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun tcpTouch(ip: String, port: Int, timeoutMs: Int): Boolean {
        java.net.Socket().use { socket ->
            return try {
                socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
                true // connected -> host up
            } catch (e: java.net.ConnectException) {
                true // refused -> host is up, port closed
            } catch (_: Exception) {
                false // timeout / unreachable
            }
        }
    }

    private fun reverseDns(ip: String): String? {
        return try {
            val name = InetAddress.getByName(ip).canonicalHostName
            if (name == ip) null else name
        } catch (_: Exception) {
            null
        }
    }

    /** Parse /proc/net/arp -> map of IP to MAC (only complete entries on a wlan interface). */
    private fun readArpTable(subnet: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        runCatching {
            val file = File("/proc/net/arp")
            if (!file.canRead()) return result
            file.readLines().drop(1).forEach { line ->
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 4) {
                    val ip = cols[0]
                    val flags = cols[2]
                    val mac = cols[3]
                    val complete = flags != "0x0"
                    val valid = mac.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) &&
                        mac != "00:00:00:00:00:00"
                    if (complete && valid && ip.startsWith("$subnet.")) {
                        result[ip] = mac.uppercase()
                    }
                }
            }
        }
        return result
    }
}
