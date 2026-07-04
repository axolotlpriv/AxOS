package com.nettools.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** TCP connect scanner for a single host. */
object PortScanner {

    /** Common service ports scanned by default. */
    val COMMON_PORTS = listOf(
        21, 22, 23, 25, 53, 80, 110, 139, 143, 443, 445, 515, 548, 554,
        631, 993, 995, 1723, 1883, 3306, 3389, 5000, 5060, 5353, 5900,
        6379, 7070, 8000, 8080, 8081, 8443, 8883, 9000, 9100, 32400
    )

    val PORT_NAMES = mapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 139 to "NetBIOS", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 515 to "Printer", 548 to "AFP",
        554 to "RTSP", 631 to "IPP", 993 to "IMAPS", 995 to "POP3S",
        1723 to "PPTP", 1883 to "MQTT", 3306 to "MySQL", 3389 to "RDP",
        5000 to "UPnP", 5060 to "SIP", 5353 to "mDNS", 5900 to "VNC",
        6379 to "Redis", 7070 to "RTSP", 8000 to "HTTP-alt", 8080 to "HTTP-alt",
        8081 to "HTTP-alt", 8443 to "HTTPS-alt", 8883 to "MQTTS",
        9000 to "HTTP-alt", 9100 to "JetDirect", 32400 to "Plex"
    )

    suspend fun scan(
        ip: String,
        ports: List<Int> = COMMON_PORTS,
        timeoutMs: Int = 600,
    ): List<Int> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(48)
        coroutineScope {
            ports.map { port ->
                async {
                    val open = semaphore.withPermit { isOpen(ip, port, timeoutMs) }
                    if (open) port else null
                }
            }.awaitAll().filterNotNull().sorted()
        }
    }

    private fun isOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        Socket().use { socket ->
            return try {
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
