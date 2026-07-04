package com.nettools.app.net

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Talks to a TP-Link Deco (and compatible "luci"-style firmware) over its local
 * encrypted admin API. Authentication uses the router's own admin password —
 * the same mechanism the Deco app uses — so this only controls the device you own.
 *
 * The crypto handshake (RSA key exchange + AES-CBC session + MD5 auth hash) mirrors
 * the documented Deco web protocol. Block/throttle endpoint names vary by firmware,
 * so [lastRaw] exposes the device's decrypted reply for troubleshooting.
 */
class DecoClient(host: String, private val username: String = "admin") {

    private val hostClean = host.removePrefix("http://").removePrefix("https://").trim('/')
    private var scheme = if (host.startsWith("https")) "https" else "http"

    private var stok: String = ""
    private var aesKey: String = ""
    private var aesIv: String = ""
    private var authHash: String = ""
    private var seq: Long = 0
    private var passwordKey: RSAPublicKey? = null
    private var authKey: RSAPublicKey? = null

    var lastRaw: String = ""
        private set

    data class DecoDevice(
        val mac: String,
        val name: String,
        val ip: String,
        val online: Boolean,
        val blocked: Boolean,
    )

    // ---- Public API ----------------------------------------------------------

    suspend fun login(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Try http first, then https (some Deco firmware forces TLS).
            var lastError: Exception? = null
            for (s in listOf(scheme, if (scheme == "http") "https" else "http")) {
                scheme = s
                try {
                    doLogin(password)
                    return@runCatching
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("Login failed")
        }
    }

    suspend fun listClients(): Result<List<DecoDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("operation", "read")
                .put("params", JSONObject().put("device_mac", "default"))
                .toString()
            val resp = authedPost("admin/client?form=client_list", body)
            parseClients(resp)
        }
    }

    suspend fun setBlocked(mac: String, name: String, blocked: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Deco "Access Control" blacklist toggle. Firmware-dependent; raw reply
                // is retained in [lastRaw] so the exact form can be confirmed if it differs.
                val params = JSONObject()
                    .put("key", "black_list")
                    .put("value", JSONObject().put("mac", mac).put("name", b64(name)))
                val body = JSONObject()
                    .put("operation", if (blocked) "add" else "remove")
                    .put("params", params)
                    .toString()
                authedPost("admin/access_control?form=black_list", body)
            }
        }

    suspend fun setThrottle(mac: String, downKbps: Int, upKbps: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("operation", "write")
                    .put(
                        "params",
                        JSONObject().put(
                            "client_list",
                            JSONArray().put(
                                JSONObject()
                                    .put("mac", mac)
                                    .put("down_limit", downKbps)
                                    .put("up_limit", upKbps)
                            )
                        )
                    )
                    .toString()
                authedPost("admin/client?form=client", body)
            }
        }

    // ---- Login handshake ------------------------------------------------------

    private fun doLogin(password: String) {
        passwordKey = fetchRsaKey("keys").first
        val (aKey, aSeq) = fetchRsaKey("auth")
        authKey = aKey
        seq = aSeq
        aesKey = randomDigits(16)
        aesIv = randomDigits(16)
        authHash = md5Hex(username + password)

        val encPassword = rsaEncryptHex(password.toByteArray(Charsets.UTF_8), passwordKey!!)
        val dataJson = JSONObject()
            .put("params", JSONObject().put("password", encPassword))
            .put("operation", "login")
            .toString()
        val encData = aesEncryptB64(dataJson)
        val signData = "key=$aesKey&iv=$aesIv&h=$authHash&s=${seq + encData.length}"
        val sign = rsaEncryptHex(signData.toByteArray(Charsets.UTF_8), authKey!!)

        val resp = post("cgi-bin/luci/;stok=/login?form=login", form("data" to encData, "sign" to sign))
        val decrypted = decryptResponse(resp)
        lastRaw = decrypted
        val obj = JSONObject(decrypted)
        stok = obj.optJSONObject("result")?.optString("stok").orEmpty()
        if (stok.isBlank()) {
            val code = obj.opt("error_code")
            throw IllegalStateException("Login rejected (error_code=$code). Check the admin password.")
        }
    }

    private fun fetchRsaKey(form: String): Pair<RSAPublicKey, Long> {
        val resp = post("cgi-bin/luci/;stok=/login?form=$form", form("data" to "{\"operation\":\"read\"}"))
        val obj = JSONObject(resp)
        val result = obj.optJSONObject("result")
            ?: throw IllegalStateException("Router did not return a $form key (not a Deco/luci device?).")
        val pw = result.getJSONArray("password")
        val key = rsaPublicKey(pw.getString(0), pw.getString(1))
        val s = result.optLong("seq", 0)
        return key to s
    }

    // ---- Authenticated request --------------------------------------------------

    private fun authedPost(path: String, jsonBody: String): String {
        val encData = aesEncryptB64(jsonBody)
        val signData = "h=$authHash&s=${seq + encData.length}"
        val sign = rsaEncryptHex(signData.toByteArray(Charsets.UTF_8), authKey!!)
        val resp = post("cgi-bin/luci/;stok=$stok/$path", form("data" to encData, "sign" to sign))
        val decrypted = decryptResponse(resp)
        lastRaw = decrypted
        return decrypted
    }

    private fun parseClients(decrypted: String): List<DecoDevice> {
        val obj = JSONObject(decrypted)
        val result = obj.optJSONObject("result") ?: return emptyList()
        val list = result.optJSONArray("client_list") ?: return emptyList()
        val out = ArrayList<DecoDevice>(list.length())
        for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            val rawName = c.optString("name").ifBlank { c.optString("client_name") }
            out.add(
                DecoDevice(
                    mac = c.optString("mac").uppercase(),
                    name = tryB64Decode(rawName).ifBlank { "(device)" },
                    ip = c.optString("ip"),
                    online = c.optBoolean("online", c.optInt("online", 0) == 1),
                    blocked = c.optBoolean("blocked", false) ||
                        c.optString("access_state").equals("blocked", ignoreCase = true),
                )
            )
        }
        return out
    }

    // ---- HTTP ------------------------------------------------------------------

    private fun post(path: String, body: String): String {
        val url = URL("$scheme://$hostClean/$path")
        val conn = url.openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = insecureSslFactory()
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 8000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Referer", "$scheme://$hostClean/")
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream.bufferedReader().use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code from router")
        return text
    }

    /** Response may be plaintext JSON, or {"data":"<b64>"}, or a bare base64 blob. */
    private fun decryptResponse(resp: String): String {
        val trimmed = resp.trim()
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull()
            val dataField = obj?.optString("data").orEmpty()
            if (dataField.isBlank()) return trimmed // already plaintext JSON
            return runCatching { aesDecrypt(dataField) }.getOrElse { trimmed }
        }
        return runCatching { aesDecrypt(trimmed) }.getOrElse { trimmed }
    }

    // ---- Crypto helpers --------------------------------------------------------

    private fun rsaPublicKey(nHex: String, eHex: String): RSAPublicKey {
        val spec = RSAPublicKeySpec(BigInteger(nHex, 16), BigInteger(eHex, 16))
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    private fun rsaEncryptHex(data: ByteArray, key: RSAPublicKey): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val blockSize = key.modulus.bitLength() / 8
        val max = blockSize - 11
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val len = minOf(max, data.size - i)
            val enc = cipher.doFinal(data, i, len)
            for (b in enc) sb.append("%02x".format(b))
            i += len
        }
        return sb.toString()
    }

    private fun aesEncryptB64(data: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(aesIv.toByteArray(Charsets.UTF_8)),
        )
        return Base64.encodeToString(cipher.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun aesDecrypt(b64: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(aesIv.toByteArray(Charsets.UTF_8)),
        )
        return String(cipher.doFinal(Base64.decode(b64, Base64.DEFAULT)), Charsets.UTF_8)
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun randomDigits(n: Int): String {
        val r = SecureRandom()
        return buildString { repeat(n) { append(r.nextInt(10)) } }
    }

    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun tryB64Decode(s: String): String = runCatching {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).takeIf { it.all { c -> c.code in 32..126 || c.code > 160 } } ?: s
    }.getOrDefault(s)

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) ->
            "$k=" + URLEncoder.encode(v, "UTF-8")
        }

    private fun insecureSslFactory() = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }), SecureRandom())
    }.socketFactory
}
