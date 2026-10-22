package com.mahirex.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

/**
 * Official (marketing) device name, e.g. "Samsung Galaxy A14 4G" for SM-A145P. Never invented:
 *  a) the phone's own marketing-name properties,
 *  b) a small built-in table keyed by Build.MODEL,
 *  c) Google's public Google Play supported-devices list (downloaded once, then cached in Prefs).
 * [official] needs no network and no UI work, so widgets can use it. [resolve] adds step c on a worker thread.
 */
object DeviceNames {
    private const val CSV_URL = "https://storage.googleapis.com/play_public/supported_devices.csv"
    private const val MISS_RETRY_MS = 7L * 24 * 3600 * 1000
    private const val DEADLINE_MS = 25000L

    private val MODEL: String get() = Build.MODEL ?: ""

    @Volatile
    private var localDone = false

    @Volatile
    private var local: String? = null
    private val fetching = AtomicBoolean(false)

    // Samsung model code -> official name. Looked up by the full model first, then by its first 7 characters
    // (e.g. SM-A145F / SM-A145M / SM-A145P are all the same product "SM-A145").
    private val TABLE: Map<String, String> = hashMapOf(
        "SM-A145P" to "Samsung Galaxy A14 4G",
        "SM-A145" to "Samsung Galaxy A14 4G", "SM-A146" to "Samsung Galaxy A14 5G",
        "SM-A135" to "Samsung Galaxy A13", "SM-A136" to "Samsung Galaxy A13 5G",
        "SM-A125" to "Samsung Galaxy A12", "SM-A127" to "Samsung Galaxy A12",
        "SM-A042" to "Samsung Galaxy A04e", "SM-A045" to "Samsung Galaxy A04", "SM-A047" to "Samsung Galaxy A04s",
        "SM-A055" to "Samsung Galaxy A05", "SM-A057" to "Samsung Galaxy A05s",
        "SM-A155" to "Samsung Galaxy A15 4G", "SM-A156" to "Samsung Galaxy A15 5G",
        "SM-A165" to "Samsung Galaxy A16 4G", "SM-A166" to "Samsung Galaxy A16 5G",
        "SM-A225" to "Samsung Galaxy A22", "SM-A226" to "Samsung Galaxy A22 5G",
        "SM-A235" to "Samsung Galaxy A23", "SM-A236" to "Samsung Galaxy A23 5G",
        "SM-A245" to "Samsung Galaxy A24",
        "SM-A256" to "Samsung Galaxy A25 5G", "SM-A266" to "Samsung Galaxy A26 5G",
        "SM-A325" to "Samsung Galaxy A32", "SM-A326" to "Samsung Galaxy A32 5G",
        "SM-A336" to "Samsung Galaxy A33 5G", "SM-A346" to "Samsung Galaxy A34 5G",
        "SM-A356" to "Samsung Galaxy A35 5G", "SM-A366" to "Samsung Galaxy A36 5G",
        "SM-A525" to "Samsung Galaxy A52", "SM-A526" to "Samsung Galaxy A52 5G", "SM-A528" to "Samsung Galaxy A52s 5G",
        "SM-A536" to "Samsung Galaxy A53 5G", "SM-A546" to "Samsung Galaxy A54 5G",
        "SM-A556" to "Samsung Galaxy A55 5G", "SM-A566" to "Samsung Galaxy A56 5G",
        "SM-A725" to "Samsung Galaxy A72", "SM-A736" to "Samsung Galaxy A73 5G",
        "SM-A107" to "Samsung Galaxy A10s", "SM-A205" to "Samsung Galaxy A20", "SM-A207" to "Samsung Galaxy A20s",
        "SM-A217" to "Samsung Galaxy A21s", "SM-A305" to "Samsung Galaxy A30", "SM-A307" to "Samsung Galaxy A30s",
        "SM-A315" to "Samsung Galaxy A31", "SM-A505" to "Samsung Galaxy A50", "SM-A515" to "Samsung Galaxy A51",
        "SM-A715" to "Samsung Galaxy A71",
        "SM-M127" to "Samsung Galaxy M12", "SM-M135" to "Samsung Galaxy M13", "SM-M146" to "Samsung Galaxy M14 5G",
        "SM-M215" to "Samsung Galaxy M21", "SM-M236" to "Samsung Galaxy M23 5G", "SM-M315" to "Samsung Galaxy M31",
        "SM-M325" to "Samsung Galaxy M32", "SM-M326" to "Samsung Galaxy M32 5G", "SM-M336" to "Samsung Galaxy M33 5G",
        "SM-M346" to "Samsung Galaxy M34 5G", "SM-M515" to "Samsung Galaxy M51", "SM-M546" to "Samsung Galaxy M54 5G",
        "SM-G950" to "Samsung Galaxy S8", "SM-G955" to "Samsung Galaxy S8+",
        "SM-G960" to "Samsung Galaxy S9", "SM-G965" to "Samsung Galaxy S9+",
        "SM-G970" to "Samsung Galaxy S10e", "SM-G973" to "Samsung Galaxy S10", "SM-G975" to "Samsung Galaxy S10+",
        "SM-G980" to "Samsung Galaxy S20", "SM-G985" to "Samsung Galaxy S20+", "SM-G988" to "Samsung Galaxy S20 Ultra",
        "SM-G780" to "Samsung Galaxy S20 FE", "SM-G781" to "Samsung Galaxy S20 FE 5G",
        "SM-G990" to "Samsung Galaxy S21 FE 5G", "SM-G991" to "Samsung Galaxy S21 5G",
        "SM-G996" to "Samsung Galaxy S21+ 5G", "SM-G998" to "Samsung Galaxy S21 Ultra 5G",
        "SM-S901" to "Samsung Galaxy S22", "SM-S906" to "Samsung Galaxy S22+", "SM-S908" to "Samsung Galaxy S22 Ultra",
        "SM-S911" to "Samsung Galaxy S23", "SM-S916" to "Samsung Galaxy S23+", "SM-S918" to "Samsung Galaxy S23 Ultra",
        "SM-S711" to "Samsung Galaxy S23 FE",
        "SM-S921" to "Samsung Galaxy S24", "SM-S926" to "Samsung Galaxy S24+", "SM-S928" to "Samsung Galaxy S24 Ultra",
        "SM-S721" to "Samsung Galaxy S24 FE",
        "SM-S931" to "Samsung Galaxy S25", "SM-S936" to "Samsung Galaxy S25+", "SM-S938" to "Samsung Galaxy S25 Ultra",
        "SM-N950" to "Samsung Galaxy Note8", "SM-N960" to "Samsung Galaxy Note9",
        "SM-N970" to "Samsung Galaxy Note10", "SM-N975" to "Samsung Galaxy Note10+",
        "SM-F711" to "Samsung Galaxy Z Flip3 5G", "SM-F721" to "Samsung Galaxy Z Flip4",
        "SM-F731" to "Samsung Galaxy Z Flip5", "SM-F741" to "Samsung Galaxy Z Flip6",
        "SM-F926" to "Samsung Galaxy Z Fold3 5G", "SM-F936" to "Samsung Galaxy Z Fold4",
        "SM-F946" to "Samsung Galaxy Z Fold5", "SM-F956" to "Samsung Galaxy Z Fold6"
    )

    private fun prop(key: String): String? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val r = p.inputStream.bufferedReader().readText().trim()
        if (r.isEmpty() || r.equals("unknown", true)) null else r
    } catch (e: Throwable) {
        null
    }

    private fun clean(v: String?): String? {
        val t = v?.trim() ?: return null
        if (t.length < 2 || t.length > 60 || t.any { it.isISOControl() } || t.equals(MODEL, true)) return null
        return t
    }

    /** steps a) and b); no network. Cached per process because reading properties starts a tiny process. */
    private fun localName(): String? {
        if (localDone) return local
        var r: String? = null
        for (k in listOf("ro.product.marketname", "ro.config.marketing_name", "ro.product.vendor.marketname")) {
            r = clean(prop(k))
            if (r != null) break
        }
        if (r == null) {
            val m = MODEL.trim().uppercase(Locale.US)
            r = clean(TABLE[m] ?: if (m.startsWith("SM-") && m.length >= 7) TABLE[m.substring(0, 7)] else null)
        }
        local = r
        localDone = true
        return r
    }

    private fun cacheKey(): String = "dn_" + MODEL

    /** official name from the phone, the built-in table or the cache of an earlier online lookup; null when unknown. No network. */
    fun official(ctx: Context): String? = localName() ?: clean(Prefs.getStr(cacheKey()))

    /**
     * Same as [official] and, when still unknown and the phone is online, looks the model up once in Google's public
     * Play supported-devices list. Runs on a worker thread; [onDone] is called on the main thread (null = unknown).
     */
    fun resolve(ctx: Context, onDone: (String?) -> Unit) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            var name: String? = null
            try {
                name = official(app)
                if (name == null && Net.isOnline(app)) name = lookupOnline(app)
            } catch (e: Throwable) {
                // an unresolved name simply keeps the row hidden
            }
            main.post {
                try {
                    onDone(name)
                } catch (e: Throwable) {
                    // the screen may be gone by now
                }
            }
        }.start()
    }

    private fun lookupOnline(ctx: Context): String? {
        val missAt = Prefs.getLong("dn_miss_" + MODEL, 0L)
        if (missAt > 0 && System.currentTimeMillis() - missAt < MISS_RETRY_MS) return null
        if (!fetching.compareAndSet(false, true)) return null
        try {
            val found = searchCsv(MODEL) ?: return null
            if (found.isEmpty()) {
                Prefs.putLong("dn_miss_" + MODEL, System.currentTimeMillis())
                return null
            }
            val name = clean(found) ?: return null
            Prefs.putStr(cacheKey(), name)
            return name
        } finally {
            fetching.set(false)
        }
    }

    /**
     * Streams Google's list (UTF-16 CSV: Retail Branding, Marketing Name, Device, Model) and stops at the first row of [model].
     * Returns the name, "" when the whole list was read without a match, or null when the download failed.
     */
    private fun searchCsv(model: String): String? {
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = Net.open(CSV_URL, "GET", 6000, 8000)
            if (conn.responseCode != 200) return null
            val end = System.currentTimeMillis() + DEADLINE_MS
            conn.inputStream.bufferedReader(Charsets.UTF_16).use { r ->
                while (true) {
                    if (System.currentTimeMillis() > end) return null
                    val line = r.readLine() ?: break
                    val f = splitCsv(line)
                    if (f.size < 4 || !f[3].trim().equals(model, true)) continue
                    val market = f[1].trim()
                    if (market.isEmpty() || market.equals(model, true)) continue
                    val brand = f[0].trim()
                    val b = if (brand == brand.lowercase(Locale.US)) brand.replaceFirstChar { it.uppercase() } else brand
                    return if (b.isEmpty() || market.startsWith(b, true)) market else "$b $market"
                }
            }
            return ""
        } catch (e: Throwable) {
            return null
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(5)
        val sb = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (q) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else q = false
                } else sb.append(ch)
            } else when (ch) {
                '"' -> q = true
                ',' -> {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
