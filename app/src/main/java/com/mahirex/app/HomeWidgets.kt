package com.mahirex.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real Android home-screen widgets (AppWidgetProvider + RemoteViews).
 * Battery friendly by design: no always-running service, no WorkManager, no wake locks.
 *  - Auto update uses ONE non-waking alarm: it fires only while the phone is awake, refreshes local data only
 *    (no network) and is cancelled when no widget needs it.
 *  - "Measure now" / "Start test" run the real speed test in a short foreground service that stops itself;
 *    the widget shows ping, jitter, download and upload as they are measured.
 *  - The graphic widgets (Battery Orb, Storage & RAM, Device identity) draw with Canvas at a size matched to the widget,
 *    read the battery only from the sticky broadcast, update at most every 15 min (or never) and redraw only when a
 *    visible value changed. No service, no WorkManager, no network.
 */
object HomeWidgets {
    const val KIND_SPEED = 0
    const val KIND_TEST = 1
    const val KIND_HEALTH = 2
    const val KIND_ORB = 3
    const val KIND_RINGS = 4
    const val KIND_ID = 5

    const val ACTION_START_TEST = "com.mahirex.app.WIDGET_START_TEST"
    const val ACTION_TICK = "com.mahirex.app.WIDGET_TICK"
    const val ACTION_OPEN = "com.mahirex.app.OPEN"
    const val EXTRA_DEST = "dest"

    // field bits (meaning depends on the widget kind)
    const val F1 = 1
    const val F2 = 2
    const val F3 = 4

    /** automatic update choices in seconds (0 = off) */
    val INTERVALS = intArrayOf(30, 300, 1800, 0)
    const val DEFAULT_INTERVAL = 1800

    /** graphic widgets: 15 min minimum, 0 = off */
    val INTERVALS_MIN15 = intArrayOf(900, 1800, 3600, 0)
    const val MIN_GRAPHIC_INTERVAL = 900

    fun isGraphic(kind: Int): Boolean = kind >= KIND_ORB

    fun intervalsFor(kind: Int): IntArray = if (isGraphic(kind)) INTERVALS_MIN15 else INTERVALS

    // live test state (written by SpeedTestService, drawn by the speed and test widgets)
    const val PH_IDLE = 0
    const val PH_SELECT = 1
    const val PH_PING = 2
    const val PH_DOWN = 3
    const val PH_UP = 4
    const val ERR_START = 9

    private const val SIZE_SMALL = 0
    private const val SIZE_MEDIUM = 1
    private const val SIZE_LARGE = 2

    val ACCENT_LIGHT = intArrayOf(0xFF2563EB.toInt(), 0xFF0EA5E9.toInt(), 0xFF9333EA.toInt(), 0xFF0E9F6E.toInt(), 0xFFF59E0B.toInt(), 0xFFEC4899.toInt())
    val ACCENT_DARK = intArrayOf(0xFF60A5FA.toInt(), 0xFF38BDF8.toInt(), 0xFFC084FC.toInt(), 0xFF34D399.toInt(), 0xFFFBBF24.toInt(), 0xFFF472B6.toInt())
    private val PILLS = intArrayOf(
        R.drawable.widget_pill_0, R.drawable.widget_pill_1, R.drawable.widget_pill_2,
        R.drawable.widget_pill_3, R.drawable.widget_pill_4, R.drawable.widget_pill_5
    )

    /** [opacity] (20..100 %) is used by the graphic widgets only */
    class Cfg(val theme: Int, val accent: Int, val fields: Int, val interval: Int, val opacity: Int = 100)

    class Pal(val dark: Boolean, val bg: Int, val tx: Int, val mu: Int, val accent: Int)

    private fun sp(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("mahirex_widgets", Context.MODE_PRIVATE)

    fun defaultFields(kind: Int): Int = F1 or F2 or F3

    fun cfg(ctx: Context, id: Int, kind: Int): Cfg {
        val p = sp(ctx)
        var iv = p.getInt("i_$id", -1)
        if (iv < 0) iv = if (p.getBoolean("a_$id", true)) DEFAULT_INTERVAL else 0 // settings saved by the previous version
        if (isGraphic(kind) && iv in 1 until MIN_GRAPHIC_INTERVAL) iv = MIN_GRAPHIC_INTERVAL
        return Cfg(
            p.getInt("t_$id", 0).coerceIn(0, 2),
            p.getInt("c_$id", 0).coerceIn(0, ACCENT_LIGHT.size - 1),
            p.getInt("f_$id", defaultFields(kind)),
            iv,
            p.getInt("o_$id", 100).coerceIn(20, 100)
        )
    }

    fun saveCfg(ctx: Context, id: Int, c: Cfg) {
        sp(ctx).edit().putInt("t_$id", c.theme).putInt("c_$id", c.accent).putInt("f_$id", c.fields).putInt("i_$id", c.interval).putInt("o_$id", c.opacity).apply()
    }

    fun forget(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        for (id in ids) {
            e.remove("t_$id").remove("c_$id").remove("f_$id").remove("a_$id").remove("i_$id").remove("r_$id").remove("u_$id").remove("o_$id").remove("s_$id")
        }
        e.apply()
    }

    fun kindOf(ctx: Context, id: Int): Int {
        val cn = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className ?: return KIND_SPEED
        return when {
            cn.endsWith("TestWidgetProvider") -> KIND_TEST
            cn.endsWith("HealthWidgetProvider") -> KIND_HEALTH
            cn.endsWith("OrbWidgetProvider") -> KIND_ORB
            cn.endsWith("RingsWidgetProvider") -> KIND_RINGS
            cn.endsWith("IdentityWidgetProvider") -> KIND_ID
            else -> KIND_SPEED
        }
    }

    private fun providers(): List<Pair<Class<*>, Int>> = listOf(
        Pair(SpeedWidgetProvider::class.java, KIND_SPEED),
        Pair(TestWidgetProvider::class.java, KIND_TEST),
        Pair(HealthWidgetProvider::class.java, KIND_HEALTH),
        Pair(OrbWidgetProvider::class.java, KIND_ORB),
        Pair(RingsWidgetProvider::class.java, KIND_RINGS),
        Pair(IdentityWidgetProvider::class.java, KIND_ID)
    )

    /** refresh every placed widget (cheap: local data only) */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for ((cls, kind) in providers()) {
            if (isGraphic(kind)) continue // graphic widgets follow only the alarm / their own interval (speed-test pushes must not redraw them)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            for (id in ids) update(ctx, mgr, id, kind)
        }
    }

    fun hasWidgets(ctx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        for ((cls, _) in providers()) {
            if (mgr.getAppWidgetIds(ComponentName(ctx, cls)).isNotEmpty()) return true
        }
        return false
    }

    // ------------------------------------------------------------ automatic update (one non-waking alarm)
    private fun alarmIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, WidgetActionReceiver::class.java)
        i.action = ACTION_TICK
        return PendingIntent.getBroadcast(ctx, 9001, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun shortestInterval(ctx: Context): Int {
        val mgr = AppWidgetManager.getInstance(ctx)
        var best = 0
        for ((cls, kind) in providers()) {
            for (id in mgr.getAppWidgetIds(ComponentName(ctx, cls))) {
                val iv = cfg(ctx, id, kind).interval
                if (iv > 0 && (best == 0 || iv < best)) best = iv
            }
        }
        return best
    }

    /**
     * (Re)arms the alarm for the shortest interval any widget asks for, or cancels it when none does.
     * ELAPSED_REALTIME (not _WAKEUP): with the screen off nothing runs, so nothing is spent.
     */
    fun scheduleNext(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val iv = shortestInterval(ctx)
            if (iv <= 0) {
                am.cancel(alarmIntent(ctx))
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + iv * 1000L, alarmIntent(ctx))
            }
        } catch (e: Exception) {
            // widgets keep working through the system's own periodic update
        }
    }

    /** alarm fired: refresh the widgets whose interval has passed, then arm the next alarm */
    fun tick(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val now = System.currentTimeMillis()
            for ((cls, kind) in providers()) {
                for (id in mgr.getAppWidgetIds(ComponentName(ctx, cls))) {
                    val iv = cfg(ctx, id, kind).interval
                    if (iv > 0 && now - sp(ctx).getLong("u_$id", 0L) >= iv * 1000L - 3000L) update(ctx, mgr, id, kind)
                }
            }
        } finally {
            scheduleNext(ctx)
        }
    }

    // ------------------------------------------------------------ live test state
    class TestState(val phase: Int, val down: Double, val up: Double, val ping: Double, val jitter: Double, val live: Double, val err: Int, val ts: Long)

    @Volatile
    private var lastPush = 0L

    fun testBegin(ctx: Context) {
        sp(ctx).edit().putInt("lt_phase", PH_SELECT).putFloat("lt_down", -1f).putFloat("lt_up", -1f).putFloat("lt_ping", -1f)
            .putFloat("lt_jit", -1f).putFloat("lt_live", 0f).putInt("lt_err", 0).putLong("lt_ts", System.currentTimeMillis()).apply()
    }

    fun testPhase(ctx: Context, phase: Int) {
        sp(ctx).edit().putInt("lt_phase", phase).putFloat("lt_live", 0f).putLong("lt_ts", System.currentTimeMillis()).apply()
    }

    fun testLive(ctx: Context, mbps: Double) {
        sp(ctx).edit().putFloat("lt_live", mbps.toFloat()).putLong("lt_ts", System.currentTimeMillis()).apply()
    }

    fun testPing(ctx: Context, ping: Double, jitter: Double) {
        sp(ctx).edit().putFloat("lt_ping", ping.toFloat()).putFloat("lt_jit", jitter.toFloat()).apply()
    }

    fun testResult(ctx: Context, download: Boolean, mbps: Double) {
        sp(ctx).edit().putFloat(if (download) "lt_down" else "lt_up", mbps.toFloat()).apply()
    }

    fun testEnd(ctx: Context, err: Int) {
        sp(ctx).edit().putInt("lt_phase", PH_IDLE).putInt("lt_err", err).putLong("lt_ts", System.currentTimeMillis()).apply()
    }

    fun testState(ctx: Context): TestState {
        val p = sp(ctx)
        return TestState(
            p.getInt("lt_phase", PH_IDLE), p.getFloat("lt_down", -1f).toDouble(), p.getFloat("lt_up", -1f).toDouble(),
            p.getFloat("lt_ping", -1f).toDouble(), p.getFloat("lt_jit", -1f).toDouble(), p.getFloat("lt_live", 0f).toDouble(),
            p.getInt("lt_err", 0), p.getLong("lt_ts", 0L)
        )
    }

    /** redraw widgets while a test runs, at most ~1.4 times per second */
    fun pushLive(ctx: Context, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPush < 700) return
        lastPush = now
        updateAll(ctx)
    }

    // ------------------------------------------------------------ current traffic (speed widget, no sleeping, no network)
    class Rate(val down: Double, val up: Double, val at: Long)

    /**
     * Average device traffic since the previous call (>= 5 s ago), from the system counters.
     * Returns null until two samples exist, so there is never an invented number.
     */
    fun trafficRate(ctx: Context): Rate? {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        val p = sp(ctx)
        val now = SystemClock.elapsedRealtime()
        val pt = p.getLong("tr_t", -1L)
        val age = now - pt
        if (pt < 0 || age < 0 || age > 20 * 60 * 1000L) {
            p.edit().putLong("tr_rx", rx).putLong("tr_tx", tx).putLong("tr_t", now).remove("tr_ts").apply()
            return null
        }
        if (age < 5000) {
            val ts = p.getLong("tr_ts", 0L)
            return if (ts > 0) Rate(p.getFloat("tr_d", 0f).toDouble(), p.getFloat("tr_u", 0f).toDouble(), ts) else null
        }
        val secs = age / 1000.0
        val d = Math.max(0L, rx - p.getLong("tr_rx", rx)) * 8.0 / secs / 1e6
        val u = Math.max(0L, tx - p.getLong("tr_tx", tx)) * 8.0 / secs / 1e6
        val ts = System.currentTimeMillis()
        p.edit().putLong("tr_rx", rx).putLong("tr_tx", tx).putLong("tr_t", now)
            .putFloat("tr_d", d.toFloat()).putFloat("tr_u", u.toFloat()).putLong("tr_ts", ts).apply()
        return Rate(d, u, ts)
    }

    // ------------------------------------------------------------ helpers
    private fun localized(ctx: Context): Context {
        val tag = Prefs.lang()
        if (tag.isEmpty()) return ctx
        val c = Configuration(ctx.resources.configuration)
        c.setLocale(Locale.forLanguageTag(tag))
        return ctx.createConfigurationContext(c)
    }

    private fun isDark(ctx: Context, theme: Int): Boolean {
        if (theme == 1) return false
        if (theme == 2) return true
        val m = Prefs.nightMode()
        if (m == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) return true
        if (m == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) return false
        return (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Colours of a widget. The chosen colour tints the whole widget (background, numbers, buttons, rings),
     * so changing it is clearly visible and not only a thin accent line.
     */
    fun palette(ctx: Context, theme: Int, accentIdx: Int): Pal {
        val dark = isDark(ctx, theme)
        val i = accentIdx.coerceIn(0, ACCENT_LIGHT.size - 1)
        val accent = (if (dark) ACCENT_DARK else ACCENT_LIGHT)[i]
        val base = if (dark) 0xFF141A36.toInt() else 0xFFFFFFFF.toInt()
        val bg = ColorUtils.blendARGB(base, (if (dark) ACCENT_LIGHT else ACCENT_LIGHT)[i], if (dark) 0.34f else 0.16f)
        val tx = if (dark) 0xFFF4F5FF.toInt() else 0xFF12183A.toInt()
        val mu = if (dark) 0xFFC3CAEA.toInt() else 0xFF4B557F.toInt()
        return Pal(dark, bg, tx, mu, accent)
    }

    private fun sizeClass(mgr: AppWidgetManager, id: Int): Int {
        val o: Bundle = mgr.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        return when {
            h < 100 -> SIZE_SMALL
            w >= 240 -> SIZE_LARGE
            else -> SIZE_MEDIUM
        }
    }

    /** opens the app directly on [dest]. The data URI makes every button a different PendingIntent. */
    private fun openIntent(ctx: Context, dest: String, auto: Boolean, widgetId: Int, slot: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.action = ACTION_OPEN
        i.data = Uri.parse("mahirex://widget/$dest/$widgetId/$slot")
        i.putExtra(EXTRA_DEST, dest)
        i.putExtra("auto", auto)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, ("$dest/$widgetId/$slot").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** the button starts the test through an invisible activity: the app never opens */
    private fun startTestIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, WidgetTestActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        return PendingIntent.getActivity(ctx, 7001, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun num(v: Double): String = when {
        v >= 100 -> String.format(Locale.US, "%.0f", v)
        v >= 10 -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }

    private fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    private fun battery(ctx: Context): Triple<Int, Boolean, Float> {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (i == null) return Triple(-1, false, -1f)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return Triple(pct, charging, if (temp >= 0) temp / 10f else -1f)
    }

    private fun storagePct(): Int {
        return try {
            val s = StatFs(Environment.getDataDirectory().path)
            val total = s.totalBytes
            if (total <= 0) -1 else (((total - s.availableBytes) * 100) / total).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun ring(pct: Int, color: Int, track: Int, textColor: Int, label: String): Bitmap {
        val size = 150
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 14f
        p.strokeCap = Paint.Cap.ROUND
        val r = RectF(14f, 14f, size - 14f, size - 14f)
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        if (pct > 0) {
            p.color = color
            c.drawArc(r, -90f, 360f * pct.coerceAtMost(100) / 100f, false, p)
        }
        val t = Paint(Paint.ANTI_ALIAS_FLAG)
        t.color = textColor
        t.textAlign = Paint.Align.CENTER
        t.typeface = Typeface.DEFAULT_BOLD
        t.textSize = 38f
        val fm = t.fontMetrics
        c.drawText(label, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, t)
        return b
    }

    private fun phaseLabel(ctx: Context, phase: Int): String = when (phase) {
        PH_SELECT -> ctx.getString(R.string.sp_phase_select)
        PH_PING -> ctx.getString(R.string.sp_phase_ping)
        PH_DOWN -> ctx.getString(R.string.sp_phase_down)
        PH_UP -> ctx.getString(R.string.sp_phase_up)
        else -> ""
    }

    private fun errLabel(ctx: Context, err: Int): String = when (err) {
        SpeedRunner.ERR_OFFLINE -> ctx.getString(R.string.sp_err_offline)
        SpeedRunner.ERR_SERVER -> ctx.getString(R.string.sp_err_server)
        SpeedRunner.ERR_DOWNLOAD -> ctx.getString(R.string.sp_err_down)
        SpeedRunner.ERR_CANCEL -> ctx.getString(R.string.sp_err_cancel)
        ERR_START -> ctx.getString(R.string.wd_err_start)
        else -> ""
    }

    // ------------------------------------------------------------ rendering
    /** [force]: redraw even if no visible value changed (graphic widgets skip unchanged redraws otherwise) */
    fun update(ctx0: Context, mgr: AppWidgetManager, id: Int, kind: Int, force: Boolean = false) {
        if (isGraphic(kind)) {
            updateGraphic(ctx0, mgr, id, kind, force)
            return
        }
        val ctx = localized(ctx0.applicationContext)
        val cfg = cfg(ctx0, id, kind)
        val pal = palette(ctx, cfg.theme, cfg.accent)
        val dark = pal.dark
        val accent = pal.accent
        val tx = pal.tx
        val mu = pal.mu
        val size = sizeClass(mgr, id)
        val bad = 0xFFEF4444.toInt()

        val layout = when (kind) {
            KIND_TEST -> R.layout.widget_test
            KIND_HEALTH -> R.layout.widget_health
            else -> R.layout.widget_speed
        }
        val v = RemoteViews(ctx0.packageName, layout)
        v.setInt(R.id.w_bg, "setColorFilter", pal.bg)
        v.setInt(R.id.w_bg, "setImageAlpha", if (dark) 245 else 250)

        fun txt(viewId: Int, text: String, color: Int, sp: Float? = null) {
            v.setTextViewText(viewId, text)
            v.setTextColor(viewId, color)
            if (sp != null) v.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, sp)
        }

        fun show(viewId: Int, on: Boolean) = v.setViewVisibility(viewId, if (on) View.VISIBLE else View.GONE)

        fun pillBtn(viewId: Int, label: String, pi: PendingIntent) {
            v.setTextViewText(viewId, label)
            v.setTextColor(viewId, accent)
            v.setInt(viewId, "setBackgroundResource", PILLS[cfg.accent])
            v.setOnClickPendingIntent(viewId, pi)
        }

        val small = size == SIZE_SMALL
        val ts = testState(ctx0)
        val running = ts.phase != PH_IDLE && System.currentTimeMillis() - ts.ts < 150000L
        val failed = !running && ts.err != 0 && System.currentTimeMillis() - ts.ts < 90000L
        val vs = if (small) 18f else 22f

        when (kind) {
            KIND_SPEED -> {
                val last = SpeedStore.last(ctx0)
                val rate = if (cfg.interval > 0) trafficRate(ctx0) else null
                val rateFresh = rate != null && System.currentTimeMillis() - rate.at < cfg.interval * 3000L + 60000L
                var d: Double? = null
                var u: Double? = null
                var sub = ctx.getString(R.string.wd_tap_measure)
                var subColor = mu
                if (running) {
                    d = if (ts.phase == PH_DOWN) ts.live else if (ts.down >= 0) ts.down else null
                    u = if (ts.phase == PH_UP) ts.live else null
                    sub = if (ts.ping >= 0) ctx.getString(R.string.wd_ping_jit, num(ts.ping), num(ts.jitter)) else phaseLabel(ctx, ts.phase)
                    subColor = accent
                } else if (failed) {
                    d = last?.down
                    u = if (last != null && last.up >= 0) last.up else null
                    sub = errLabel(ctx, ts.err)
                    subColor = bad
                } else if (rateFresh && rate != null) {
                    d = rate.down
                    u = rate.up
                    sub = ctx.getString(R.string.wd_live_at, hm(rate.at))
                } else if (last != null) {
                    d = last.down
                    u = if (last.up >= 0) last.up else null
                    sub = ctx.getString(R.string.wd_ping_jit_at, num(last.ping), num(last.jitter), hm(last.time))
                }
                val online = Net.isOnline(ctx0)
                val tr = when (Net.transport(ctx0)) {
                    Net.T_WIFI -> ctx.getString(R.string.sp_net_wifi)
                    Net.T_CELL -> ctx.getString(R.string.sp_net_cell)
                    Net.T_ETH -> ctx.getString(R.string.sp_net_eth)
                    Net.T_VPN -> ctx.getString(R.string.sp_net_vpn)
                    else -> ctx.getString(R.string.sp_net_other)
                }
                val status = if (running) phaseLabel(ctx, ts.phase) else if (online) tr + " · " + ctx.getString(R.string.wd_online) else ctx.getString(R.string.sp_net_none)
                txt(R.id.w_title, ctx.getString(R.string.wd_speed_title), accent)
                txt(R.id.w_status, status, if (running) accent else if (online) mu else bad)
                txt(R.id.w_down_lbl, if (small) "↓ Mbps" else "↓ " + ctx.getString(R.string.sp_download), mu)
                txt(R.id.w_down, if (d == null) "—" else num(d), accent, vs)
                txt(R.id.w_down_unit, "Mbps", mu)
                txt(R.id.w_up_lbl, if (small) "↑ Mbps" else "↑ " + ctx.getString(R.string.sp_upload), mu)
                txt(R.id.w_up, if (u == null) "—" else num(u), accent, vs)
                txt(R.id.w_up_unit, "Mbps", mu)
                txt(R.id.w_sub, sub, subColor)
                show(R.id.w_head, !small)
                show(R.id.w_down_unit, !small)
                show(R.id.w_up_unit, !small)
                show(R.id.w_down_box, cfg.fields and F1 != 0)
                show(R.id.w_up_box, cfg.fields and F2 != 0)
                show(R.id.w_status, cfg.fields and F3 != 0 || running)
                show(R.id.w_sub, !small)
                show(R.id.w_btns, !small)
                show(R.id.w_b2, size == SIZE_LARGE)
                pillBtn(R.id.w_b1, if (running) phaseLabel(ctx, ts.phase) else ctx.getString(R.string.wd_measure), startTestIntent(ctx0))
                pillBtn(R.id.w_b2, ctx.getString(R.string.sp_title), openIntent(ctx0, "speed", false, id, "b2"))
                v.setOnClickPendingIntent(R.id.w_content, openIntent(ctx0, "speed", false, id, "body"))
            }
            KIND_TEST -> {
                val last = SpeedStore.last(ctx0)
                var d: Double? = last?.down
                var u: Double? = if (last != null && last.up >= 0) last.up else null
                var p: Double? = last?.ping
                var j: Double? = last?.jitter
                var whenTxt = if (last != null) hm(last.time) else ""
                var whenColor = mu
                if (running) {
                    d = if (ts.phase == PH_DOWN) ts.live else if (ts.down >= 0) ts.down else null
                    u = if (ts.phase == PH_UP) ts.live else null
                    p = if (ts.ping >= 0) ts.ping else null
                    j = if (ts.jitter >= 0) ts.jitter else null
                    whenTxt = phaseLabel(ctx, ts.phase)
                    whenColor = accent
                } else if (failed) {
                    whenTxt = errLabel(ctx, ts.err)
                    whenColor = bad
                }
                txt(R.id.w_title, ctx.getString(R.string.wd_test_title), accent)
                txt(R.id.w_when, whenTxt, whenColor)
                txt(R.id.w_down_lbl, if (small) "↓ Mbps" else "↓ " + ctx.getString(R.string.sp_download), mu)
                txt(R.id.w_down, if (d == null) "—" else num(d), accent, vs)
                txt(R.id.w_down_unit, "Mbps", mu)
                txt(R.id.w_up_lbl, if (small) "↑ Mbps" else "↑ " + ctx.getString(R.string.sp_upload), mu)
                txt(R.id.w_up, if (u == null) "—" else num(u), accent, vs)
                txt(R.id.w_up_unit, "Mbps", mu)
                txt(R.id.w_ping_lbl, ctx.getString(R.string.sp_ping), mu)
                txt(R.id.w_ping, if (p == null) "—" else String.format(Locale.US, "%.0f", p), tx, vs)
                txt(R.id.w_ping_unit, "ms", mu)
                txt(R.id.w_jit_lbl, ctx.getString(R.string.sp_jitter), mu)
                txt(R.id.w_jit, if (j == null) "—" else String.format(Locale.US, "%.1f", j), tx, vs)
                txt(R.id.w_jit_unit, "ms", mu)
                val showDown = cfg.fields and F1 != 0
                val showUp = cfg.fields and F2 != 0
                val showRow2 = cfg.fields and F3 != 0 && (!small || (!showDown && !showUp))
                show(R.id.w_head, !small || running || failed)
                show(R.id.w_down_box, showDown)
                show(R.id.w_up_box, showUp)
                show(R.id.w_vals, showDown || showUp)
                show(R.id.w_vals2, showRow2)
                show(R.id.w_down_unit, !small)
                show(R.id.w_up_unit, !small)
                show(R.id.w_ping_unit, !small)
                show(R.id.w_jit_unit, !small)
                show(R.id.w_btns, !small)
                pillBtn(R.id.w_b1, if (running) phaseLabel(ctx, ts.phase) else ctx.getString(R.string.wd_start_test), startTestIntent(ctx0))
                v.setOnClickPendingIntent(R.id.w_content, openIntent(ctx0, "speed", false, id, "body"))
            }
            else -> {
                val b = battery(ctx0)
                val sto = storagePct()
                val trackC = if (dark) 0x44FFFFFF else 0x26000000
                val batColor = if (b.first in 0..15 && !b.second) bad else accent
                val stoColor = if (sto >= 90) bad else accent
                val batTxt = if (b.first >= 0) b.first.toString() + "%" else "—"
                val stoTxt = if (sto >= 0) "$sto%" else "—"
                txt(R.id.w_title, ctx.getString(R.string.wd_health_title), accent)
                txt(R.id.w_when, "", mu)
                txt(R.id.w_line, ctx.getString(R.string.wd_health_line, batTxt, stoTxt), tx)
                v.setImageViewBitmap(R.id.w_ring1, ring(b.first.coerceAtLeast(0), batColor, trackC, tx, batTxt))
                v.setImageViewBitmap(R.id.w_ring2, ring(sto.coerceAtLeast(0), stoColor, trackC, tx, stoTxt))
                txt(R.id.w_ring1_lbl, ctx.getString(R.string.sec_battery) + if (b.second) " ⚡" else "", mu)
                txt(R.id.w_ring2_lbl, ctx.getString(R.string.sec_storage), mu)
                show(R.id.w_head, size == SIZE_LARGE)
                show(R.id.w_line, small)
                show(R.id.w_rings, !small)
                show(R.id.w_r1_box, cfg.fields and F1 != 0)
                show(R.id.w_r2_box, cfg.fields and F2 != 0)
                show(R.id.w_btns, size == SIZE_LARGE && cfg.fields and F3 != 0)
                pillBtn(R.id.w_b1, ctx.getString(R.string.tool_cleaner), openIntent(ctx0, "cleaner", false, id, "b1"))
                pillBtn(R.id.w_b2, ctx.getString(R.string.tool_storage), openIntent(ctx0, "storage", false, id, "b2"))
                pillBtn(R.id.w_b3, ctx.getString(R.string.tool_battery), openIntent(ctx0, "battery", false, id, "b3"))
                v.setOnClickPendingIntent(R.id.w_content, openIntent(ctx0, "health", false, id, "body"))
            }
        }
        sp(ctx0).edit().putBoolean("r_$id", true).putLong("u_$id", System.currentTimeMillis()).apply()
        mgr.updateAppWidget(id, v)
    }

    // ------------------------------------------------------------ graphic widgets: Battery Orb, Storage & RAM, Device identity
    internal class Bat(val pct: Int, val status: Int, val tempC: Float?, val mv: Int?) {
        val charging: Boolean get() = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val low: Boolean get() = pct in 0..15 && !charging
    }

    internal class Mem(val stoPct: Int, val stoUsed: Long, val stoTotal: Long, val ramPct: Int, val ramUsed: Long, val ramTotal: Long)

    private class Snap(val bat: Bat?, val mem: Mem?, val title: String)

    private class Dim(val wDp: Int, val hDp: Int) {
        val wide: Boolean get() = wDp >= 200
        val tall: Boolean get() = hDp >= 100
    }

    /** battery from the sticky broadcast only: nothing is registered, nothing keeps running */
    internal fun batSnap(ctx: Context): Bat {
        val i: Intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return Bat(-1, BatteryManager.BATTERY_STATUS_UNKNOWN, null, null)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        return Bat(
            if (level >= 0 && scale > 0) level * 100 / scale else -1,
            i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            if (temp != Int.MIN_VALUE) temp / 10f else null,
            if (volt > 0) volt else null
        )
    }

    internal fun memSnap(ctx: Context): Mem {
        val st = Dev.storage(ctx)
        val mi = Dev.ram(ctx)
        val ramUsed = Math.max(0L, mi.totalMem - mi.availMem)
        return Mem(
            if (st.total > 0) Math.round(st.usedPct) else -1, st.used, st.total,
            if (mi.totalMem > 0) Math.round(ramUsed * 100f / mi.totalMem) else -1, ramUsed, mi.totalMem
        )
    }

    /** official name (Task 2, no network here); otherwise manufacturer + model */
    internal fun deviceTitle(ctx: Context): String {
        DeviceNames.official(ctx)?.let { return it }
        val man = (Build.MANUFACTURER ?: "").trim().replaceFirstChar { it.uppercase() }
        val mod = (Build.MODEL ?: "").trim()
        return if (man.isEmpty() || mod.startsWith(man, true)) mod else "$man $mod"
    }

    internal fun stateText(ctx: Context, b: Bat): String = when (b.status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ctx.getString(R.string.bat_charging)
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ctx.getString(R.string.bat_discharging)
        BatteryManager.BATTERY_STATUS_FULL -> ctx.getString(R.string.bat_full)
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ctx.getString(R.string.bat_not_charging)
        else -> "—"
    }

    internal fun tempText(b: Bat): String = if (b.tempC == null) "—" else String.format(Locale.US, "%.1f °C", b.tempC)

    internal fun voltText(b: Bat): String = if (b.mv == null) "—" else String.format(Locale.US, "%.2f V", b.mv / 1000.0)

    /** "79.4 / 128 GB" (the unit is written once when both sides share it) */
    internal fun usedTotal(used: Long, total: Long): String {
        val u = Fmt.sizeShort(used)
        val t = Fmt.sizeShort(total)
        val uu = u.substringAfter(' ')
        return if (uu == t.substringAfter(' ')) u.substringBefore(' ') + " / " + t else "$u / $t"
    }

    private fun partner(accent: Int, dark: Boolean): Int =
        ColorUtils.blendARGB(accent, if (dark) 0xFFA78BFA.toInt() else 0xFF7C3AED.toInt(), 0.5f)

    private fun accentAt(i: Int, dark: Boolean): Int = (if (dark) ACCENT_DARK else ACCENT_LIGHT)[((i % ACCENT_LIGHT.size) + ACCENT_LIGHT.size) % ACCENT_LIGHT.size]

    private fun trackColor(dark: Boolean): Int = if (dark) 0x33FFFFFF else 0x1F000000

    /** colours of the ring of a value: [accent] -> partner, or orange -> red when it is in a warning state */
    private fun ringColors(accent: Int, dark: Boolean, warn: Boolean, charging: Boolean): IntArray = when {
        warn -> intArrayOf(0xFFF97316.toInt(), 0xFFEF4444.toInt())
        charging -> intArrayOf(accent, (if (dark) 0xFF34D399 else 0xFF0E9F6E).toInt())
        else -> intArrayOf(accent, partner(accent, dark))
    }

    /** round-capped arc with a gradient stroke and a soft glow; starts at the top and runs clockwise */
    private fun gradArc(c: Canvas, r: RectF, stroke: Float, frac: Float, cols: IntArray, track: Int, glowAlpha: Int, glowR: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        if (frac <= 0f) return
        val f = frac.coerceIn(0.02f, 1f)
        val cx = r.centerX()
        val cy = r.centerY()
        val sg = if (f >= 0.995f) SweepGradient(cx, cy, intArrayOf(cols[0], cols[1], cols[0]), floatArrayOf(0f, 0.5f, 1f))
        else SweepGradient(cx, cy, intArrayOf(cols[0], cols[1], cols[1]), floatArrayOf(0f, f, 1f))
        val m = Matrix()
        m.postRotate(-90f, cx, cy)
        sg.setLocalMatrix(m)
        val g = Paint(p)
        g.shader = sg
        g.maskFilter = BlurMaskFilter(glowR, BlurMaskFilter.Blur.NORMAL)
        g.alpha = glowAlpha
        c.drawArc(r, -90f, 360f * f, false, g)
        p.shader = sg
        c.drawArc(r, -90f, 360f * f, false, p)
    }

    /** Battery Orb graphic (rings only; the numbers are real TextViews so text stays crisp and RTL-correct) */
    internal fun orbBitmap(px: Int, pct: Int, charging: Boolean, pal: Pal): Bitmap {
        val s = px.toFloat()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val stroke = s * 0.085f
        val glowR = s * 0.035f
        val inset = stroke / 2f + glowR * 1.8f
        val r = RectF(inset, inset, s - inset, s - inset)
        val cols = ringColors(pal.accent, pal.dark, pct in 0..15 && !charging, charging)
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = r.width() / 2f - stroke / 2f
        body.shader = RadialGradient(
            s / 2f, s / 2f, rad,
            intArrayOf(ColorUtils.setAlphaComponent(cols[0], if (pal.dark) 70 else 40), ColorUtils.setAlphaComponent(cols[1], 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, rad, body)
        val guide = Paint(Paint.ANTI_ALIAS_FLAG)
        guide.style = Paint.Style.STROKE
        guide.strokeWidth = Math.max(1f, s * 0.006f)
        guide.color = if (pal.dark) 0x22FFFFFF else 0x14000000
        c.drawCircle(s / 2f, s / 2f, rad * 0.82f, guide)
        gradArc(c, r, stroke, if (pct > 0) pct.coerceAtMost(100) / 100f else 0f, cols, trackColor(pal.dark), if (pal.dark) 150 else 90, glowR)
        return b
    }

    /** Storage (outer) and RAM (inner) as concentric gradient rings; a null value is not drawn (a single ring is drawn outer) */
    internal fun dualBitmap(px: Int, storagePct: Int?, ramPct: Int?, pal: Pal, accentIdx: Int): Bitmap {
        val s = px.toFloat()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val stroke = s * (if (storagePct != null && ramPct != null) 0.085f else 0.11f)
        val gap = s * 0.04f
        val glowR = s * 0.03f
        var inset = stroke / 2f + glowR * 1.8f
        val items = ArrayList<Pair<Int, IntArray>>()
        if (storagePct != null) items.add(Pair(storagePct, ringColors(pal.accent, pal.dark, storagePct >= 90, false)))
        if (ramPct != null) items.add(Pair(ramPct, ringColors(accentAt(accentIdx + 2, pal.dark), pal.dark, ramPct >= 90, false)))
        if (items.isEmpty()) return b
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(ColorUtils.setAlphaComponent(items[0].second[0], if (pal.dark) 46 else 26), ColorUtils.setAlphaComponent(items[0].second[1], 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, s / 2f, glow)
        for (item in items) {
            val r = RectF(inset, inset, s - inset, s - inset)
            gradArc(c, r, stroke, if (item.first > 0) item.first.coerceAtMost(100) / 100f else 0f, item.second, trackColor(pal.dark), if (pal.dark) 140 else 80, glowR)
            inset += stroke + gap
        }
        return b
    }

    /** mini battery bar; [rtl] fills it from the right edge */
    internal fun barBitmap(w: Int, h: Int, pct: Int, pal: Pal, low: Boolean, rtl: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = h / 2f
        p.color = trackColor(pal.dark)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, p)
        if (pct > 0) {
            val cols = ringColors(pal.accent, pal.dark, low, false)
            val fw = Math.max(h.toFloat(), w * pct.coerceAtMost(100) / 100f)
            val left = if (rtl) w - fw else 0f
            p.shader = LinearGradient(left, 0f, left + fw, 0f, if (rtl) cols[1] else cols[0], if (rtl) cols[0] else cols[1], Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(left, 0f, left + fw, h.toFloat()), rad, rad, p)
        }
        return b
    }

    private fun dims(mgr: AppWidgetManager, id: Int): Dim {
        val o: Bundle = mgr.getAppWidgetOptions(id)
        return Dim(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110))
    }

    /** bitmap side in pixels for a widget of [dp]: its real size, never above [cap] (keeps the RemoteViews small) */
    private fun px(ctx: Context, dp: Int, cap: Int, floor: Int): Int =
        (dp * ctx.resources.displayMetrics.density).toInt().coerceIn(floor, cap)

    private fun fieldOn(cfg: Cfg, bit: Int): Boolean = cfg.fields and bit != 0

    /** every value that is visible on the widget; when it equals the last drawn one nothing is redrawn */
    private fun keyFor(ctx: Context, kind: Int, cfg: Cfg, pal: Pal, d: Dim, s: Snap): String {
        val k = StringBuilder()
        k.append(kind).append('|').append(pal.dark).append('|').append(cfg.accent).append('|').append(cfg.fields).append('|')
            .append(cfg.opacity).append('|').append(Prefs.lang()).append('|').append(d.wDp).append('x').append(d.hDp).append('|')
        when (kind) {
            KIND_ORB -> {
                val b = s.bat!!
                k.append(b.pct).append(b.charging).append(b.low)
                if (fieldOn(cfg, F1)) k.append('|').append(stateText(ctx, b))
                if (fieldOn(cfg, F2)) k.append('|').append(tempText(b))
                if (fieldOn(cfg, F3)) k.append('|').append(voltText(b))
            }
            KIND_RINGS -> {
                val m = s.mem!!
                k.append(m.stoPct).append('|').append(m.ramPct)
                if (fieldOn(cfg, F3)) k.append('|').append(usedTotal(m.stoUsed, m.stoTotal)).append('|').append(usedTotal(m.ramUsed, m.ramTotal))
            }
            else -> {
                val b = s.bat!!
                k.append(s.title).append('|').append(b.pct).append(b.low)
            }
        }
        return k.toString()
    }

    private fun updateGraphic(ctx0: Context, mgr: AppWidgetManager, id: Int, kind: Int, force: Boolean) {
        val ctx = localized(ctx0.applicationContext)
        val cfg = cfg(ctx0, id, kind)
        val pal = palette(ctx, cfg.theme, cfg.accent)
        val d = dims(mgr, id)
        val snap = Snap(
            if (kind == KIND_RINGS) null else batSnap(ctx0),
            if (kind == KIND_RINGS) memSnap(ctx0) else null,
            if (kind == KIND_ID) deviceTitle(ctx0) else ""
        )
        val key = keyFor(ctx, kind, cfg, pal, d, snap)
        val p = sp(ctx0)
        val now = System.currentTimeMillis()
        if (!force && p.getBoolean("r_$id", false) && key == p.getString("s_$id", null)) {
            // nothing visible changed: keep the existing picture, only remember that this interval has been served
            p.edit().putLong("u_$id", now).apply()
            return
        }
        val v = RemoteViews(
            ctx0.packageName,
            when (kind) {
                KIND_ORB -> R.layout.widget_orb
                KIND_RINGS -> if (d.wide) R.layout.widget_rings_wide else R.layout.widget_rings
                else -> R.layout.widget_identity
            }
        )
        val a = cfg.opacity.coerceIn(20, 100)
        v.setInt(R.id.w_bg, "setColorFilter", pal.bg)
        v.setInt(R.id.w_bg, "setImageAlpha", a * 255 / 100)
        v.setInt(R.id.w_sheen, "setImageAlpha", a * (if (pal.dark) 150 else 110) / 100)
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        when (kind) {
            KIND_ORB -> drawOrb(ctx0, ctx, v, id, cfg, pal, d, snap.bat!!)
            KIND_RINGS -> drawRings(ctx0, ctx, v, id, cfg, pal, d, snap.mem!!)
            else -> drawIdentity(ctx0, ctx, v, id, cfg, pal, d, snap.bat!!, snap.title, rtl)
        }
        mgr.updateAppWidget(id, v)
        p.edit().putBoolean("r_$id", true).putLong("u_$id", now).putString("s_$id", key).apply()
    }

    private fun RemoteViews.txt(viewId: Int, text: String, color: Int, size: Float? = null) {
        setTextViewText(viewId, text)
        setTextColor(viewId, color)
        if (size != null) setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun RemoteViews.show(viewId: Int, on: Boolean) = setViewVisibility(viewId, if (on) View.VISIBLE else View.GONE)

    private fun drawOrb(ctx0: Context, ctx: Context, v: RemoteViews, id: Int, cfg: Cfg, pal: Pal, d: Dim, b: Bat) {
        val size = px(ctx0, Math.min(d.wDp, d.hDp), 360, 160)
        v.setImageViewBitmap(R.id.w_orb, orbBitmap(size, b.pct, b.charging, pal))
        val showState = fieldOn(cfg, F1)
        val showTemp = fieldOn(cfg, F2)
        val showVolt = fieldOn(cfg, F3)
        v.txt(R.id.w_pct, if (b.pct >= 0) b.pct.toString() + "%" else "—", pal.tx, if (!d.tall) 16f else if (d.wide) 24f else 22f)
        // 2x2: state inside the ring, temperature / voltage under it. 4x2: three rows beside the ring.
        v.txt(R.id.w_state, stateText(ctx, b), if (b.low) 0xFFEF4444.toInt() else pal.accent)
        v.show(R.id.w_state, showState && d.tall && !d.wide)
        val extra = ArrayList<String>()
        if (showTemp) extra.add(tempText(b))
        if (showVolt) extra.add(voltText(b))
        v.txt(R.id.w_extra, extra.joinToString("  ·  "), pal.mu)
        v.show(R.id.w_extra, extra.isNotEmpty() && d.tall && !d.wide)
        v.show(R.id.w_info, d.wide)
        v.txt(R.id.w_title, ctx.getString(R.string.wd_orb_title), pal.accent)
        v.show(R.id.w_title, d.tall)
        v.txt(R.id.w_r1_lbl, ctx.getString(R.string.bat_status), pal.mu)
        v.txt(R.id.w_r1_val, stateText(ctx, b), if (b.low) 0xFFEF4444.toInt() else pal.tx)
        v.txt(R.id.w_r2_lbl, ctx.getString(R.string.bat_temp), pal.mu)
        v.txt(R.id.w_r2_val, tempText(b), pal.tx)
        v.txt(R.id.w_r3_lbl, ctx.getString(R.string.bat_voltage), pal.mu)
        v.txt(R.id.w_r3_val, voltText(b), pal.tx)
        v.show(R.id.w_r1, showState)
        v.show(R.id.w_r2, showTemp)
        v.show(R.id.w_r3, showVolt)
        v.setOnClickPendingIntent(R.id.w_content, openIntent(ctx0, "battery", false, id, "body"))
    }

    private fun drawRings(ctx0: Context, ctx: Context, v: RemoteViews, id: Int, cfg: Cfg, pal: Pal, d: Dim, m: Mem) {
        var showSto = fieldOn(cfg, F1)
        var showRam = fieldOn(cfg, F2)
        if (!showSto && !showRam) {
            showSto = true
            showRam = true
        }
        val showLbl = fieldOn(cfg, F3)
        val size = px(ctx0, Math.min(d.wDp, d.hDp), 360, 160)
        v.setImageViewBitmap(R.id.w_dual, dualBitmap(size, if (showSto) m.stoPct.coerceAtLeast(0) else null, if (showRam) m.ramPct.coerceAtLeast(0) else null, pal, cfg.accent))
        val stoCol = ringColors(pal.accent, pal.dark, m.stoPct >= 90, false)[0]
        val ramCol = ringColors(accentAt(cfg.accent + 2, pal.dark), pal.dark, m.ramPct >= 90, false)[0]
        // a lone ring is drawn in the outer position, so its legend dot uses the outer colour too
        val ramDot = if (showSto) ramCol else stoCol
        val sPct = if (m.stoPct >= 0) m.stoPct.toString() + "%" else "—"
        val rPct = if (m.ramPct >= 0) m.ramPct.toString() + "%" else "—"
        val sSub = if (showLbl && m.stoTotal > 0) usedTotal(m.stoUsed, m.stoTotal) else sPct
        val rSub = if (showLbl && m.ramTotal > 0) usedTotal(m.ramUsed, m.ramTotal) else rPct
        v.txt(R.id.w_title, ctx.getString(R.string.wd_rings_title), pal.accent)
        v.show(R.id.w_title, d.tall && d.wide)
        v.setInt(R.id.w_l1_dot, "setColorFilter", stoCol)
        v.setInt(R.id.w_l2_dot, "setColorFilter", ramDot)
        v.txt(R.id.w_l1_txt, ctx.getString(R.string.sec_storage) + "  " + sPct, pal.tx)
        v.txt(R.id.w_l2_txt, ctx.getString(R.string.sec_ram) + "  " + rPct, pal.tx)
        v.txt(R.id.w_l1_sub, sSub, pal.mu)
        v.txt(R.id.w_l2_sub, rSub, pal.mu)
        v.show(R.id.w_l1_sub, showLbl || !d.wide)
        v.show(R.id.w_l2_sub, showLbl || !d.wide)
        v.show(R.id.w_l1_box, showSto)
        v.show(R.id.w_l2_box, showRam)
        v.show(R.id.w_legend, d.tall)
        v.setOnClickPendingIntent(R.id.w_content, openIntent(ctx0, "storage", false, id, "body"))
        v.setOnClickPendingIntent(R.id.w_l1_box, openIntent(ctx0, "storage", false, id, "l1"))
        v.setOnClickPendingIntent(R.id.w_l2_box, openIntent(ctx0, "ram", false, id, "l2"))
    }

    private fun drawIdentity(ctx0: Context, ctx: Context, v: RemoteViews, id: Int, cfg: Cfg, pal: Pal, d: Dim, b: Bat, title: String, rtl: Boolean) {
        v.txt(R.id.w_title, ctx.getString(R.string.wd_id_title), pal.accent)
        v.show(R.id.w_title, d.tall)
        v.txt(R.id.w_name, title, pal.tx, if (d.wide) 20f else 16f)
        v.txt(R.id.w_code, Build.MODEL ?: "", pal.mu)
        v.show(R.id.w_code, fieldOn(cfg, F3) && d.tall)
        v.txt(R.id.w_android, "Android " + Build.VERSION.RELEASE + "  ·  API " + Build.VERSION.SDK_INT, pal.tx)
        v.show(R.id.w_android, fieldOn(cfg, F1))
        val showBar = fieldOn(cfg, F2) && b.pct >= 0
        v.show(R.id.w_bat_row, showBar)
        if (showBar) {
            v.txt(R.id.w_bat_lbl, ctx.getString(R.string.sec_battery) + "  " + b.pct + "%" + if (b.charging) "  ⚡" else "", pal.mu)
            val bw = px(ctx0, Math.max(60, d.wDp - 24), 480, 200)
            val bh = px(ctx0, 10, 28, 14)
            v.setImageViewBitmap(R.id.w_bat_bar, barBitmap(bw, bh, b.pct, pal, b.low, rtl))
        }
        v.setOnClickPendingIntent(R.id.w_content, openIntent(ctx0, "device", false, id, "body"))
    }

    fun wasRendered(ctx: Context, id: Int): Boolean = sp(ctx).getBoolean("r_$id", false)
}

// ====================================================================== providers + receiver

abstract class BaseWidgetProvider(private val kind: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            // interval "off": keep what is shown, only the first draw and explicit user actions refresh it
            val c = HomeWidgets.cfg(context, id, kind)
            if (HomeWidgets.isGraphic(kind)) {
                // only the system (first add / restore after a reboot) lands here: draw once, never on a timer
                HomeWidgets.update(context, mgr, id, kind, true)
                continue
            }
            if (c.interval == 0 && HomeWidgets.wasRendered(context, id)) continue
            HomeWidgets.update(context, mgr, id, kind)
        }
        HomeWidgets.scheduleNext(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        HomeWidgets.update(context, mgr, id, kind)
    }

    override fun onEnabled(context: Context) {
        HomeWidgets.scheduleNext(context)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        HomeWidgets.forget(context, ids)
        HomeWidgets.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        HomeWidgets.scheduleNext(context)
    }
}

class SpeedWidgetProvider : BaseWidgetProvider(HomeWidgets.KIND_SPEED)

class TestWidgetProvider : BaseWidgetProvider(HomeWidgets.KIND_TEST)

class HealthWidgetProvider : BaseWidgetProvider(HomeWidgets.KIND_HEALTH)

class OrbWidgetProvider : BaseWidgetProvider(HomeWidgets.KIND_ORB)

class RingsWidgetProvider : BaseWidgetProvider(HomeWidgets.KIND_RINGS)

class IdentityWidgetProvider : BaseWidgetProvider(HomeWidgets.KIND_ID)

/** widget button "Measure now" / "Start test" and the auto-update alarm */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                HomeWidgets.ACTION_TICK -> HomeWidgets.tick(context)
                HomeWidgets.ACTION_START_TEST -> {
                    if (!SpeedTestService.start(context)) {
                        // the system refused to start the service: tell the user right in the widget
                        HomeWidgets.testEnd(context, HomeWidgets.ERR_START)
                        HomeWidgets.updateAll(context)
                    }
                }
            }
        } catch (e: Throwable) {
            // a widget must never crash the app
        }
    }
}
