package com.kadaikutty.pos.core.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS for every backend connection.
 *
 * Phones fail to resolve the server for reasons that have nothing to do with the app: a strict
 * Private DNS setting, an ad-blocking DNS, a carrier or Wi-Fi resolver that drops queries, a VPN.
 * The result is "Unable to resolve host" on a phone that is otherwise online. So the system
 * resolver is tried first and, only when it fails, the name is looked up over HTTPS at a public
 * resolver reached by IP address (which needs no DNS itself). The last answer that worked is kept
 * as a final fallback, so a flaky resolver does not take a working shop offline.
 */
object ResilientDns : Dns {
    internal class Entry(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private val fresh = ConcurrentHashMap<String, Entry>()
    private val lastGood = ConcurrentHashMap<String, List<InetAddress>>()

    // Resolvers are dialled by IP, so this client is given no DNS of its own.
    private val resolverClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    internal val resolverUrls = listOf(
        "https://1.1.1.1/dns-query?type=A&name=",
        "https://8.8.8.8/resolve?type=A&name=",
        "https://1.0.0.1/dns-query?type=A&name=",
    )

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname).also { if (it.isNotEmpty()) lastGood[hostname] = it }
        } catch (systemFailure: UnknownHostException) {
            fresh[hostname]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.addresses }
            for (base in resolverUrls) {
                val answer = runCatching { queryOverHttps(base, hostname) }.getOrNull()
                if (answer != null && answer.addresses.isNotEmpty()) {
                    fresh[hostname] = answer
                    lastGood[hostname] = answer.addresses
                    return answer.addresses
                }
            }
            lastGood[hostname]?.let { return it }
            throw systemFailure
        }
    }

    internal fun queryOverHttps(baseUrl: String, hostname: String): Entry? {
        val request = Request.Builder()
            .url(baseUrl + hostname)
            .header("Accept", "application/dns-json")
            .get()
            .build()
        resolverClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val answers = JSONObject(response.body?.string().orEmpty()).optJSONArray("Answer") ?: return null
            val addresses = ArrayList<InetAddress>()
            var ttlSeconds = Long.MAX_VALUE
            for (i in 0 until answers.length()) {
                val record = answers.getJSONObject(i)
                if (record.optInt("type") != 1) continue // A records only; CNAME rows carry a host name
                val ip = record.optString("data")
                if (!IPV4.matches(ip)) continue
                // A literal address is parsed locally, never resolved.
                addresses += InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address)
                ttlSeconds = minOf(ttlSeconds, record.optLong("TTL", 60))
            }
            if (addresses.isEmpty()) return null
            val ttlMs = TimeUnit.SECONDS.toMillis(ttlSeconds.coerceIn(30, 300))
            return Entry(addresses, System.currentTimeMillis() + ttlMs)
        }
    }

    private val IPV4 = Regex("""(\d{1,3})(\.\d{1,3}){3}""")
}
