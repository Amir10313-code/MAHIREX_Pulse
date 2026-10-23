package com.mahirex.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
 *  - Every widget is drawn by WidgetBuilder / WidgetArt (Canvas, size matched to the widget), reads the battery only from the
 *    sticky broadcast and redraws only when a visible value changed. The Orb / Storage & RAM / Identity widgets update at most
 *    every 15 minutes (or never). No service, no WorkManager, no network, no continuous animation.
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
    const val F4 = 8
    const val F5 = 16

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

    val ACCENT_LIGHT = intArrayOf(0xFF2563EB.toInt(), 0xFF0EA5E9.toInt(), 0xFF9333EA.toInt(), 0xFF0E9F6E.toInt(), 0xFFF59E0B.toInt(), 0xFFEC4899.toInt())
    val ACCENT_DARK = intArrayOf(0xFF60A5FA.toInt(), 0xFF38BDF8.toInt(), 0xFFC084FC.toInt(), 0xFF34D399.toInt(), 0xFFFBBF24.toInt(), 0xFFF472B6.toInt())
    internal val PILLS = intArrayOf(
        R.drawable.widget_pill_0, R.drawable.widget_pill_1, R.drawable.widget_pill_2,
        R.drawable.widget_pill_3, R.drawable.widget_pill_4, R.drawable.widget_pill_5
    )

    /**
     * [opacity] 20..100 % of the card; [corner] 0 sharp / 1 standard / 2 round; [style] 0 thin / 1 thick rings and gauges.
     * The defaults are the look of older installs.
     */
    class Cfg(val theme: Int, val accent: Int, val fields: Int, val interval: Int, val opacity: Int = 100, val corner: Int = 1, val style: Int = 0)

    /** colours of a widget: card, text, accent and the status colours used by rings and gauges */
    class Pal(
        val dark: Boolean, val bg: Int, val tx: Int, val mu: Int, val accent: Int,
        val tint: Int, val good: Int, val warnA: Int, val warnB: Int, val badA: Int, val badB: Int, val track: Int, val guide: Int
    )

    private fun sp(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("mahirex_widgets", Context.MODE_PRIVATE)

    /** every toggle on for a new widget; settings saved by older versions keep their own bits (the newer ones stay off) */
    fun defaultFields(kind: Int): Int = F1 or F2 or F3 or F4 or F5

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
            p.getInt("o_$id", 100).coerceIn(20, 100),
            p.getInt("cr_$id", 1).coerceIn(0, 2),
            p.getInt("gs_$id", 0).coerceIn(0, 1)
        )
    }

    fun saveCfg(ctx: Context, id: Int, c: Cfg) {
        sp(ctx).edit().putInt("t_$id", c.theme).putInt("c_$id", c.accent).putInt("f_$id", c.fields).putInt("i_$id", c.interval).putInt("o_$id", c.opacity).putInt("cr_$id", c.corner).putInt("gs_$id", c.style).apply()
    }

    fun forget(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        for (id in ids) {
            e.remove("t_$id").remove("c_$id").remove("f_$id").remove("a_$id").remove("i_$id").remove("r_$id").remove("u_$id").remove("o_$id").remove("s_$id").remove("cr_$id").remove("gs_$id")
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
            // only the speed widgets show a running test; the others follow their own interval and are not touched by test pushes
            if (kind != KIND_SPEED && kind != KIND_TEST) continue
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
            .putFloat("tr_d", d.toFloat()).putFloat("tr_u", u.toFloat()).putLong("tr_ts", ts)
            .putString("tr_hd", pushSample(p.getString("tr_hd", null), d)).putString("tr_hu", pushSample(p.getString("tr_hu", null), u)).apply()
        return Rate(d, u, ts)
    }

    private const val HISTORY_MAX = 20

    private fun pushSample(csv: String?, v: Double): String {
        val l = ArrayList<String>()
        if (!csv.isNullOrEmpty()) l.addAll(csv.split(','))
        l.add(String.format(Locale.US, "%.2f", v))
        return l.takeLast(HISTORY_MAX).joinToString(",")
    }

    private fun parseSamples(csv: String?): List<Float> =
        if (csv.isNullOrEmpty()) emptyList() else csv.split(',').mapNotNull { it.toFloatOrNull() }

    /** the last measured traffic samples (download, upload) in Mbps, oldest first; they exist only while the update interval is on */
    fun trafficHistory(ctx: Context): Pair<List<Float>, List<Float>> {
        val p = sp(ctx)
        return Pair(parseSamples(p.getString("tr_hd", null)), parseSamples(p.getString("tr_hu", null)))
    }

    // ------------------------------------------------------------ helpers
    internal fun localized(ctx: Context): Context {
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
        return Pal(
            dark, bg, tx, mu, accent,
            ctx.getColor(if (dark) R.color.wd_tint_d else R.color.wd_tint_l),
            ctx.getColor(if (dark) R.color.wd_good_d else R.color.wd_good_l),
            ctx.getColor(R.color.wd_warn_a), ctx.getColor(R.color.wd_warn_b), ctx.getColor(R.color.wd_bad_a), ctx.getColor(R.color.wd_bad_b),
            ctx.getColor(if (dark) R.color.wd_track_d else R.color.wd_track_l),
            ctx.getColor(if (dark) R.color.wd_guide_d else R.color.wd_guide_l)
        )
    }

    /** opens the app directly on [dest]. The data URI makes every button a different PendingIntent. */
    internal fun openIntent(ctx: Context, dest: String, auto: Boolean, widgetId: Int, slot: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.action = ACTION_OPEN
        i.data = Uri.parse("mahirex://widget/$dest/$widgetId/$slot")
        i.putExtra(EXTRA_DEST, dest)
        i.putExtra("auto", auto)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, ("$dest/$widgetId/$slot").hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** the button starts the test through an invisible activity: the app never opens */
    internal fun startTestIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, WidgetTestActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        return PendingIntent.getActivity(ctx, 7001, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal fun num(v: Double): String = when {
        v >= 100 -> String.format(Locale.US, "%.0f", v)
        v >= 10 -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }

    internal fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

    // ------------------------------------------------------------ rendering (see WidgetBuilder)
    /** [force]: redraw even if no visible value changed (an unchanged widget keeps its picture and costs almost nothing) */
    fun update(ctx0: Context, mgr: AppWidgetManager, id: Int, kind: Int, force: Boolean = false) {
        val cfg = cfg(ctx0, id, kind)
        val d = WidgetBuilder.dimsOf(mgr, id)
        val prep = WidgetBuilder.prepare(ctx0, kind, cfg, d, false)
        val p = sp(ctx0)
        val now = System.currentTimeMillis()
        if (!force && p.getBoolean("r_$id", false) && prep.key == p.getString("s_$id", null)) {
            // nothing visible changed: keep the existing picture, only remember that this interval has been served
            p.edit().putLong("u_$id", now).apply()
            return
        }
        mgr.updateAppWidget(id, WidgetBuilder.render(ctx0, kind, cfg, d, id, prep))
        p.edit().putBoolean("r_$id", true).putLong("u_$id", now).putString("s_$id", prep.key).apply()
    }

    fun wasRendered(ctx: Context, id: Int): Boolean = sp(ctx).getBoolean("r_$id", false)
}

// ====================================================================== providers + receiver

abstract class BaseWidgetProvider(private val kind: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            // the widgets declare updatePeriodMillis = 0, so the system only lands here for the first draw, after a reboot
            // or after an app update; the real refresh schedule is the user's own interval (or never when it is off)
            HomeWidgets.update(context, mgr, id, kind, true)
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
