package com.mahirex.app

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Collector(private val lc: Context, private val app: Context) {

    private val met = LinkedHashMap<String, Any>()
    private val isFa: Boolean = lc.resources.configuration.locales.get(0).language == "fa"

    private fun tx(id: Int, vararg a: Any): String = lc.getString(id, *a)


    // ------------------------------------------------------------ helpers
    private fun dateStr(ms: Long): String = Fmt.date(ms, isFa)

    private fun size(b: Long): String {
        var v = b.toDouble()
        var i = 0
        val u = arrayOf("B", "KB", "MB", "GB", "TB")
        while (v >= 1024 && i < u.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${v.toLong()} B" else String.format(Locale.US, "%.2f %s", v, u[i])
    }

    private fun pct(part: Long, total: Long): String {
        if (total <= 0) return "0%"
        return String.format(Locale.US, "%.1f%%", part * 100.0 / total)
    }

    private fun yn(b: Boolean): String = if (b) tx(R.string.yes) else tx(R.string.no)

    private fun readFile(path: String): String? = try {
        File(path).readText().trim()
    } catch (e: Throwable) {
        null
    }

    private fun dur(ms: Long): String {
        val totalMin = ms / 60000
        val d = totalMin / (60 * 24)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return tx(R.string.dur_fmt, d, h, m)
    }

    // ------------------------------------------------------------ entry
    private class Step(val id: String, val titleRes: Int, val fn: () -> InfoSection)

    private fun safe(st: Step): InfoSection {
        return try {
            st.fn()
        } catch (e: Throwable) {
            val s = InfoSection(st.id, tx(st.titleRes))
            s.add(tx(R.string.k_status), tx(R.string.v_section_err), Tone.WARN)
            s
        }
    }

    fun collect(progress: ((Int, Int, String) -> Unit)? = null): Report {
        val sum = LinkedHashMap<String, String>()
        val out = ArrayList<InfoSection>()
        val steps = listOf(
            Step("device", R.string.sec_device) { device(app, sum) },
            Step("system", R.string.sec_system) { system(sum) },
            Step("cpu", R.string.sec_cpu) { cpu(sum) },
            Step("gpu", R.string.sec_gpu) { gpu(app, sum) },
            Step("ram", R.string.sec_ram) { memory(app, sum) },
            Step("storage", R.string.sec_storage) { storage(app, sum) },
            Step("display", R.string.sec_display) { display(app, sum) },
            Step("battery", R.string.sec_battery) { battery(app, sum) },
            Step("network", R.string.sec_network) { network(app, sum) },
            Step("sim", R.string.sec_sim) { sim(app) },
            Step("camera", R.string.sec_camera) { camera(app) },
            Step("sensors", R.string.sec_sensors) { sensors(app) },
            Step("features", R.string.sec_features) { features(app) },
            Step("security", R.string.sec_security) { security(app) }
        )
        var i = 0
        for (st in steps) {
            progress?.invoke(i + 1, steps.size, tx(st.titleRes))
            out.add(safe(st))
            i++
        }
        // short preview line for each section (shown on the home screen)
        for (sec in out) {
            sec.preview = when (sec.id) {
                "device" -> sum["model"] ?: ""
                "system" -> sum["android"] ?: ""
                "cpu" -> sum["cpu"] ?: ""
                "gpu" -> sum["gpu"] ?: ""
                "ram" -> sum["ram"] ?: ""
                "storage" -> sum["storage"] ?: ""
                "display" -> sum["display"] ?: ""
                "battery" -> sum["battery"] ?: ""
                "network" -> sum["ip"] ?: ""
                "camera" -> (met["cameras"]?.toString() ?: "") + (met["max_mp"]?.let { "  •  " + String.format(Locale.US, "%.0f MP", it as Double) } ?: "")
                "sensors" -> met["sensors"]?.toString() ?: ""
                else -> ""
            }
        }
        out.add(0, summary(sum))
        val ver = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "2.0"
        } catch (e: Throwable) {
            "2.0"
        }
        val lang = lc.resources.configuration.locales.get(0).language
        return Report(System.currentTimeMillis(), lang, ver, met, out)
    }

    private fun summary(sum: Map<String, String>): InfoSection {
        val s = InfoSection("summary", tx(R.string.sec_summary))
        s.add(tx(R.string.k_model), sum["model"])
        s.add(tx(R.string.k_android), sum["android"])
        s.add(tx(R.string.sec_cpu), sum["cpu"])
        s.add(tx(R.string.k_cores), sum["cores"])
        s.add(tx(R.string.k_gpu), sum["gpu"])
        s.add(tx(R.string.sec_ram), sum["ram"])
        s.add(tx(R.string.k_internal), sum["storage"])
        s.add(tx(R.string.sec_display), sum["display"])
        s.add(tx(R.string.sec_battery), sum["battery"])
        s.add(tx(R.string.k_ip), sum["ip"])
        s.add(tx(R.string.k_uptime), sum["uptime"])
        return s
    }

    // ------------------------------------------------------------ device
    private fun device(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("device", tx(R.string.sec_device))
        s.add(tx(R.string.k_manufacturer), Build.MANUFACTURER)
        s.add(tx(R.string.k_brand), Build.BRAND)
        s.add(tx(R.string.k_model), Build.MODEL)
        val market = prop("ro.product.marketname") ?: prop("ro.config.marketing_name") ?: prop("ro.product.vendor.marketname")
        if (market != null && !market.equals(Build.MODEL, true)) s.add(tx(R.string.k_market_name), market)
        s.add(tx(R.string.k_device_name), Build.DEVICE)
        s.add(tx(R.string.k_product), Build.PRODUCT)
        s.add(tx(R.string.k_board), Build.BOARD)
        s.add(tx(R.string.k_hardware), Build.HARDWARE)
        if (Build.VERSION.SDK_INT >= 31) {
            s.add(tx(R.string.k_soc_man), Build.SOC_MANUFACTURER)
            s.add(tx(R.string.k_soc_model), Build.SOC_MODEL)
            sum["soc"] = Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL
        }
        s.add(tx(R.string.k_build_type), Build.TYPE)
        s.add("Tags", Build.TAGS)
        s.add("Bootloader", Build.BOOTLOADER)
        s.add("Host", Build.HOST)
        // build times straight from the system properties (each image of the firmware has its own)
        val stamps = LinkedHashMap<String, Long>()
        val keys = listOf(
            tx(R.string.k_build_os) to "ro.build.date.utc",
            "System" to "ro.system.build.date.utc",
            "Vendor" to "ro.vendor.build.date.utc",
            "ODM" to "ro.odm.build.date.utc",
            "Product" to "ro.product.build.date.utc",
            "System Ext" to "ro.system_ext.build.date.utc",
            "Boot image" to "ro.bootimage.build.date.utc"
        )
        for (k in keys) {
            val v = prop(k.second)?.toLongOrNull()
            if (v != null && v > 1_000_000_000L) stamps[k.first] = v * 1000
        }
        val newest = maxOf(Build.TIME, stamps.values.maxOrNull() ?: 0L)
        s.add(tx(R.string.k_build_newest), dateStr(newest))
        if (stamps.isNotEmpty()) {
            s.sub2(tx(R.string.k_build_parts))
            for (e in stamps) s.add(e.key, dateStr(e.value))
        }
        try {
            val pi = ctx.packageManager.getPackageInfo("android", 0)
            if (pi.lastUpdateTime > 0) s.add(tx(R.string.k_last_update), dateStr(pi.lastUpdateTime))
            if (pi.firstInstallTime > 0 && pi.firstInstallTime != pi.lastUpdateTime) {
                s.add(tx(R.string.k_first_install), dateStr(pi.firstInstallTime))
            }
        } catch (e: Throwable) {
            // not available
        }
        s.add("Fingerprint", Build.FINGERPRINT)
        sum["model"] = Build.MANUFACTURER + " " + Build.MODEL
        met["model"] = Build.MODEL
        met["manufacturer"] = Build.MANUFACTURER
        return s
    }

    // ------------------------------------------------------------ system
    private fun system(sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("system", tx(R.string.sec_system))
        s.add(tx(R.string.k_android_ver), Build.VERSION.RELEASE)
        s.add(tx(R.string.k_api), Build.VERSION.SDK_INT)
        s.add("Codename", Build.VERSION.CODENAME)
        if (Build.VERSION.SDK_INT >= 23) s.add(tx(R.string.k_sec_patch), Build.VERSION.SECURITY_PATCH)
        s.add("Incremental", Build.VERSION.INCREMENTAL)
        s.add("Build ID", Build.ID)
        s.add("Build Display", Build.DISPLAY)
        s.add(tx(R.string.k_kernel), System.getProperty("os.version"))
        s.add(tx(R.string.k_radio), try { Build.getRadioVersion() } catch (e: Throwable) { null })
        if (Build.VERSION.SDK_INT >= 23) s.add(tx(R.string.k_64bit), yn(android.os.Process.is64Bit()))
        s.add("Java VM", System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"))
        s.add(tx(R.string.k_sys_lang), Locale.getDefault().toString())
        s.add(tx(R.string.k_timezone), TimeZone.getDefault().id)
        val up = dur(SystemClock.elapsedRealtime())
        s.add(tx(R.string.k_uptime), up)
        s.add(tx(R.string.k_awake), dur(SystemClock.uptimeMillis()))
        s.add(tx(R.string.k_last_boot), dateStr(System.currentTimeMillis() - SystemClock.elapsedRealtime()))
        sum["android"] = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        sum["uptime"] = up
        met["android"] = Build.VERSION.RELEASE
        met["api"] = Build.VERSION.SDK_INT
        return s
    }

    // ------------------------------------------------------------ cpu
    private fun prop(key: String): String? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val r = p.inputStream.bufferedReader().readText().trim()
        if (r.isEmpty() || r.equals("unknown", true)) null else r
    } catch (e: Throwable) {
        null
    }

    private fun armCore(part: String?): String? = when (part?.trim()?.lowercase(Locale.US)) {
        "0xd03" -> "Cortex-A53"
        "0xd04" -> "Cortex-A35"
        "0xd05" -> "Cortex-A55"
        "0xd07" -> "Cortex-A57"
        "0xd08" -> "Cortex-A72"
        "0xd09" -> "Cortex-A73"
        "0xd0a" -> "Cortex-A75"
        "0xd0b" -> "Cortex-A76"
        "0xd0d" -> "Cortex-A77"
        "0xd41" -> "Cortex-A78"
        "0xd44" -> "Cortex-X1"
        "0xd46" -> "Cortex-A510"
        "0xd47" -> "Cortex-A710"
        "0xd48" -> "Cortex-X2"
        "0xd4d" -> "Cortex-A715"
        "0xd4e" -> "Cortex-X3"
        "0xd80" -> "Cortex-A520"
        "0xd81" -> "Cortex-A720"
        "0xd82" -> "Cortex-X4"
        "0xd85" -> "Cortex-X925"
        "0xd87" -> "Cortex-A725"
        else -> null
    }

    private fun marketingName(model: String): String? = when (model.trim().uppercase(Locale.US)) {
        "MT6769V/CU" -> "MediaTek Helio G80"
        "MT6769V/CZ" -> "MediaTek Helio G85"
        else -> null
    }

    private fun chipName(raw: String): String {
        val r = raw.trim()
        val l = r.lowercase(Locale.US)
        return when {
            l.startsWith("mt") && l.length > 2 && l[2].isDigit() -> "MediaTek " + r.uppercase(Locale.US)
            l.startsWith("exynos") -> "Samsung " + r
            l.startsWith("s5e") -> "Samsung Exynos (" + r + ")"
            l.startsWith("sm") && l.length > 2 && l[2].isDigit() -> "Qualcomm " + r.uppercase(Locale.US)
            l.startsWith("sdm") || l.startsWith("msm") || l.startsWith("sdx") -> "Qualcomm " + r.uppercase(Locale.US)
            l.startsWith("kirin") || l.startsWith("hi3") -> "HiSilicon " + r
            l.startsWith("ums") || l.startsWith("sc9") -> "Unisoc " + r.uppercase(Locale.US)
            else -> r
        }
    }

    private fun cpu(sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("cpu", tx(R.string.sec_cpu))
        val info = readFile("/proc/cpuinfo") ?: ""

        // first non-numeric value (the "processor" key holds the core index, not a name)
        fun field(vararg names: String): String? {
            for (line in info.lines()) {
                val idx = line.indexOf(':')
                if (idx < 0) continue
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (v.isEmpty() || v.toIntOrNull() != null) continue
                if (names.any { it.equals(k, true) }) return v
            }
            return null
        }

        // per-core ARM part numbers
        val parts = HashMap<Int, String>()
        var curCore = -1
        for (line in info.lines()) {
            val idx = line.indexOf(':')
            if (idx < 0) continue
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            if (k.equals("processor", true)) curCore = v.toIntOrNull() ?: curCore
            else if (k.equals("CPU part", true) && curCore >= 0) parts[curCore] = v
        }

        val hwField = field("Hardware")
        val modelField = field("model name", "Processor")

        // resolve the chip name from the most reliable source available
        var rawSoc: String? = null
        var soc: String? = null
        if (Build.VERSION.SDK_INT >= 31) {
            val mod = Build.SOC_MODEL
            val man = Build.SOC_MANUFACTURER
            if (!mod.isNullOrBlank() && !mod.equals("unknown", true)) {
                rawSoc = mod
                soc = marketingName(mod) ?: run {
                    val c = chipName(mod)
                    if (c == mod.trim() && !man.isNullOrBlank() && !man.equals("unknown", true) &&
                        !mod.startsWith(man, true)
                    ) "$man $mod" else c
                }
            }
        }
        if (soc == null) {
            val cand = prop("ro.soc.model") ?: prop("ro.hardware.chipname") ?: prop("ro.chipname") ?: hwField
                ?: prop("ro.mediatek.platform") ?: prop("ro.board.platform") ?: prop("ro.hardware")
                ?: Build.HARDWARE ?: Build.BOARD
            if (!cand.isNullOrBlank()) {
                rawSoc = cand
                soc = marketingName(cand) ?: chipName(cand)
            }
        }
        val cpuName = soc ?: modelField ?: tx(R.string.unknown)
        val cores = Runtime.getRuntime().availableProcessors()

        // market name (e.g. "Helio G80") from every identifier the phone exposes; unknown chips are said to be unknown
        val marketName = SocNames.find(
            listOf(
                rawSoc, if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null,
                prop("ro.soc.model"), prop("ro.hardware.chipname"), prop("ro.chipname"), hwField,
                prop("ro.mediatek.platform"), prop("ro.vendor.mediatek.platform"), prop("ro.board.platform"), prop("ro.product.board"),
                prop("ro.hardware"), Build.HARDWARE, Build.BOARD
            )
        )
        s.add(tx(R.string.k_cpu_model), marketName ?: tx(R.string.v_cpu_unknown, cpuName))
        s.add(tx(R.string.k_cpu_name), cpuName)
        if (rawSoc != null && !cpuName.equals(rawSoc, true)) s.add(tx(R.string.k_cpu_code), rawSoc)
        s.add("Hardware", hwField ?: prop("ro.hardware") ?: Build.HARDWARE)
        s.add(tx(R.string.k_platform), prop("ro.board.platform") ?: Build.BOARD)
        s.add(tx(R.string.k_abi_main), Build.SUPPORTED_ABIS.firstOrNull())
        s.add(tx(R.string.k_abi_all), Build.SUPPORTED_ABIS.joinToString(", "))
        s.add(tx(R.string.k_cores_count), cores)

        var anyFreq = false
        val groups = LinkedHashMap<String, Int>()
        val lines = ArrayList<Pair<String, String>>()
        for (i in 0 until cores) {
            val base = "/sys/devices/system/cpu/cpu${i}/cpufreq/"
            val max = readFile(base + "cpuinfo_max_freq")?.toLongOrNull()
            val min = readFile(base + "cpuinfo_min_freq")?.toLongOrNull()
            val cur = readFile(base + "scaling_cur_freq")?.toLongOrNull()
            val core = armCore(parts[i])
            if (max != null) {
                anyFreq = true
                var t = if (core != null) "$core | " else ""
                t += tx(R.string.f_max, max / 1000)
                if (min != null) t += " | " + tx(R.string.f_min, min / 1000)
                if (cur != null) t += " | " + tx(R.string.f_cur, cur / 1000)
                lines.add(Pair(tx(R.string.core_label, i + 1, i), t))
                val key = (core ?: tx(R.string.v_core_word)) + " @ " + (max / 1000) + " MHz"
                groups[key] = (groups[key] ?: 0) + 1
            } else if (core != null) {
                lines.add(Pair(tx(R.string.core_label, i + 1, i), core))
                groups[core] = (groups[core] ?: 0) + 1
            }
        }
        if (groups.isNotEmpty()) {
            s.add(tx(R.string.k_core_layout), groups.entries.joinToString("  +  ") { "${it.value}× ${it.key}" })
        }
        for (l in lines) s.add(l.first, l.second)
        if (!anyFreq) s.add(tx(R.string.k_freq_cores), tx(R.string.v_freq_limited))
        val gov = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        if (gov != null) s.add("Governor", gov)
        val feat = field("Features")
        if (feat != null) s.add(tx(R.string.k_cpu_features), feat.split(" ").filter { it.isNotBlank() }.joinToString(", "))

        sum["cpu"] = cpuName
        sum["cores"] = cores.toString()
        met["cpu"] = cpuName
        met["cores"] = cores
        return s
    }

    // ------------------------------------------------------------ gpu
    private fun glInfo(): Map<String, String> {
        val r = LinkedHashMap<String, String>()
        var dpy = EGL14.EGL_NO_DISPLAY
        var ctx = EGL14.EGL_NO_CONTEXT
        var surf = EGL14.EGL_NO_SURFACE
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val v = IntArray(2)
            if (!EGL14.eglInitialize(dpy, v, 0, v, 1)) return r
            val cfgAttr = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(dpy, cfgAttr, 0, cfgs, 0, 1, num, 0)
            val cfg = cfgs[0] ?: return r
            ctx = EGL14.eglCreateContext(
                dpy, cfg, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            surf = EGL14.eglCreatePbufferSurface(
                dpy, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            GLES20.glGetString(GLES20.GL_RENDERER)?.let { r["renderer"] = it }
            GLES20.glGetString(GLES20.GL_VENDOR)?.let { r["vendor"] = it }
            GLES20.glGetString(GLES20.GL_VERSION)?.let { r["version"] = it }
            GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)?.let { r["glsl"] = it }
            GLES20.glGetString(GLES20.GL_EXTENSIONS)?.let { r["ext"] = it }
        } catch (e: Throwable) {
            // ignore
        } finally {
            try {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf)
                if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx)
                EGL14.eglTerminate(dpy)
            } catch (e: Throwable) {
                // ignore
            }
        }
        return r
    }

    private fun gpu(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("gpu", tx(R.string.sec_gpu))
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        s.add(tx(R.string.k_gles), am.deviceConfigurationInfo.glEsVersion)
        val g = glInfo()
        s.add(tx(R.string.k_gpu_renderer), g["renderer"])
        s.add(tx(R.string.k_vendor), g["vendor"])
        s.add(tx(R.string.k_gl_version), g["version"])
        s.add(tx(R.string.k_glsl), g["glsl"])
        for (f in ctx.packageManager.systemAvailableFeatures) {
            if (f.name == "android.hardware.vulkan.version") {
                val v = f.version
                s.add(tx(R.string.k_vk_version), "${v shr 22}.${(v shr 12) and 0x3ff}.${v and 0xfff}")
            }
            if (f.name == "android.hardware.vulkan.level") s.add(tx(R.string.k_vk_level), f.version)
        }
        val ext = g["ext"]
        if (ext != null) {
            val list = ext.trim().split(" ").filter { it.isNotBlank() }
            s.add(tx(R.string.k_gl_ext_count), list.size)
            s.add(tx(R.string.k_gl_ext_list), list.joinToString(", "))
        }
        sum["gpu"] = g["renderer"] ?: "—"
        met["gpu"] = g["renderer"] ?: "—"
        return s
    }

    // ------------------------------------------------------------ robust readers (primary method + fallbacks)
    private fun numFile(vararg paths: String): Long? {
        for (p in paths) {
            val v = readFile(p)?.toLongOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }

    // advertised RAM: (bytes, trustworthy). 1) size reported by the bootloader, 2) next standard size
    internal fun ramAdvertised(total: Long): Pair<Long, Boolean> {
        val gib = 1024.0 * 1024.0 * 1024.0
        val keys = listOf(
            "ro.boot.ddr_size", "ro.boot.ddrsize", "ro.boot.hardware.ddr", "ro.boot.ram_size",
            "ro.vendor.boot.ddr_size", "ro.ram.size", "ro.config.ramsize"
        )
        for (k in keys) {
            val raw = prop(k) ?: continue
            val m = Regex("(\\d+(?:\\.\\d+)?)\\s*([GgMm])").find(raw) ?: continue
            val v = m.groupValues[1].toDoubleOrNull() ?: continue
            val bytes = (if (m.groupValues[2].equals("g", true)) v * gib else v * 1024.0 * 1024.0).toLong()
            if (bytes >= total && bytes <= total * 1.6) return Pair(bytes, true)
        }
        val std = doubleArrayOf(
            1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0, 11.0, 12.0, 14.0,
            16.0, 18.0, 20.0, 24.0, 32.0, 48.0, 64.0
        )
        for (g in std) {
            val b = (g * gib).toLong()
            if (b >= total) {
                val reserved = (b - total).toDouble() / b
                // 2%..22% reserved is what real phones show; outside that the guess is doubtful
                return Pair(b, reserved in 0.02..0.22)
            }
        }
        return Pair(total, false)
    }

    // ------------------------------------------------------------ memory
    private fun memory(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("ram", tx(R.string.sec_ram))
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val memTotal = if (mi.totalMem > 0) mi.totalMem else (readFile("/proc/meminfo")?.lines()
            ?.firstOrNull { it.startsWith("MemTotal:") }?.replace(Regex("[^0-9]"), "")?.toLongOrNull()?.times(1024) ?: 0L)
        val used = memTotal - mi.availMem
        val adv = ramAdvertised(memTotal)
        val advertised = adv.first
        val gibB = 1024L * 1024L * 1024L
        val advBase = if (advertised % gibB == 0L) "${advertised / gibB} GB" else size(advertised)
        val advText = if (adv.second) advBase else tx(R.string.v_ram_est, advBase)
        s.add(tx(R.string.k_ram_nominal), advText)
        s.add(tx(R.string.k_ram_usable), size(memTotal))
        if (advertised > memTotal) s.add(tx(R.string.k_ram_reserved), size(advertised - memTotal))
        s.add(tx(R.string.k_ram_avail), size(mi.availMem))
        s.add(tx(R.string.k_ram_used), size(used) + "  (" + pct(used, memTotal) + ")")
        s.add(tx(R.string.k_low_threshold), size(mi.threshold))
        s.add(tx(R.string.k_low_state), yn(mi.lowMemory), if (mi.lowMemory) Tone.BAD else Tone.OK)

        val meminfo = readFile("/proc/meminfo") ?: ""
        fun kb(name: String): Long? {
            for (line in meminfo.lines()) {
                if (line.startsWith("${name}:")) {
                    val digits = line.substring(name.length + 1).replace("kB", "").trim()
                    return digits.toLongOrNull()?.times(1024)
                }
            }
            return null
        }
        kb("Buffers")?.let { s.add("Buffers", size(it)) }
        kb("Cached")?.let { s.add("Cached", size(it)) }
        val swapT = kb("SwapTotal")
        val swapF = kb("SwapFree")
        if (swapT != null) {
            s.add(tx(R.string.k_swap_total), size(swapT))
            if (swapF != null) s.add(tx(R.string.k_swap_free), size(swapF))
        }
        s.add(tx(R.string.k_mem_class), "${am.memoryClass} MB")
        s.add(tx(R.string.k_mem_class_large), "${am.largeMemoryClass} MB")
        s.add(tx(R.string.k_low_ram_dev), yn(am.isLowRamDevice))
        s.add(tx(R.string.k_java_heap), size(Runtime.getRuntime().maxMemory()))
        sum["ram"] = tx(R.string.v_ram_summary, advText, pct(used, memTotal))
        met["ram_total"] = advertised
        met["ram_used"] = used
        met["ram_usable"] = memTotal
        return s
    }

    // ------------------------------------------------------------ storage
    private fun fs(path: String): Pair<Long, Long>? = try {
        val st = StatFs(path)
        if (st.totalBytes > 0) Pair(st.totalBytes, st.availableBytes) else null
    } catch (e: Throwable) {
        null
    }

    private val GIB =1024L * 1024L * 1024L

    // Android (and Samsung) show phone storage in binary units: 64 GB = 64 x 1024^3 bytes
    // advertised phone capacity: smallest standard size that can hold the reported bytes
    private fun nominalPhone(bytes: Long): Long {
        for (g in longArrayOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)) {
            if (g * GIB >= bytes) return g * GIB
        }
        return bytes
    }

    // memory cards are sold in decimal sizes (32 GB = 32,000,000,000 bytes)
    private fun nominalCard(usable: Long): Long {
        for (g in longArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)) {
            if (g * 1_000_000_000L >= usable) return g * 1_000_000_000L
        }
        return usable
    }

    private fun storage(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("storage", tx(R.string.sec_storage))
        val data = fs(Environment.getDataDirectory().path)
        if (data != null) {
            var rawTotal = 0L
            var rawFree = 0L
            try {
                val ssm = ctx.getSystemService(StorageStatsManager::class.java)
                rawTotal = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                rawFree = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
            } catch (e: Throwable) {
                // fall back below
            }
            // primary: StorageStatsManager. fallback: partitions + raw block device size
            var cand = maxOf(rawTotal, data.first)
            if (rawTotal <= 0L) {
                var sumParts = data.first
                for (p in listOf("/system", "/vendor", "/product", "/system_ext", "/odm")) {
                    fs(p)?.let { sumParts += it.first }
                }
                cand = maxOf(cand, sumParts)
                for (dev in listOf("sda", "mmcblk0", "nvme0n1")) {
                    val sec = numFile("/sys/block/$dev/size")
                    if (sec != null) cand = maxOf(cand, sec * 512L)
                }
            }
            val total: Long = nominalPhone(cand)
            val free: Long = if (rawFree <= 0L || rawFree > total) data.second else rawFree
            val used: Long = total - free

            s.sub(tx(R.string.sub_main_storage))
            s.add(tx(R.string.k_total_cap), size(total) + (if (rawTotal > 0L) "" else ("  " + tx(R.string.v_est_paren))))
            s.add(tx(R.string.k_used_space), size(used) + "  (" + pct(used, total) + ")")
            s.add(tx(R.string.k_free), size(free) + "  (" + pct(free, total) + ")")

            s.sub2(tx(R.string.sub_user_part))
            s.add(tx(R.string.k_capacity), size(data.first))
            s.add(tx(R.string.k_used), size(data.first - data.second) + "  (" + pct(data.first - data.second, data.first) + ")")
            s.add(tx(R.string.k_free), size(data.second))

            val sysPart = total - data.first
            val sys = ArrayList<Pair<String, Pair<Long, Long>>>()
            for (p in listOf("/system", "/vendor", "/product", "/system_ext", "/odm")) {
                val f = fs(p) ?: continue
                sys.add(Pair(p, f))
            }
            s.sub2(tx(R.string.sub_sys_parts))
            for (p in sys) s.add(p.first, size(p.second.first))
            if (sysPart > 0) {
                s.add(tx(R.string.k_os_share), size(sysPart) + "  " + tx(R.string.v_not_usable))
            }
            sum["storage"] = tx(R.string.v_storage_summary, size(total), size(free))
            met["storage_total"] = total
            met["storage_free"] = free
        }
        var n = 0
        for (d in ctx.getExternalFilesDirs(null)) {
            if (d == null) continue
            val removable = try {
                Environment.isExternalStorageRemovable(d)
            } catch (e: Throwable) {
                false
            }
            if (!removable) continue
            val e = fs(d.path) ?: continue
            n++
            s.sub(tx(R.string.sub_sd, n))
            s.add(tx(R.string.k_nominal_cap), String.format(Locale.US, "%d GB", nominalCard(e.first) / 1_000_000_000L))
            s.add(tx(R.string.k_usable_cap), size(e.first))
            s.add(tx(R.string.k_free), size(e.second))
        }
        if (n == 0) {
            s.sub(tx(R.string.sub_card))
            s.add(tx(R.string.k_status), tx(R.string.v_no_card))
        }
        return s
    }

    // ------------------------------------------------------------ display
    @Suppress("DEPRECATION")
    private fun display(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("display", tx(R.string.sec_display))
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        val disp = wm.defaultDisplay
        disp.getRealMetrics(dm)
        val res = "${dm.widthPixels} x ${dm.heightPixels}"
        s.add(tx(R.string.k_resolution), res + " px")
        s.add(tx(R.string.k_density), dm.density)
        s.add("DPI", dm.densityDpi)
        s.add("xdpi / ydpi", String.format(Locale.US, "%.1f / %.1f", dm.xdpi, dm.ydpi))
        if (dm.xdpi > 0 && dm.ydpi > 0) {
            val wIn = dm.widthPixels / dm.xdpi.toDouble()
            val hIn = dm.heightPixels / dm.ydpi.toDouble()
            s.add(tx(R.string.k_diag), String.format(Locale.US, "%.2f inch", Math.sqrt(wIn * wIn + hIn * hIn)))
            met["diag"] = Math.sqrt(wIn * wIn + hIn * hIn)
        }
        s.add(tx(R.string.k_refresh), String.format(Locale.US, "%.0f Hz", disp.refreshRate))
        val modes = disp.supportedModes
        if (modes != null) {
            s.add(
                tx(R.string.k_modes),
                modes.joinToString("\n") {
                    "${it.physicalWidth} x ${it.physicalHeight} @ ${String.format(Locale.US, "%.0f", it.refreshRate)} Hz"
                }
            )
        }
        if (Build.VERSION.SDK_INT >= 26) {
            s.add("HDR", yn(disp.isHdr))
            s.add(tx(R.string.k_wide_color), yn(disp.isWideColorGamut))
        }
        val cfg = ctx.resources.configuration
        s.add(tx(R.string.k_orientation), if (cfg.orientation == 2) tx(R.string.v_landscape) else tx(R.string.v_portrait))
        s.add(tx(R.string.k_font_scale), cfg.fontScale)
        try {
            val b = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            s.add(tx(R.string.k_brightness), tx(R.string.v_brightness, b, b * 100 / 255))
        } catch (e: Throwable) {
            // ignore
        }
        try {
            val t = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            s.add(tx(R.string.k_screen_timeout), tx(R.string.v_timeout, t / 1000))
        } catch (e: Throwable) {
            // ignore
        }
        sum["display"] = res + " | " + String.format(Locale.US, "%.0f Hz", disp.refreshRate)
        met["display"] = res
        met["refresh"] = disp.refreshRate.toDouble()
        met["dpi"] = dm.densityDpi
        return s
    }

    // ------------------------------------------------------------ battery
    // factory (design) capacity from the system power profile; null when the phone does not expose it
    private fun designCapacityMah(ctx: Context): Double? = try {
        val c = Class.forName("com.android.internal.os.PowerProfile")
        val o = c.getConstructor(Context::class.java).newInstance(ctx)
        val v = c.getMethod("getBatteryCapacity").invoke(o) as? Double
        if (v != null && v > 500) v else null
    } catch (e: Throwable) {
        null
    }

    private fun battery(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("battery", tx(R.string.sec_battery))
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        var percent = -1
        if (i != null) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val p = if (level >= 0 && scale > 0) level * 100 / scale else -1
            percent = p
            s.add(tx(R.string.k_charge_pct), "${p}%")
            sum["battery"] = "${p}%"
            met["battery_pct"] = p

            val stCode = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            met["battery_charging"] = (stCode == BatteryManager.BATTERY_STATUS_CHARGING)
            val status = when (stCode) {
                BatteryManager.BATTERY_STATUS_CHARGING -> tx(R.string.v_charging)
                BatteryManager.BATTERY_STATUS_DISCHARGING -> tx(R.string.v_discharging)
                BatteryManager.BATTERY_STATUS_FULL -> tx(R.string.v_full)
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> tx(R.string.v_not_charging)
                else -> tx(R.string.unknown)
            }
            s.add(tx(R.string.k_status), status, if (stCode == BatteryManager.BATTERY_STATUS_CHARGING || stCode == BatteryManager.BATTERY_STATUS_FULL) Tone.OK else Tone.NONE)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val src = ArrayList<String>()
            if (plugged and BatteryManager.BATTERY_PLUGGED_AC != 0) src.add(tx(R.string.v_ac))
            if (plugged and BatteryManager.BATTERY_PLUGGED_USB != 0) src.add("USB")
            if (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0) src.add(tx(R.string.v_wireless))
            s.add(tx(R.string.k_source), if (src.isEmpty()) tx(R.string.v_not_connected) else src.joinToString(" + "))
            val hCode = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val health = when (hCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> tx(R.string.v_good)
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> tx(R.string.v_overheat)
                BatteryManager.BATTERY_HEALTH_DEAD -> tx(R.string.v_dead)
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> tx(R.string.v_overvolt)
                BatteryManager.BATTERY_HEALTH_COLD -> tx(R.string.v_cold)
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> tx(R.string.v_failure)
                else -> tx(R.string.unknown)
            }
            val hTone = when (hCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> Tone.OK
                BatteryManager.BATTERY_HEALTH_OVERHEAT, BatteryManager.BATTERY_HEALTH_DEAD, BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> Tone.BAD
                BatteryManager.BATTERY_HEALTH_COLD, BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> Tone.WARN
                else -> Tone.NONE
            }
            s.add(tx(R.string.k_health), health, hTone)
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (temp >= 0) {
                s.add(tx(R.string.k_temp), String.format(Locale.US, "%.1f °C", temp / 10.0))
                met["battery_temp"] = temp / 10.0
            }
            val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            if (volt > 0) {
                s.add(tx(R.string.k_voltage), "${volt} mV")
                met["battery_mv"] = volt
            }
            s.add(tx(R.string.k_tech), i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY))

            // cycle count: Samsung phones do not report a trustworthy value to apps
            val cycles = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) else -1
            val sysCycles = numFile(
                "/sys/class/power_supply/battery/cycle_count",
                "/sys/class/power_supply/battery/battery_cycle",
                "/sys/class/power_supply/bms/cycle_count"
            )
            if (cycles > 0 && !Build.MANUFACTURER.equals("samsung", true)) {
                s.add(tx(R.string.k_cycles), cycles)
            } else if (sysCycles != null && sysCycles < 5000) {
                s.add(tx(R.string.k_cycles), sysCycles)
            } else {
                s.add(tx(R.string.k_cycles), tx(R.string.v_cycles_unreliable))
            }
        }

        // capacity
        val design = designCapacityMah(ctx) ?: numFile(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design"
        )?.let { v -> (if (v > 100000L) v / 1000.0 else v.toDouble()).takeIf { it in 800.0..30000.0 } }
        val cc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val pNow = if (cap in 0..100) cap else percent
        var capacityShown = false
        if (design != null) {
            s.add(tx(R.string.k_design_cap), "${design.toInt()} mAh")
            capacityShown = true
            if (pNow in 0..100) s.add(tx(R.string.k_remaining), "${(design * pNow / 100).toInt()} mAh")
        }
        // measured estimate: only meaningful when the battery is neither almost empty nor full
        if (cc > 0 && pNow in 20..99) {
            val est = cc / 1000.0 * 100.0 / pNow
            if (design == null || (est >= design * 0.5 && est <= design * 1.2)) {
                s.add(tx(R.string.k_current_cap), "${est.toInt()} mAh")
                capacityShown = true
            }
        }
        if (!capacityShown) {
            s.add(tx(R.string.k_cap), tx(R.string.v_cap_unknown))
        }

        val cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (cur != Int.MIN_VALUE && cur != 0) s.add(tx(R.string.k_current_now), "${cur / 1000} mA")
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        s.add(tx(R.string.k_power_save), yn(pm.isPowerSaveMode))
        return s
    }

    // ------------------------------------------------------------ network
    @Suppress("DEPRECATION")
    private fun network(ctx: Context, sum: MutableMap<String, String>): InfoSection {
        val s = InfoSection("network", tx(R.string.sec_network))
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = if (net != null) cm.getNetworkCapabilities(net) else null
        val lp = if (net != null) cm.getLinkProperties(net) else null

        // all interfaces
        val ifaces = ArrayList<Pair<String, List<String>>>()
        try {
            val nis = NetworkInterface.getNetworkInterfaces()
            if (nis != null) {
                for (ni in Collections.list(nis)) {
                    if (!ni.isUp || ni.isLoopback) continue
                    val addrs = ArrayList<String>()
                    for (a in Collections.list(ni.inetAddresses)) {
                        val h = a.hostAddress ?: continue
                        addrs.add(h.substringBefore('%'))
                    }
                    ifaces.add(Pair(ni.name, addrs))
                }
            }
        } catch (e: Throwable) {
            // ignore
        }

        var primary = ""
        val v4 = ArrayList<String>()
        val v6 = ArrayList<String>()
        if (lp != null) {
            for (la in lp.linkAddresses) {
                val a = la.address
                val h = a.hostAddress ?: continue
                if (a is Inet4Address) v4.add(h) else v6.add(h.substringBefore('%'))
            }
            primary = v4.firstOrNull() ?: ""
        }
        if (primary.isEmpty()) {
            for (f in ifaces) {
                val c = f.second.firstOrNull { it.contains('.') && !it.startsWith("169.254.") }
                if (c != null) {
                    primary = c
                    break
                }
            }
        }
        s.add(tx(R.string.k_ip_main), if (primary.isEmpty()) tx(R.string.v_no_conn_found) else primary)
        sum["ip"] = if (primary.isEmpty()) "—" else primary

        if (caps != null) {
            val types = ArrayList<String>()
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) types.add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) types.add(tx(R.string.v_mobile_data))
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) types.add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) types.add("Bluetooth")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) types.add("VPN")
            s.add(tx(R.string.k_conn_type), types.joinToString(" + "))
            val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            s.add(tx(R.string.k_internet), yn(online), if (online) Tone.OK else Tone.BAD)
            s.add(tx(R.string.k_bw_down), "${caps.linkDownstreamBandwidthKbps / 1000} Mbps")
            s.add(tx(R.string.k_bw_up), "${caps.linkUpstreamBandwidthKbps / 1000} Mbps")
            s.add(tx(R.string.k_metered), yn(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)))
        } else {
            s.add(tx(R.string.k_conn_state), tx(R.string.v_no_active))
        }
        if (lp != null) {
            s.add(tx(R.string.k_iface), lp.interfaceName)
            s.add(tx(R.string.k_ipv4), v4.joinToString(", "))
            s.add(tx(R.string.k_ipv6), v6.joinToString(", "))
            val gw = lp.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress?.substringBefore('%') }.distinct()
            s.add("Gateway", gw.joinToString(", "))
            s.add("DNS", lp.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }.joinToString(", "))
        }

        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            s.sub("Wi-Fi")
            s.add(tx(R.string.k_wifi_state), if (wm.isWifiEnabled) tx(R.string.v_on) else tx(R.string.v_off), if (wm.isWifiEnabled) Tone.OK else Tone.NONE)
            val wi = wm.connectionInfo
            if (wi != null && wi.rssi != -127) {
                s.add(tx(R.string.k_signal), "${wi.rssi} dBm")
                s.add(tx(R.string.k_link_speed), "${wi.linkSpeed} Mbps")
                s.add(tx(R.string.k_frequency), "${wi.frequency} MHz")
                val ip = wi.ipAddress
                if (ip != 0) {
                    s.add(tx(R.string.k_ip_wifi), "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}")
                }
            }
        } catch (e: Throwable) {
            // ignore
        }

        if (ifaces.isNotEmpty()) {
            s.sub(tx(R.string.sub_ifaces))
            for (f in ifaces) s.add(f.first, f.second.joinToString(", "))
        }
        return s
    }

    // ------------------------------------------------------------ sim
    private fun simStateText(st: Int): String = when (st) {
        TelephonyManager.SIM_STATE_ABSENT -> tx(R.string.sim_absent)
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> tx(R.string.sim_pin)
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> tx(R.string.sim_puk)
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> tx(R.string.sim_netlock)
        TelephonyManager.SIM_STATE_READY -> tx(R.string.sim_ready)
        TelephonyManager.SIM_STATE_NOT_READY -> tx(R.string.sim_notready)
        TelephonyManager.SIM_STATE_PERM_DISABLED -> tx(R.string.sim_permdis)
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> tx(R.string.sim_ioerr)
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> tx(R.string.sim_restricted)
        else -> tx(R.string.unknown)
    }

    @Suppress("DEPRECATION")
    private fun simBlock(s: InfoSection, title: String, t: TelephonyManager, fallbackName: String?) {
        s.sub(title)
        val ss = t.simState
        s.add(tx(R.string.k_sim_state), simStateText(ss), if (ss == TelephonyManager.SIM_STATE_READY) Tone.OK else if (ss == TelephonyManager.SIM_STATE_ABSENT) Tone.NONE else Tone.WARN)
        val sn = t.simOperatorName
        s.add(tx(R.string.k_sim_op), if (sn.isNullOrBlank()) fallbackName else sn)
        s.add(tx(R.string.k_sim_code), t.simOperator)
        s.add(tx(R.string.k_sim_country), t.simCountryIso)
        s.add(tx(R.string.k_net_op), t.networkOperatorName)
        s.add(tx(R.string.k_net_code), t.networkOperator)
        s.add(tx(R.string.k_net_country), t.networkCountryIso)
        s.add(tx(R.string.k_roaming), yn(t.isNetworkRoaming))
    }

    @Suppress("DEPRECATION")
    private fun sim(ctx: Context): InfoSection {
        val s = InfoSection("sim", tx(R.string.sec_sim))
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            s.add(tx(R.string.k_status), tx(R.string.v_no_telephony))
            return s
        }
        val base = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val slots = base.phoneCount
        s.add(tx(R.string.k_sim_slots), slots)
        s.add(tx(R.string.k_phone_type), when (base.phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> tx(R.string.unknown)
        })
        var active = 0
        for (slot in 0 until slots) {
            val st = try { base.getSimState(slot) } catch (e: Throwable) { TelephonyManager.SIM_STATE_UNKNOWN }
            if (st != TelephonyManager.SIM_STATE_ABSENT && st != TelephonyManager.SIM_STATE_UNKNOWN) active++
        }
        s.add(tx(R.string.k_active_sims), active)

        // (subscriptionId, slotIndex or -1, display name)
        val subs = ArrayList<Triple<Int, Int, String?>>()
        try {
            val sm = ctx.getSystemService(SubscriptionManager::class.java)
            val list = sm?.activeSubscriptionInfoList
            if (list != null) {
                for (si in list.sortedBy { it.simSlotIndex }) {
                    subs.add(Triple(si.subscriptionId, si.simSlotIndex, si.displayName?.toString()))
                }
            }
        } catch (e: Throwable) {
            // no phone permission: probe subscription ids instead
        }
        if (subs.isEmpty()) {
            val want = if (active > 0) active else 1
            for (id in 1..16) {
                if (subs.size >= want) break
                try {
                    val t = base.createForSubscriptionId(id)
                    if (t.simOperator.isNotEmpty()) subs.add(Triple(id, -1, null))
                } catch (e: Throwable) {
                    // ignore
                }
            }
        }

        if (subs.isEmpty()) {
            simBlock(s, tx(R.string.sim_card), base, null)
        } else {
            var n = 0
            for (sub in subs) {
                n++
                val t = try { base.createForSubscriptionId(sub.first) } catch (e: Throwable) { base }
                val title = if (sub.second >= 0) tx(R.string.sim_slot_title, sub.second + 1) else tx(R.string.sim_n_title, n)
                simBlock(s, title, t, sub.third)
            }
            if (subs.size < active) {
                s.add(tx(R.string.k_note), tx(R.string.v_sim_more, active - subs.size))
            }
        }
        return s
    }

    // ------------------------------------------------------------ camera
    private fun camera(ctx: Context): InfoSection {
        val s = InfoSection("camera", tx(R.string.sec_camera))
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val listed = cm.cameraIdList.toList()
        val ids = LinkedHashSet<String>(listed)
        val physical = HashSet<String>()
        val logical = HashSet<String>()
        val hidden = HashSet<String>()

        // physical cameras behind a logical (multi-camera) device
        if (Build.VERSION.SDK_INT >= 28) {
            for (id in listed) {
                try {
                    val ph = cm.getCameraCharacteristics(id).physicalCameraIds
                    if (ph.isNotEmpty()) logical.add(id)
                    for (p in ph) {
                        physical.add(p)
                        ids.add(p)
                    }
                } catch (e: Throwable) {
                    // ignore
                }
            }
        }
        // cameras that exist but are not in the public list
        for (n in 0..63) {
            val id = n.toString()
            if (ids.contains(id)) continue
            try {
                cm.getCameraCharacteristics(id)
                ids.add(id)
                hidden.add(id)
            } catch (e: Throwable) {
                // not available
            }
        }

        s.add(tx(R.string.k_cam_found), ids.size)
        met["cameras"] = ids.size
        s.add(tx(R.string.k_cam_reported), listed.size)

        var camNo = 0
        for (id in ids) {
            camNo++
            try {
                val c = cm.getCameraCharacteristics(id)
                val facingVal = c.get(CameraCharacteristics.LENS_FACING)
                val facing = when (facingVal) {
                    CameraCharacteristics.LENS_FACING_FRONT -> tx(R.string.v_front)
                    CameraCharacteristics.LENS_FACING_BACK -> tx(R.string.v_back)
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> tx(R.string.v_external)
                    else -> tx(R.string.unknown)
                }
                s.sub(tx(R.string.sub_cam, camNo, facing))
                s.add(tx(R.string.k_sys_id), id)
                s.add(tx(R.string.k_detect_type), when {
                    logical.contains(id) -> tx(R.string.v_logical)
                    physical.contains(id) -> tx(R.string.v_physical)
                    hidden.contains(id) -> tx(R.string.v_hidden)
                    else -> tx(R.string.v_main)
                })
                val pa = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                var mp = 0.0
                if (pa != null) {
                    mp = pa.width.toDouble() * pa.height / 1e6
                    s.add(tx(R.string.k_sensor_res), "${pa.width} x ${pa.height}")
                    s.add(tx(R.string.k_megapixel), String.format(Locale.US, "%.1f MP", mp))
                    if (mp > ((met["max_mp"] as? Double) ?: 0.0)) met["max_mp"] = mp
                }
                val ps = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (ps != null) s.add(tx(R.string.k_sensor_size), String.format(Locale.US, "%.2f x %.2f mm", ps.width, ps.height))
                val fl = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (fl != null) s.add(tx(R.string.k_focal), fl.joinToString(", ") { "${it} mm" })

                // equivalent focal length and a best-guess lens type
                val f = fl?.firstOrNull()
                if (ps != null && f != null) {
                    val diag = Math.hypot(ps.width.toDouble(), ps.height.toDouble())
                    if (diag > 0) {
                        val eq = f * 43.27 / diag
                        s.add(tx(R.string.k_focal_eq), String.format(Locale.US, "%.0f mm", eq))
                        val minFocus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                        val type = when {
                            caps != null && caps.contains(8) -> tx(R.string.v_depth)
                            facingVal == CameraCharacteristics.LENS_FACING_FRONT -> tx(R.string.v_selfie)
                            minFocus != null && minFocus >= 8f && mp < 8.5 -> tx(R.string.v_macro)
                            eq < 20 -> tx(R.string.v_ultrawide)
                            eq <= 40 -> tx(R.string.v_wide)
                            else -> tx(R.string.v_tele)
                        }
                        s.add(tx(R.string.k_lens_type), type)
                    }
                }
                val ap = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                if (ap != null) s.add(tx(R.string.k_aperture), ap.joinToString(", ") { "f/${it}" })
                s.add(tx(R.string.k_flash), yn(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true))
                val ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                s.add(tx(R.string.k_ois), yn(ois != null && ois.contains(1)))
                val lvl = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                s.add(tx(R.string.k_cam2_level), when (lvl) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL 3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                    else -> tx(R.string.unknown)
                })
                s.add(tx(R.string.k_max_zoom), c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))
                s.add(tx(R.string.k_sensor_orient), c.get(CameraCharacteristics.SENSOR_ORIENTATION))
                val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val jpeg = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
                if (jpeg != null) s.add(tx(R.string.k_max_jpeg), "${jpeg.width} x ${jpeg.height}")
            } catch (e: Throwable) {
                s.add(tx(R.string.v_cam_label, camNo), tx(R.string.v_cam_failed))
            }
        }
        s.add(
            tx(R.string.k_note),
            tx(R.string.v_cam_note)
        )
        return s
    }

    // ------------------------------------------------------------ sensors
    private fun sensors(ctx: Context): InfoSection {
        val s = InfoSection("sensors", tx(R.string.sec_sensors))
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val list = sm.getSensorList(Sensor.TYPE_ALL)
        s.add(tx(R.string.k_sensor_count), list.size)
        met["sensors"] = list.size
        var n = 0
        for (sen in list) {
            n++
            s.sub("${n}. ${sen.name}")
            s.add(tx(R.string.k_manufacturer), sen.vendor)
            s.add(tx(R.string.k_s_type), sen.stringType)
            s.add(tx(R.string.k_s_version), sen.version)
            s.add(tx(R.string.k_s_max), sen.maximumRange)
            s.add(tx(R.string.k_s_res), sen.resolution)
            s.add(tx(R.string.k_s_power), "${sen.power} mA")
            s.add(tx(R.string.k_s_mindelay), "${sen.minDelay} µs")
        }
        return s
    }

    // ------------------------------------------------------------ features
    private fun features(ctx: Context): InfoSection {
        val s = InfoSection("features", tx(R.string.sec_features))
        val pm = ctx.packageManager
        val list = listOf(
            "NFC" to "android.hardware.nfc",
            "Bluetooth" to "android.hardware.bluetooth",
            "Bluetooth Low Energy" to "android.hardware.bluetooth_le",
            "Wi-Fi" to "android.hardware.wifi",
            "Wi-Fi Direct" to "android.hardware.wifi.direct",
            "Wi-Fi Aware" to "android.hardware.wifi.aware",
            tx(R.string.f_gps) to "android.hardware.location.gps",
            tx(R.string.f_telephony) to "android.hardware.telephony",
            tx(R.string.f_fingerprint) to "android.hardware.fingerprint",
            tx(R.string.f_face) to "android.hardware.biometrics.face",
            tx(R.string.f_accel) to "android.hardware.sensor.accelerometer",
            tx(R.string.f_gyro) to "android.hardware.sensor.gyroscope",
            tx(R.string.f_compass) to "android.hardware.sensor.compass",
            tx(R.string.f_baro) to "android.hardware.sensor.barometer",
            tx(R.string.f_light) to "android.hardware.sensor.light",
            tx(R.string.f_prox) to "android.hardware.sensor.proximity",
            tx(R.string.f_step) to "android.hardware.sensor.stepcounter",
            tx(R.string.f_heart) to "android.hardware.sensor.heartrate",
            tx(R.string.f_cam_back) to "android.hardware.camera",
            tx(R.string.f_cam_front) to "android.hardware.camera.front",
            tx(R.string.f_cam_flash) to "android.hardware.camera.flash",
            "USB Host" to "android.hardware.usb.host",
            "USB Accessory" to "android.hardware.usb.accessory",
            tx(R.string.f_ir) to "android.hardware.consumerir",
            tx(R.string.f_audio_ll) to "android.hardware.audio.low_latency",
            tx(R.string.f_mic) to "android.hardware.microphone",
            tx(R.string.f_vibrator) to "android.hardware.vibrator",
            "UWB" to "android.hardware.uwb",
            tx(R.string.f_aod) to "android.software.always_on_display",
            tx(R.string.f_multiwin) to "android.software.picture_in_picture",
            "eSIM" to "android.hardware.telephony.euicc",
            "Vulkan" to "android.hardware.vulkan.version"
        )
        for (p in list) {
            val has = pm.hasSystemFeature(p.second)
            s.add(p.first, yn(has), if (has) Tone.OK else Tone.BAD)
        }
        val all = pm.systemAvailableFeatures.mapNotNull { it.name }.sorted()
        s.add(tx(R.string.k_feat_count), all.size)
        s.add(tx(R.string.k_feat_list), all.joinToString(", "))
        return s
    }

    // ------------------------------------------------------------ security
    private fun security(ctx: Context): InfoSection {
        val s = InfoSection("security", tx(R.string.sec_security))
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = km.isDeviceSecure
        s.add(tx(R.string.k_screen_lock), yn(secure), if (secure) Tone.OK else Tone.WARN)
        met["secure"] = secure
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val enc = dpm.storageEncryptionStatus
        val encText: String
        var encTone = Tone.NONE
        when (enc) {
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> { encText = tx(R.string.v_unsupported); encTone = Tone.BAD }
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> { encText = tx(R.string.v_inactive); encTone = Tone.BAD }
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING -> { encText = tx(R.string.v_activating); encTone = Tone.WARN }
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> { encText = tx(R.string.v_active); encTone = Tone.OK }
            else -> encText = tx(R.string.unknown)
        }
        s.add(tx(R.string.k_encryption), encText, encTone)
        val adb = Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        val dev = Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        s.add(tx(R.string.k_dev_options), if (dev) tx(R.string.v_active) else tx(R.string.v_inactive), if (dev) Tone.WARN else Tone.NONE)
        s.add("USB Debugging (ADB)", if (adb) tx(R.string.v_active) else tx(R.string.v_inactive), if (adb) Tone.WARN else Tone.NONE)
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/local/bin/su"
        )
        val rooted = suPaths.any { File(it).exists() } || (Build.TAGS?.contains("test-keys") == true)
        met["rooted"] = rooted
        s.add(tx(R.string.k_root), if (rooted) tx(R.string.v_rooted) else tx(R.string.v_not_rooted), if (rooted) Tone.BAD else Tone.OK)
        return s
    }
}
