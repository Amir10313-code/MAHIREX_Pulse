package com.mahirex.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// ====================================================================== models

enum class SrvType { CLOUDFLARE, LIBRESPEED, STATIC }

/**
 * One speed test endpoint. Everything the engine needs is a plain URL, so new servers are just data
 * (assets/servers.json or the cached remote list) - no code change is needed to add one.
 *  - CLOUDFLARE : download + upload + ping on the same host
 *  - LIBRESPEED : open-source LibreSpeed backend (garbage / empty endpoints), download + upload + ping
 *  - STATIC     : plain HTTPS file, download + ping only (upload is measured on an upload-capable server)
 */
class SpeedServer(
    val id: String,
    val name: String,
    val city: String,
    val country: String,
    val type: SrvType,
    val pingUrl: String,
    val downUrl: String,
    val upUrl: String?,
    val json: JSONObject
) {
    val label: String get() = if (city.isEmpty()) name else "$name · $city"
    val place: String get() = if (city.isEmpty()) country else "$city, $country"
    val canUpload: Boolean get() = upUrl != null
}

class SpeedResult(
    val time: Long,
    val down: Double,
    val up: Double,       // < 0 when upload could not be measured
    val ping: Double,
    val jitter: Double,
    val server: String,
    val country: String,
    val net: Int,
    val upServer: String  // "" when upload ran on the same server
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("t", time)
        o.put("d", down)
        o.put("u", up)
        o.put("p", ping)
        o.put("j", jitter)
        o.put("s", server)
        o.put("c", country)
        o.put("n", net)
        o.put("us", upServer)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): SpeedResult = SpeedResult(
            o.optLong("t"), o.optDouble("d", 0.0), o.optDouble("u", -1.0), o.optDouble("p", 0.0),
            o.optDouble("j", 0.0), o.optString("s"), o.optString("c"), o.optInt("n"), o.optString("us")
        )
    }
}

// ====================================================================== local storage (history + selected server + cached list)

object SpeedStore {
    private const val FILE = "mahirex_speed"
    private const val MAX_HISTORY = 60

    private fun sp(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun history(ctx: Context): List<SpeedResult> {
        val raw = sp(ctx).getString("history", null) ?: return emptyList()
        val out = ArrayList<SpeedResult>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) out.add(SpeedResult.fromJson(a.getJSONObject(i)))
        } catch (e: Exception) {
            // corrupted data is dropped instead of crashing
        }
        return out
    }

    fun last(ctx: Context): SpeedResult? = history(ctx).firstOrNull()

    @Synchronized
    fun add(ctx: Context, r: SpeedResult) {
        val list = ArrayList<SpeedResult>()
        list.add(r)
        list.addAll(history(ctx))
        val a = JSONArray()
        for (i in 0 until Math.min(list.size, MAX_HISTORY)) a.put(list[i].toJson())
        sp(ctx).edit().putString("history", a.toString()).apply()
    }

    @Synchronized
    fun clear(ctx: Context) {
        sp(ctx).edit().remove("history").apply()
    }

    /** "" = automatic (lowest ping) */
    fun selectedId(ctx: Context): String = sp(ctx).getString("server", "") ?: ""

    fun setSelectedId(ctx: Context, id: String) {
        sp(ctx).edit().putString("server", id).apply()
    }

    fun remoteJson(ctx: Context): String? = sp(ctx).getString("remote_list", null)

    fun setRemoteJson(ctx: Context, json: String) {
        sp(ctx).edit().putString("remote_list", json).putLong("remote_ts", System.currentTimeMillis()).apply()
    }
}

// ====================================================================== server list + latency checks

object ServerManager {
    private const val CF_PING = "https://speed.cloudflare.com/__down?bytes=0"
    private const val CF_DOWN = "https://speed.cloudflare.com/__down?bytes=50000000"
    private const val CF_UP = "https://speed.cloudflare.com/__up"
    private const val PING_TTL_MS = 10 * 60 * 1000L

    // The public LibreSpeed directory. Used only when the user taps "Update list".
    private val DIRECTORY_URLS = arrayOf(
        "https://librespeed.org/backend-servers/servers.php",
        "https://librespeed.org/backend-servers/servers_list.json"
    )
    private val WANTED = arrayOf(
        "germany", "france", "netherlands", "turkey", "türkiye", "turkiye",
        "united arab emirates", "uae", "dubai", "frankfurt", "amsterdam", "paris", "istanbul"
    )

    private class Ping(val at: Long, val ms: Double?)

    private val pings = ConcurrentHashMap<String, Ping>()

    // ---- parsing (shared by the built-in file and the cached directory)
    fun parse(o: JSONObject): SpeedServer? {
        return try {
            val id = o.getString("id")
            val name = o.optString("name", id)
            val city = o.optString("city", "")
            val country = o.optString("country", "")
            when (o.optString("type", "static").lowercase()) {
                "cloudflare" -> SpeedServer(id, name, city, country, SrvType.CLOUDFLARE, CF_PING, CF_DOWN, CF_UP, o)
                "librespeed" -> {
                    val base = o.getString("base").let { if (it.endsWith("/")) it else "$it/" }
                    if (!base.startsWith("https://")) return null
                    SpeedServer(
                        id, name, city, country, SrvType.LIBRESPEED,
                        base + o.optString("ping", "empty.php"),
                        base + o.optString("dl", "garbage.php"),
                        base + o.optString("ul", "empty.php"), o
                    )
                }
                else -> {
                    val url = o.getString("url")
                    if (!url.startsWith("https://")) return null
                    SpeedServer(id, name, city, country, SrvType.STATIC, url, url, null, o)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseArray(a: JSONArray?, out: MutableList<SpeedServer>) {
        if (a == null) return
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val s = parse(o) ?: continue
            if (out.none { it.id == s.id }) out.add(s)
        }
    }

    /** built-in servers first, then the ones from the last list update */
    fun load(ctx: Context): List<SpeedServer> {
        val out = ArrayList<SpeedServer>()
        try {
            val txt = ctx.assets.open("servers.json").bufferedReader().use { it.readText() }
            parseArray(JSONObject(txt).optJSONArray("servers"), out)
        } catch (e: Exception) {
            // keep going with whatever could be read
        }
        try {
            val raw = SpeedStore.remoteJson(ctx)
            if (raw != null) parseArray(JSONArray(raw), out)
        } catch (e: Exception) {
            // ignore a damaged cache
        }
        return out
    }

    /**
     * Downloads the public LibreSpeed directory and keeps a few HTTPS servers in the regions we care about.
     * Blocking - call from a worker thread. Returns the number of servers that were added.
     */
    fun refreshRemote(ctx: Context): Int {
        var lastError: Exception? = null
        for (u in DIRECTORY_URLS) {
            try {
                val c = Net.open(u, "GET", 6000, 10000)
                try {
                    if (c.responseCode !in 200..299) continue
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    val picked = JSONArray()
                    val perCountry = HashMap<String, Int>()
                    val have = load(ctx).map { it.id }.toMutableSet()
                    var added = 0
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        val name = e.optString("name")
                        val lower = name.lowercase()
                        if (WANTED.none { lower.contains(it) }) continue
                        var host = e.optString("server")
                        if (host.startsWith("//")) host = "https:$host"
                        if (!host.startsWith("https://")) continue
                        val id = "ls-" + e.optString("id", name.hashCode().toString())
                        val head = name.substringBefore("(")
                        val parts = head.split(",")
                        val city = parts[0].trim()
                        val country = if (parts.size > 1) parts[1].trim() else ""
                        val n = perCountry.getOrDefault(country, 0)
                        if (n >= 2) continue
                        val o = JSONObject()
                        o.put("id", id)
                        o.put("type", "librespeed")
                        o.put("name", "LibreSpeed")
                        o.put("city", city)
                        o.put("country", country)
                        o.put("base", host)
                        o.put("dl", e.optString("dlURL", "garbage.php"))
                        o.put("ul", e.optString("ulURL", "empty.php"))
                        o.put("ping", e.optString("pingURL", "empty.php"))
                        if (parse(o) == null) continue
                        perCountry[country] = n + 1
                        picked.put(o)
                        if (have.add(id)) added++
                        if (picked.length() >= 10) break
                    }
                    SpeedStore.setRemoteJson(ctx, picked.toString())
                    return added
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: java.io.IOException("directory unavailable")
    }

    // ---- latency
    fun cachedPing(id: String): Double? {
        val p = pings[id] ?: return null
        return if (System.currentTimeMillis() - p.at < PING_TTL_MS) p.ms else null
    }

    /** true when this server was already checked recently (online or offline) */
    fun isChecked(id: String): Boolean {
        val p = pings[id] ?: return false
        return System.currentTimeMillis() - p.at < PING_TTL_MS
    }

    fun clearCache() {
        pings.clear()
    }

    fun measure(s: SpeedServer, samples: Int): Double? {
        val list = ArrayList<Double>()
        Net.pingOnce(s) // first request pays for DNS + TCP + TLS and is not counted
        for (i in 0 until samples) {
            val v = Net.pingOnce(s)
            if (v != null) list.add(v)
        }
        val ms = if (list.isEmpty()) null else list.min()
        pings[s.id] = Ping(System.currentTimeMillis(), ms)
        return ms
    }

    /**
     * Checks all servers (4 at a time). Recently checked servers are not contacted again unless [force].
     * Blocking; [onEach] is called from worker threads.
     */
    fun probeAll(servers: List<SpeedServer>, force: Boolean, cancelled: () -> Boolean, onEach: (SpeedServer, Double?) -> Unit) {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            for (s in servers) {
                pool.execute {
                    if (cancelled()) return@execute
                    if (!force && isChecked(s.id)) {
                        onEach(s, cachedPing(s.id))
                    } else {
                        val ms = try {
                            measure(s, 2)
                        } catch (e: Exception) {
                            null
                        }
                        if (!cancelled()) onEach(s, ms)
                    }
                }
            }
        } finally {
            pool.shutdown()
        }
        try {
            pool.awaitTermination(40, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            pool.shutdownNow()
        }
    }

    /** server with the lowest recent ping among [servers], or null when none answered */
    fun best(servers: List<SpeedServer>): SpeedServer? {
        var bs: SpeedServer? = null
        var bp = Double.MAX_VALUE
        for (s in servers) {
            val p = cachedPing(s.id) ?: continue
            if (p < bp) {
                bp = p
                bs = s
            }
        }
        return bs
    }
}
