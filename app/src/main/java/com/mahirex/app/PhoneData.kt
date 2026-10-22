package com.mahirex.app

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/** Real, read-only device data used by the phone health screens. Nothing here is estimated or invented. */
object Dev {
    class Storage(val total: Long, val free: Long) {
        val used: Long get() = Math.max(0L, total - free)
        val usedPct: Float get() = if (total > 0) used * 100f / total else 0f
        val freePct: Float get() = if (total > 0) free * 100f / total else 0f
    }

    class Battery(
        val pct: Int, val status: Int, val plugged: Int, val tempC: Float?, val voltageMv: Int?,
        val health: Int, val tech: String?, val cycles: Int?
    ) {
        val charging: Boolean get() = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun storage(ctx: Context): Storage {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                if (total > 0) return Storage(total, free)
            } catch (e: Exception) {
                // fall back to StatFs below
            }
        }
        return try {
            val s = StatFs(Environment.getDataDirectory().path)
            Storage(s.totalBytes, s.availableBytes)
        } catch (e: Exception) {
            Storage(0, 0)
        }
    }

    /** free bytes on the user data partition right now (used for the before / after comparison) */
    fun freeNow(): Long = try {
        StatFs(Environment.getDataDirectory().path).availableBytes
    } catch (e: Exception) {
        -1L
    }

    fun ram(ctx: Context): ActivityManager.MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        try {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        } catch (e: Exception) {
            // leave zeros
        }
        return mi
    }

    /** proportional memory of this app in bytes (what the app really holds), or -1 */
    fun ownPss(ctx: Context): Long {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val m = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            if (m.isNotEmpty()) m[0].totalPss * 1024L else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    fun battery(ctx: Context): Battery? {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        // Same trust rule as Collector.kt: Samsung does not report a trustworthy EXTRA_CYCLE_COUNT, so it is ignored there.
        // Anything that is missing or absurd is "not available" - never a guessed number.
        val cycles = cycleCount(i)
        return Battery(
            if (level >= 0 && scale > 0) level * 100 / scale else -1,
            i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            if (temp != Int.MIN_VALUE) temp / 10f else null,
            if (volt > 0) volt else null,
            i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            cycles
        )
    }

    /** first positive number found in one of the sysfs [paths], or null */
    private fun sysNum(vararg paths: String): Long? {
        for (p in paths) {
            val v = try {
                File(p).readText().trim().toLongOrNull()
            } catch (e: Throwable) {
                null
            }
            if (v != null && v > 0) return v
        }
        return null
    }

    /** charge cycles from a trustworthy source only (1..4999 from sysfs), or null */
    private fun cycleCount(i: Intent): Int? {
        if (Build.VERSION.SDK_INT >= 34 && !Build.MANUFACTURER.equals("samsung", true)) {
            val official = i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
            if (official in 1..9999) return official
        }
        val sys = sysNum(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle",
            "/sys/class/power_supply/bms/cycle_count"
        )
        return if (sys != null && sys in 1..4999) sys.toInt() else null
    }

    fun dirSize(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) return f.length()
        var sum = 0L
        val kids = f.listFiles() ?: return 0L
        for (k in kids) sum += dirSize(k)
        return sum
    }

    /** deletes everything inside [dir] but keeps the folder itself; returns the number of bytes removed */
    fun clearDir(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        val kids = dir.listFiles() ?: return 0L
        for (k in kids) {
            val size = dirSize(k)
            val ok = if (k.isDirectory) k.deleteRecursively() else k.delete()
            if (ok) freed += size
        }
        return freed
    }
}

/**
 * Transparent score. It is only an at-a-glance summary of four real readings, never a hardware diagnosis:
 *   storage free space (35) + RAM available now (25) + battery temperature (20) + battery health state reported by Android (20)
 * A reading the device does not provide is left out and the remaining weights are rescaled.
 */
object HealthScore {
    class Part(val nameRes: Int, val score: Int, val weight: Int, val detail: String)

    class Result(val score: Int, val parts: List<Part>)

    private fun lin(v: Double, zeroAt: Double, fullAt: Double): Int {
        val t = (v - zeroAt) / (fullAt - zeroAt)
        return Math.round(t.coerceIn(0.0, 1.0) * 100).toInt()
    }

    fun compute(ctx: Context): Result {
        val parts = ArrayList<Part>()
        val st = Dev.storage(ctx)
        if (st.total > 0) {
            parts.add(
                Part(R.string.hs_storage, lin(st.freePct.toDouble(), 3.0, 20.0), 35, String.format(java.util.Locale.US, "%.0f%% free", st.freePct))
            )
        }
        val mi = Dev.ram(ctx)
        if (mi.totalMem > 0) {
            val availPct = mi.availMem * 100.0 / mi.totalMem
            val s = if (mi.lowMemory) 0 else lin(availPct, 5.0, 25.0)
            parts.add(Part(R.string.hs_ram, s, 25, String.format(java.util.Locale.US, "%.0f%% available", availPct)))
        }
        val b = Dev.battery(ctx)
        if (b != null) {
            val t = b.tempC
            if (t != null) {
                parts.add(Part(R.string.hs_temp, 100 - lin(t.toDouble(), 35.0, 45.0), 20, String.format(java.util.Locale.US, "%.1f °C", t)))
            }
            if (b.health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
                val good = b.health == BatteryManager.BATTERY_HEALTH_GOOD
                parts.add(Part(R.string.hs_bat_state, if (good) 100 else 20, 20, if (good) "GOOD" else "!"))
            }
        }
        var w = 0
        var sum = 0
        for (p in parts) {
            w += p.weight
            sum += p.score * p.weight
        }
        return Result(if (w == 0) 0 else Math.round(sum / w.toFloat()), parts)
    }
}


/**
 * Real "RAM clean": asks Android to stop the BACKGROUND processes of the apps the user has installed
 * (ActivityManager.killBackgroundProcesses, a normal permission - no root). It is the same mechanism the
 * system's own cleaner uses. Foreground apps, running music / downloads (foreground services), system apps,
 * this app, the launcher, the keyboard, the dialer and the SMS app are never touched.
 * The result is measured (available RAM before / after) instead of claimed.
 */
object RamCleaner {
    class Result(val apps: Int, val before: Long, val after: Long) {
        val freed: Long get() = Math.max(0L, after - before)
    }

    fun candidates(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val skip = HashSet<String>()
        skip.add(ctx.packageName)
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(home, 0)?.activityInfo?.packageName?.let { skip.add(it) }
        } catch (e: Exception) {
            // ignore
        }
        try {
            val ime = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
            for (part in ime.split(":")) {
                val pkg = part.substringBefore("/")
                if (pkg.isNotEmpty()) skip.add(pkg)
            }
        } catch (e: Exception) {
            // ignore
        }
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            tm.defaultDialerPackage?.let { skip.add(it) }
        } catch (e: Exception) {
            // ignore
        }
        try {
            android.provider.Telephony.Sms.getDefaultSmsPackage(ctx)?.let { skip.add(it) }
        } catch (e: Exception) {
            // ignore
        }
        val out = LinkedHashSet<String>()
        try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            for (ri in pm.queryIntentActivities(main, 0)) {
                val ai = ri.activityInfo?.applicationInfo ?: continue
                val pkg = ai.packageName
                if (pkg in skip) continue
                val pureSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (pureSystem) continue
                out.add(pkg)
            }
        } catch (e: Exception) {
            // ignore
        }
        return out.toList()
    }

    /** runs on a worker thread; [done] is called on the main thread */
    fun clean(ctx: Context, done: (Result) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val before = Dev.ram(app).availMem
            val list = candidates(app)
            var n = 0
            for (pkg in list) {
                try {
                    am.killBackgroundProcesses(pkg)
                    n++
                } catch (e: Exception) {
                    // a refused package is simply skipped
                }
            }
            try {
                Thread.sleep(1500) // let the system finish reclaiming before measuring
            } catch (e: InterruptedException) {
                // measure anyway
            }
            val after = Dev.ram(app).availMem
            val r = Result(n, before, after)
            Handler(Looper.getMainLooper()).post { done(r) }
        }.start()
    }
}
