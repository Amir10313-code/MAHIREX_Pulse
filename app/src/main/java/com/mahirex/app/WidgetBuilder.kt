package com.mahirex.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import java.util.Locale

/**
 * Builds the RemoteViews of every home-screen widget. The same code produces the real widget and the in-app preview
 * ([preview]), so what the user sees while choosing is exactly what the home screen will show.
 * No service, no WorkManager, no network; the battery is read only from the sticky broadcast.
 */
internal object WidgetBuilder {
    /** widget size in dp and the size classes derived from it */
    class Dim(val wDp: Int, val hDp: Int) {
        val small: Boolean get() = hDp < 100
        val roomy: Boolean get() = hDp >= 130
        val wide: Boolean get() = wDp >= 200
        val large: Boolean get() = wDp >= 240
    }

    /** what a widget shows right now; [key] changes only when something visible changed */
    class Prep(val key: String, val data: Any?)

    internal class Bat(val pct: Int, val status: Int, val tempC: Float?, val mv: Int?) {
        val charging: Boolean get() = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val level: Int get() = WidgetArt.levelOf(pct, true, charging)
    }

    internal class Mem(val stoPct: Int, val stoUsed: Long, val stoTotal: Long, val ramPct: Int, val ramUsed: Long, val ramTotal: Long)

    private class Snap(val bat: Bat?, val mem: Mem?, val title: String)

    private class SpeedData(
        val down: Double?, val up: Double?, val title: String, val chip: String, val chipBad: Boolean,
        val sub: String, val subBad: Boolean, val running: Boolean, val btn: String, val spark: List<Float>, val sparkUp: List<Float>
    )

    private class TestData(
        val running: Boolean, val failed: Boolean, val done: Boolean, val phase: Int, val live: Double,
        val down: Double?, val up: Double?, val ping: Double?, val jit: Double?, val whenTxt: String, val whenKind: Int,
        val gaugeMax: Int, val progress: Int, val btn: String
    )

    fun dimsOf(mgr: AppWidgetManager, id: Int): Dim {
        val o = mgr.getAppWidgetOptions(id)
        return Dim(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110))
    }

    /** typical size of a widget of this kind on the home screen (dp), used by the previews */
    fun typicalDp(kind: Int, maxWidthDp: Int): Dim {
        val w4 = Math.min(Math.max(maxWidthDp, 250), 400)
        return when (kind) {
            HomeWidgets.KIND_TEST, HomeWidgets.KIND_ORB -> Dim(160, 160)
            else -> Dim(w4, 150)
        }
    }

    // ------------------------------------------------------------ device data
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

    /** official name (no network here); otherwise manufacturer + model */
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
        return if (u.substringAfter(' ') == t.substringAfter(' ')) u.substringBefore(' ') + " / " + t else "$u / $t"
    }

    /** "12.4" + "Mbps": the unit follows the size of the value (Kbps / Mbps / Gbps) */
    internal fun rateParts(ctx: Context, mbps: Double?): Pair<String, String> {
        if (mbps == null) return Pair("—", ctx.getString(R.string.wd_u_mbps))
        return when {
            mbps >= 1000.0 -> Pair(HomeWidgets.num(mbps / 1000.0), ctx.getString(R.string.wd_u_gbps))
            mbps >= 1.0 -> Pair(HomeWidgets.num(mbps), ctx.getString(R.string.wd_u_mbps))
            else -> Pair(HomeWidgets.num(mbps * 1000.0), ctx.getString(R.string.wd_u_kbps))
        }
    }

    private fun on(cfg: HomeWidgets.Cfg, bit: Int): Boolean = cfg.fields and bit != 0

    private fun px(ctx: Context, dp: Int, cap: Int, floor: Int): Int = (dp * ctx.resources.displayMetrics.density).toInt().coerceIn(floor, cap)

    // ------------------------------------------------------------ RemoteViews helpers
    private fun RemoteViews.txt(viewId: Int, text: String, color: Int, size: Float? = null) {
        setTextViewText(viewId, text)
        setTextColor(viewId, color)
        if (size != null) setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun RemoteViews.show(viewId: Int, on: Boolean) = setViewVisibility(viewId, if (on) View.VISIBLE else View.GONE)

    /** click targets are only wired on the real widget (id != 0); the preview has none */
    private fun RemoteViews.tap(id: Int, viewId: Int, pi: () -> PendingIntent) {
        if (id != 0) setOnClickPendingIntent(viewId, pi())
    }

    private fun RemoteViews.pill(viewId: Int, label: String, pal: HomeWidgets.Pal, cfg: HomeWidgets.Cfg) {
        setTextViewText(viewId, label)
        setTextColor(viewId, pal.accent)
        setInt(viewId, "setBackgroundResource", HomeWidgets.PILLS[cfg.accent.coerceIn(0, HomeWidgets.PILLS.size - 1)])
    }

    private fun base(ctx0: Context, layout: Int, pal: HomeWidgets.Pal, cfg: HomeWidgets.Cfg): RemoteViews {
        val v = RemoteViews(ctx0.packageName, layout)
        v.setImageViewResource(
            R.id.w_bg,
            when (cfg.corner) {
                0 -> R.drawable.widget_bg_c0
                2 -> R.drawable.widget_bg_c2
                else -> R.drawable.widget_bg
            }
        )
        v.setImageViewResource(
            R.id.w_sheen,
            when (cfg.corner) {
                0 -> R.drawable.widget_sheen_c0
                2 -> R.drawable.widget_sheen_c2
                else -> R.drawable.widget_sheen
            }
        )
        val a = cfg.opacity.coerceIn(20, 100)
        v.setInt(R.id.w_bg, "setColorFilter", pal.bg)
        v.setInt(R.id.w_bg, "setImageAlpha", a * 255 / 100)
        v.setInt(R.id.w_sheen, "setImageAlpha", a * (if (pal.dark) 150 else 110) / 100)
        return v
    }

    private fun errShort(ctx: Context, err: Int): String = when (err) {
        SpeedRunner.ERR_OFFLINE -> ctx.getString(R.string.wd_e_offline)
        SpeedRunner.ERR_SERVER -> ctx.getString(R.string.wd_e_server)
        SpeedRunner.ERR_DOWNLOAD -> ctx.getString(R.string.wd_e_down)
        SpeedRunner.ERR_CANCEL -> ctx.getString(R.string.wd_e_cancel)
        HomeWidgets.ERR_START -> ctx.getString(R.string.wd_err_start)
        else -> ""
    }

    private fun phaseLabel(ctx: Context, phase: Int): String = when (phase) {
        HomeWidgets.PH_SELECT -> ctx.getString(R.string.sp_phase_select)
        HomeWidgets.PH_PING -> ctx.getString(R.string.sp_phase_ping)
        HomeWidgets.PH_DOWN -> ctx.getString(R.string.sp_phase_down)
        HomeWidgets.PH_UP -> ctx.getString(R.string.sp_phase_up)
        else -> ""
    }

    // ------------------------------------------------------------ prepare: values + change key
    fun prepare(ctx0: Context, kind: Int, cfg: HomeWidgets.Cfg, d: Dim, demo: Boolean): Prep {
        val ctx = HomeWidgets.localized(ctx0.applicationContext)
        val pal = HomeWidgets.palette(ctx, cfg.theme, cfg.accent)
        val k = StringBuilder()
        k.append(kind).append('|').append(pal.dark).append('|').append(cfg.accent).append('|').append(cfg.fields).append('|')
            .append(cfg.opacity).append('|').append(cfg.corner).append('|').append(cfg.style).append('|')
            .append(Prefs.lang()).append('|').append(d.wDp).append('x').append(d.hDp).append('|')
        val data: Any?
        when (kind) {
            HomeWidgets.KIND_SPEED -> {
                val s = speedData(ctx0, ctx, cfg, demo)
                data = s
                k.append(s.down).append(s.up).append(s.title).append(s.chip).append(s.sub).append(s.running).append(s.btn)
                    .append(s.spark.hashCode()).append(s.sparkUp.hashCode())
            }
            HomeWidgets.KIND_TEST -> {
                val t = testData(ctx0, ctx, demo)
                data = t
                k.append(t.phase).append(t.running).append(t.failed).append(t.done).append(Math.round(t.live * 10)).append(t.down).append(t.up)
                    .append(t.ping).append(t.jit).append(t.whenTxt).append(t.progress).append(t.gaugeMax).append(t.btn)
            }
            HomeWidgets.KIND_HEALTH -> {
                val b = batSnap(ctx0)
                val m = memSnap(ctx0)
                data = Snap(b, m, "")
                k.append(b.pct).append(b.charging).append(b.level).append(m.stoPct).append(m.ramPct)
                if (on(cfg, HomeWidgets.F5)) k.append(tempText(b))
            }
            HomeWidgets.KIND_ORB -> {
                val b = batSnap(ctx0)
                data = Snap(b, null, "")
                k.append(b.pct).append(b.charging).append(b.level)
                if (on(cfg, HomeWidgets.F1)) k.append('|').append(stateText(ctx, b))
                if (on(cfg, HomeWidgets.F2)) k.append('|').append(tempText(b))
                if (on(cfg, HomeWidgets.F3)) k.append('|').append(voltText(b))
            }
            HomeWidgets.KIND_RINGS -> {
                val m = memSnap(ctx0)
                data = Snap(null, m, "")
                k.append(m.stoPct).append('|').append(m.ramPct)
                if (on(cfg, HomeWidgets.F3)) k.append('|').append(usedTotal(m.stoUsed, m.stoTotal)).append('|').append(usedTotal(m.ramUsed, m.ramTotal))
            }
            else -> {
                val b = batSnap(ctx0)
                val title = deviceTitle(ctx0)
                data = Snap(b, null, title)
                k.append(title).append('|').append(b.pct).append(b.level)
            }
        }
        return Prep(k.toString(), data)
    }

    private fun speedData(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, demo: Boolean): SpeedData {
        val now = System.currentTimeMillis()
        val ts = HomeWidgets.testState(ctx0)
        val running = !demo && ts.phase != HomeWidgets.PH_IDLE && now - ts.ts < 150000L
        val failed = !demo && !running && ts.err != 0 && now - ts.ts < 90000L
        val last = if (demo) null else SpeedStore.last(ctx0)
        val rate = if (!demo && cfg.interval > 0) HomeWidgets.trafficRate(ctx0) else null
        val rateFresh = rate != null && now - rate.at < cfg.interval * 3000L + 60000L
        var d: Double? = null
        var u: Double? = null
        var tag = ctx.getString(R.string.wd_tag_last)
        var sub = ctx.getString(R.string.wd_tap_measure)
        var subBad = false
        var spark: List<Float> = emptyList()
        var sparkUp: List<Float> = emptyList()
        if (demo) {
            d = 48.2
            u = 12.6
            tag = ctx.getString(R.string.wd_tag_live)
            sub = ctx.getString(R.string.wd_ping_jit_at, HomeWidgets.num(45.0), HomeWidgets.num(3.1), HomeWidgets.hm(now))
            spark = listOf(12f, 18f, 15f, 28f, 34f, 30f, 41f, 38f, 46f, 44f, 52f, 48f)
            sparkUp = listOf(4f, 6f, 5f, 8f, 9f, 8f, 11f, 10f, 13f, 12f, 13f, 12.6f)
        } else {
            if (running) {
                d = if (ts.phase == HomeWidgets.PH_DOWN) ts.live else if (ts.down >= 0) ts.down else null
                u = if (ts.phase == HomeWidgets.PH_UP) ts.live else null
                tag = ctx.getString(R.string.wd_tag_testing)
                sub = if (ts.ping >= 0) ctx.getString(R.string.wd_ping_jit, HomeWidgets.num(ts.ping), HomeWidgets.num(ts.jitter)) else phaseLabel(ctx, ts.phase)
            } else if (failed) {
                d = last?.down
                u = if (last != null && last.up >= 0) last.up else null
                sub = errShort(ctx, ts.err)
                subBad = true
            } else if (rateFresh && rate != null) {
                d = rate.down
                u = rate.up
                tag = ctx.getString(R.string.wd_tag_live)
                if (last != null) sub = ctx.getString(R.string.wd_ping_jit_at, HomeWidgets.num(last.ping), HomeWidgets.num(last.jitter), HomeWidgets.hm(last.time))
            } else if (last != null) {
                d = last.down
                u = if (last.up >= 0) last.up else null
                sub = ctx.getString(R.string.wd_ping_jit_at, HomeWidgets.num(last.ping), HomeWidgets.num(last.jitter), HomeWidgets.hm(last.time))
            }
            val hist = HomeWidgets.trafficHistory(ctx0)
            if (rateFresh && hist.first.size >= 2) {
                spark = hist.first
                sparkUp = hist.second
            } else {
                val h = SpeedStore.history(ctx0).take(12).reversed()
                if (h.size >= 2) {
                    spark = h.map { it.down.toFloat() }
                    sparkUp = h.map { if (it.up >= 0) it.up.toFloat() else 0f }
                }
            }
        }
        val online = demo || Net.isOnline(ctx0)
        val tr = when (Net.transport(ctx0)) {
            Net.T_WIFI -> ctx.getString(R.string.sp_net_wifi)
            Net.T_CELL -> ctx.getString(R.string.sp_net_cell)
            Net.T_ETH -> ctx.getString(R.string.sp_net_eth)
            Net.T_VPN -> ctx.getString(R.string.sp_net_vpn)
            else -> ctx.getString(R.string.sp_net_other)
        }
        val chip = if (demo) ctx.getString(R.string.sp_net_wifi) + " · " + ctx.getString(R.string.wd_online)
        else if (online) tr + " · " + ctx.getString(R.string.wd_online) else ctx.getString(R.string.sp_net_none)
        val btn = if (running) phaseLabel(ctx, ts.phase) else ctx.getString(R.string.wd_measure)
        return SpeedData(d, u, ctx.getString(R.string.wd_speed_title) + " · " + tag, chip, !online, sub, subBad, running, btn, spark, sparkUp)
    }

    private val GAUGE_STEPS = intArrayOf(10, 25, 50, 100, 250, 500, 1000)

    private fun testData(ctx0: Context, ctx: Context, demo: Boolean): TestData {
        val now = System.currentTimeMillis()
        val ts = HomeWidgets.testState(ctx0)
        val running = !demo && ts.phase != HomeWidgets.PH_IDLE && now - ts.ts < 150000L
        val failed = !demo && !running && ts.err != 0 && now - ts.ts < 90000L
        val last = if (demo) null else SpeedStore.last(ctx0)
        var d: Double? = last?.down
        var u: Double? = if (last != null && last.up >= 0) last.up else null
        var p: Double? = last?.ping
        var j: Double? = last?.jitter
        var whenTxt = if (last != null) HomeWidgets.hm(last.time) else ""
        var kind = 0
        val done = !demo && !running && !failed && last != null && ts.ts > 0 && ts.err == 0 && now - ts.ts < 60000L
        if (demo) {
            d = 48.2
            u = 12.6
            p = 45.0
            j = 3.1
            whenTxt = HomeWidgets.hm(now)
        }
        var progress = 0
        if (running) {
            d = if (ts.phase == HomeWidgets.PH_DOWN) ts.live else if (ts.down >= 0) ts.down else null
            u = if (ts.phase == HomeWidgets.PH_UP) ts.live else null
            p = if (ts.ping >= 0) ts.ping else null
            j = if (ts.jitter >= 0) ts.jitter else null
            whenTxt = phaseLabel(ctx, ts.phase)
            kind = 1
            progress = when (ts.phase) {
                HomeWidgets.PH_SELECT -> 10
                HomeWidgets.PH_PING -> 30
                HomeWidgets.PH_DOWN -> 60
                else -> 90
            }
        } else if (failed) {
            whenTxt = errShort(ctx, ts.err)
            kind = 2
        } else if (done) {
            whenTxt = ctx.getString(R.string.sp_phase_done) + " · " + HomeWidgets.hm(last!!.time)
            kind = 1
            progress = 100
        }
        var max = GAUGE_STEPS[0]
        for (s in GAUGE_STEPS) {
            max = s
            if (ts.live * 1.1 <= s) break
        }
        val btn = if (running) phaseLabel(ctx, ts.phase) else ctx.getString(R.string.wd_start_test)
        return TestData(running, failed, done, if (running) ts.phase else HomeWidgets.PH_IDLE, if (running) ts.live else 0.0, d, u, p, j, whenTxt, kind, max, progress, btn)
    }

    // ------------------------------------------------------------ render
    fun render(ctx0: Context, kind: Int, cfg: HomeWidgets.Cfg, d: Dim, id: Int, prep: Prep): RemoteViews {
        val ctx = HomeWidgets.localized(ctx0.applicationContext)
        val pal = HomeWidgets.palette(ctx, cfg.theme, cfg.accent)
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        return when (kind) {
            HomeWidgets.KIND_SPEED -> drawSpeed(ctx0, ctx, cfg, pal, d, id, prep.data as SpeedData)
            HomeWidgets.KIND_TEST -> drawTest(ctx0, ctx, cfg, pal, d, id, prep.data as TestData, rtl)
            HomeWidgets.KIND_HEALTH -> drawHealth(ctx0, ctx, cfg, pal, d, id, prep.data as Snap)
            HomeWidgets.KIND_ORB -> drawOrb(ctx0, ctx, cfg, pal, d, id, (prep.data as Snap).bat!!)
            HomeWidgets.KIND_RINGS -> drawRings(ctx0, ctx, cfg, pal, d, id, (prep.data as Snap).mem!!)
            else -> {
                val s = prep.data as Snap
                drawIdentity(ctx0, ctx, cfg, pal, d, id, s.bat!!, s.title, rtl)
            }
        }
    }

    /** exactly what the home screen will show, with demo numbers for the speed widgets and live device values for the rest */
    fun preview(ctx0: Context, kind: Int, cfg: HomeWidgets.Cfg, d: Dim): RemoteViews =
        render(ctx0, kind, cfg, d, 0, prepare(ctx0, kind, cfg, d, true))

    /** touches on a preview do nothing (the widget buttons must not fire from inside the app) */
    private class PreviewFrame(ctx: Context) : FrameLayout(ctx) {
        override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true
    }

    /**
     * A centered, real-size copy of the widget for the app screens: the widget's own RemoteViews inflated in the app, so layout,
     * texts, colours, rings and the chosen options are exactly those of the home-screen widget. [maxWidthDp] is the room available.
     */
    fun previewView(ctx: Context, kind: Int, cfg: HomeWidgets.Cfg, maxWidthDp: Int): View {
        val d = typicalDp(kind, maxWidthDp)
        val dens = ctx.resources.displayMetrics.density
        val frame = PreviewFrame(ctx)
        frame.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (d.hDp * dens).toInt())
        try {
            val inner = preview(ctx, kind, cfg, d).apply(ctx, frame)
            frame.addView(inner, FrameLayout.LayoutParams((d.wDp * dens).toInt(), (d.hDp * dens).toInt(), Gravity.CENTER))
        } catch (e: Throwable) {
            // a preview must never crash the screen
        }
        return frame
    }

    private fun drawSpeed(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, pal: HomeWidgets.Pal, d: Dim, id: Int, s: SpeedData): RemoteViews {
        val v = base(ctx0, R.layout.widget_speed, pal, cfg)
        val headOn = !d.small && (d.large || on(cfg, HomeWidgets.F3))
        v.show(R.id.w_head, headOn)
        v.txt(R.id.w_title, s.title, pal.accent)
        v.show(R.id.w_title, d.large)
        v.txt(R.id.w_chip, s.chip, if (s.chipBad) pal.badB else pal.accent)
        v.setInt(R.id.w_chip, "setBackgroundResource", HomeWidgets.PILLS[cfg.accent.coerceIn(0, HomeWidgets.PILLS.size - 1)])
        v.show(R.id.w_chip, on(cfg, HomeWidgets.F3))
        val size = if (d.small) 20f else 26f
        val dn = rateParts(ctx, s.down)
        val up = rateParts(ctx, s.up)
        v.txt(R.id.w_down_arrow, "↓", pal.accent)
        v.txt(R.id.w_up_arrow, "↑", pal.tint)
        // narrow widgets (about 2 cells) have no room for number + unit side by side: the unit becomes the label
        val narrow = !d.wide
        v.txt(R.id.w_down_lbl, if (d.small) "" else if (narrow) dn.second else ctx.getString(R.string.sp_download), pal.mu)
        v.txt(R.id.w_up_lbl, if (d.small) "" else if (narrow) up.second else ctx.getString(R.string.sp_upload), pal.mu)
        v.txt(R.id.w_down, dn.first, pal.accent, size)
        v.txt(R.id.w_up, up.first, pal.accent, size)
        v.txt(R.id.w_down_unit, dn.second, pal.mu)
        v.txt(R.id.w_up_unit, up.second, pal.mu)
        v.show(R.id.w_down_unit, !narrow)
        v.show(R.id.w_up_unit, !narrow)
        v.show(R.id.w_down_box, on(cfg, HomeWidgets.F1))
        v.show(R.id.w_up_box, on(cfg, HomeWidgets.F2))
        val sparkOn = on(cfg, HomeWidgets.F4) && s.spark.size >= 2 && d.roomy
        v.show(R.id.w_spark, sparkOn)
        if (sparkOn) {
            v.setImageViewBitmap(R.id.w_spark, WidgetArt.sparkBitmap(px(ctx0, d.wDp - 24, 520, 200), px(ctx0, 30, 110, 40), s.spark, s.sparkUp, pal, cfg.style == 1))
        }
        v.show(R.id.w_foot, !d.small)
        v.txt(R.id.w_sub, s.sub, if (s.subBad) pal.badB else pal.mu)
        v.show(R.id.w_sub, d.large && on(cfg, HomeWidgets.F5))
        v.pill(R.id.w_b1, s.btn, pal, cfg)
        v.tap(id, R.id.w_b1) { HomeWidgets.startTestIntent(ctx0) }
        v.tap(id, R.id.w_content) { HomeWidgets.openIntent(ctx0, "speed", false, id, "body") }
        return v
    }

    private fun drawTest(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, pal: HomeWidgets.Pal, d: Dim, id: Int, t: TestData, rtl: Boolean): RemoteViews {
        val v = base(ctx0, R.layout.widget_test, pal, cfg)
        val active = t.running || t.failed || t.done
        v.show(R.id.w_head, active || d.hDp >= 170)
        v.txt(R.id.w_title, ctx.getString(R.string.wd_test_title), pal.accent)
        v.txt(R.id.w_when, if (active || on(cfg, HomeWidgets.F4)) t.whenTxt else "", if (t.whenKind == 2) pal.badB else if (t.whenKind == 1) pal.accent else pal.mu)
        val gauge = t.running && d.roomy
        v.show(R.id.w_run, gauge)
        v.show(R.id.w_idle, !gauge)
        v.show(R.id.w_prog, t.running && d.roomy)
        val numSize = if (d.small) 18f else if (d.large) 26f else 22f
        val dn = rateParts(ctx, t.down)
        val up = rateParts(ctx, t.up)
        val narrow = !d.wide
        v.txt(R.id.w_down_lbl, if (narrow) "↓ " + dn.second else ctx.getString(R.string.sp_download), pal.mu)
        v.txt(R.id.w_up_lbl, if (narrow) "↑ " + up.second else ctx.getString(R.string.sp_upload), pal.mu)
        v.show(R.id.w_down_unit, !narrow)
        v.show(R.id.w_up_unit, !narrow)
        v.txt(R.id.w_down, dn.first, pal.accent, numSize)
        v.txt(R.id.w_up, up.first, pal.accent, numSize)
        v.txt(R.id.w_down_unit, dn.second, pal.mu)
        v.txt(R.id.w_up_unit, up.second, pal.mu)
        v.txt(R.id.w_ping_lbl, ctx.getString(R.string.sp_ping), pal.mu)
        v.txt(R.id.w_jit_lbl, ctx.getString(R.string.sp_jitter), pal.mu)
        v.txt(R.id.w_ping, if (t.ping == null) "—" else String.format(Locale.US, "%.0f", t.ping), pal.tx, numSize)
        v.txt(R.id.w_jit, if (t.jit == null) "—" else String.format(Locale.US, "%.1f", t.jit), pal.tx, numSize)
        v.txt(R.id.w_ping_unit, ctx.getString(R.string.wd_u_ms), pal.mu)
        v.txt(R.id.w_jit_unit, ctx.getString(R.string.wd_u_ms), pal.mu)
        val showDown = on(cfg, HomeWidgets.F1)
        val showUp = on(cfg, HomeWidgets.F2)
        v.show(R.id.w_down_box, showDown)
        v.show(R.id.w_up_box, showUp)
        v.show(R.id.w_vals, showDown || showUp)
        v.show(R.id.w_vals2, on(cfg, HomeWidgets.F3) && (d.hDp >= 130 || (!showDown && !showUp)))
        if (gauge) {
            val wpx = px(ctx0, d.wDp - 24, 440, 200)
            val frac = if (t.phase == HomeWidgets.PH_DOWN || t.phase == HomeWidgets.PH_UP) (t.live / t.gaugeMax).toFloat() else 0f
            v.setImageViewBitmap(R.id.w_gauge, WidgetArt.gaugeBitmap(wpx, (wpx * 0.62f).toInt(), frac, pal, cfg.style == 1))
            val live = when (t.phase) {
                HomeWidgets.PH_DOWN, HomeWidgets.PH_UP -> rateParts(ctx, t.live)
                HomeWidgets.PH_PING -> Pair(if (t.ping == null) "—" else String.format(Locale.US, "%.0f", t.ping), ctx.getString(R.string.wd_u_ms))
                else -> Pair("—", "")
            }
            v.txt(R.id.w_live, live.first, pal.tx)
            v.txt(R.id.w_live_unit, live.second, pal.mu)
            v.setImageViewBitmap(R.id.w_prog, WidgetArt.barBitmap(px(ctx0, d.wDp - 24, 480, 200), px(ctx0, 6, 16, 8), t.progress, pal, WidgetArt.LVL_OK, rtl))
        }
        v.show(R.id.w_btn_box, !d.small)
        v.pill(R.id.w_b1, t.btn, pal, cfg)
        v.setInt(R.id.w_b1, "setBackgroundResource", HomeWidgets.PILLS[cfg.accent.coerceIn(0, HomeWidgets.PILLS.size - 1)])
        v.setImageViewBitmap(R.id.w_glow, WidgetArt.glowBitmap(px(ctx0, d.wDp - 24, 520, 200), px(ctx0, 46, 140, 60), pal.accent, pal))
        v.tap(id, R.id.w_b1) { HomeWidgets.startTestIntent(ctx0) }
        v.tap(id, R.id.w_content) { HomeWidgets.openIntent(ctx0, "speed", false, id, "body") }
        return v
    }

    private fun drawHealth(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, pal: HomeWidgets.Pal, d: Dim, id: Int, s: Snap): RemoteViews {
        val b = s.bat!!
        val m = s.mem!!
        val v = base(ctx0, R.layout.widget_health, pal, cfg)
        var showBat = on(cfg, HomeWidgets.F1)
        val showSto = on(cfg, HomeWidgets.F2)
        val showRam = on(cfg, HomeWidgets.F4)
        if (!showBat && !showSto && !showRam) showBat = true
        val count = (if (showBat) 1 else 0) + (if (showSto) 1 else 0) + (if (showRam) 1 else 0)
        val head = d.large && !d.small
        val temp = on(cfg, HomeWidgets.F5) && b.tempC != null
        val tempCol = if ((b.tempC ?: 0f) >= 45f) pal.badB else if ((b.tempC ?: 0f) >= 40f) pal.warnB else pal.mu
        v.show(R.id.w_head, head)
        v.txt(R.id.w_title, ctx.getString(R.string.wd_health_title), pal.accent)
        v.txt(R.id.w_when, if (temp) tempText(b) else "", tempCol)
        val btns = on(cfg, HomeWidgets.F3) && d.large && d.roomy
        v.show(R.id.w_btns, btns)
        val labels = !d.small
        val ringDp = Math.max(
            40,
            Math.min(
                (d.wDp - 24) / Math.max(1, count) - 8,
                d.hDp - 24 - (if (head) 22 else 0) - (if (labels) 18 else 0) - (if (btns) 42 else 0) - (if (temp && !head) 16 else 0)
            )
        )
        val ringPx = px(ctx0, ringDp, 300, 96)
        val pctSize = if (ringDp >= 64) 16f else 12f
        val thick = cfg.style == 1
        fun ring(n: Int, boxOn: Boolean, pct: Int, lvl: Int, charging: Boolean, lbl: String) {
            v.show(if (n == 1) R.id.w_r1_box else if (n == 2) R.id.w_r2_box else R.id.w_r3_box, boxOn)
            if (!boxOn) return
            val ringId = if (n == 1) R.id.w_ring1 else if (n == 2) R.id.w_ring2 else R.id.w_ring3
            val pctId = if (n == 1) R.id.w_r1_pct else if (n == 2) R.id.w_r2_pct else R.id.w_r3_pct
            val lblId = if (n == 1) R.id.w_ring1_lbl else if (n == 2) R.id.w_ring2_lbl else R.id.w_ring3_lbl
            v.setImageViewBitmap(ringId, WidgetArt.healthRing(ringPx, pct.coerceAtLeast(0), lvl, charging, pal, thick, charging))
            v.txt(pctId, if (pct >= 0) pct.toString() + "%" else "—", pal.tx, pctSize)
            v.txt(lblId, lbl, pal.mu)
            v.show(lblId, labels)
        }
        ring(1, showBat, b.pct, b.level, b.charging, ctx.getString(R.string.sec_battery))
        ring(2, showSto, m.stoPct, WidgetArt.levelOf(m.stoPct, false, false), false, ctx.getString(R.string.sec_storage))
        ring(3, showRam, m.ramPct, WidgetArt.levelOf(m.ramPct, false, false), false, ctx.getString(R.string.wd_ram_short))
        // temperature: in the header on wide widgets, otherwise under the battery label
        v.txt(R.id.w_r1_sub, if (temp) tempText(b) else "", tempCol)
        v.show(R.id.w_r1_sub, temp && !head && showBat && labels)
        v.pill(R.id.w_b1, ctx.getString(R.string.tool_cleaner), pal, cfg)
        v.pill(R.id.w_b2, ctx.getString(R.string.tool_storage), pal, cfg)
        v.pill(R.id.w_b3, ctx.getString(R.string.tool_battery), pal, cfg)
        v.tap(id, R.id.w_b1) { HomeWidgets.openIntent(ctx0, "cleaner", false, id, "b1") }
        v.tap(id, R.id.w_b2) { HomeWidgets.openIntent(ctx0, "storage", false, id, "b2") }
        v.tap(id, R.id.w_b3) { HomeWidgets.openIntent(ctx0, "battery", false, id, "b3") }
        v.tap(id, R.id.w_content) { HomeWidgets.openIntent(ctx0, "health", false, id, "body") }
        return v
    }

    private fun drawOrb(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, pal: HomeWidgets.Pal, d: Dim, id: Int, b: Bat): RemoteViews {
        val v = base(ctx0, R.layout.widget_orb, pal, cfg)
        val size = px(ctx0, Math.min(d.wDp, d.hDp), 360, 160)
        v.setImageViewBitmap(R.id.w_orb, WidgetArt.orbBitmap(size, b.pct, b.charging, pal, cfg.style == 1))
        val showState = on(cfg, HomeWidgets.F1)
        val showTemp = on(cfg, HomeWidgets.F2)
        val showVolt = on(cfg, HomeWidgets.F3)
        val tall = !d.small
        val badC = pal.badB
        v.txt(R.id.w_pct, if (b.pct >= 0) b.pct.toString() + "%" else "—", pal.tx, if (!tall) 16f else if (d.wide) 24f else 22f)
        // 2x2: state inside the ring, temperature / voltage under it. 4x2: three rows beside the ring.
        v.txt(R.id.w_state, stateText(ctx, b), if (b.level == WidgetArt.LVL_BAD) badC else pal.accent)
        v.show(R.id.w_state, showState && tall && !d.wide)
        val extra = ArrayList<String>()
        if (showTemp) extra.add(tempText(b))
        if (showVolt) extra.add(voltText(b))
        v.txt(R.id.w_extra, extra.joinToString("  ·  "), pal.mu)
        v.show(R.id.w_extra, extra.isNotEmpty() && tall && !d.wide)
        v.show(R.id.w_info, d.wide)
        v.txt(R.id.w_title, ctx.getString(R.string.wd_orb_title), pal.accent)
        v.show(R.id.w_title, tall)
        v.txt(R.id.w_r1_lbl, ctx.getString(R.string.bat_status), pal.mu)
        v.txt(R.id.w_r1_val, stateText(ctx, b), if (b.level == WidgetArt.LVL_BAD) badC else pal.tx)
        v.txt(R.id.w_r2_lbl, ctx.getString(R.string.bat_temp), pal.mu)
        v.txt(R.id.w_r2_val, tempText(b), pal.tx)
        v.txt(R.id.w_r3_lbl, ctx.getString(R.string.bat_voltage), pal.mu)
        v.txt(R.id.w_r3_val, voltText(b), pal.tx)
        v.show(R.id.w_r1, showState)
        v.show(R.id.w_r2, showTemp)
        v.show(R.id.w_r3, showVolt)
        v.tap(id, R.id.w_content) { HomeWidgets.openIntent(ctx0, "battery", false, id, "body") }
        return v
    }

    private fun drawRings(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, pal: HomeWidgets.Pal, d: Dim, id: Int, m: Mem): RemoteViews {
        var showSto = on(cfg, HomeWidgets.F1)
        var showRam = on(cfg, HomeWidgets.F2)
        if (!showSto && !showRam) {
            showSto = true
            showRam = true
        }
        val showLbl = on(cfg, HomeWidgets.F3)
        val v = base(ctx0, if (d.wide) R.layout.widget_rings_wide else R.layout.widget_rings, pal, cfg)
        val tall = !d.small
        val size = px(ctx0, Math.min(d.wDp, d.hDp), 360, 160)
        v.setImageViewBitmap(R.id.w_dual, WidgetArt.dualBitmap(size, if (showSto) m.stoPct.coerceAtLeast(0) else null, if (showRam) m.ramPct.coerceAtLeast(0) else null, pal, cfg.accent, cfg.style == 1))
        val stoCol = WidgetArt.dualDot(pal, m.stoPct, false, cfg.accent)
        val ramCol = WidgetArt.dualDot(pal, m.ramPct, true, cfg.accent)
        // a lone ring is drawn in the outer position, so its legend dot uses the outer colour too
        val ramDot = if (showSto) ramCol else WidgetArt.dualDot(pal, m.ramPct, false, cfg.accent)
        val sPct = if (m.stoPct >= 0) m.stoPct.toString() + "%" else "—"
        val rPct = if (m.ramPct >= 0) m.ramPct.toString() + "%" else "—"
        val sSub = if (showLbl && m.stoTotal > 0) usedTotal(m.stoUsed, m.stoTotal) else sPct
        val rSub = if (showLbl && m.ramTotal > 0) usedTotal(m.ramUsed, m.ramTotal) else rPct
        v.txt(R.id.w_title, ctx.getString(R.string.wd_rings_title), pal.accent)
        v.show(R.id.w_title, tall && d.wide)
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
        v.show(R.id.w_legend, tall)
        v.tap(id, R.id.w_content) { HomeWidgets.openIntent(ctx0, "storage", false, id, "body") }
        v.tap(id, R.id.w_l1_box) { HomeWidgets.openIntent(ctx0, "storage", false, id, "l1") }
        v.tap(id, R.id.w_l2_box) { HomeWidgets.openIntent(ctx0, "ram", false, id, "l2") }
        return v
    }

    private fun drawIdentity(ctx0: Context, ctx: Context, cfg: HomeWidgets.Cfg, pal: HomeWidgets.Pal, d: Dim, id: Int, b: Bat, title: String, rtl: Boolean): RemoteViews {
        val v = base(ctx0, R.layout.widget_identity, pal, cfg)
        val tall = !d.small
        v.txt(R.id.w_title, ctx.getString(R.string.wd_id_title), pal.accent)
        v.show(R.id.w_title, tall)
        v.txt(R.id.w_name, title, pal.tx, if (d.wide) 20f else 16f)
        v.txt(R.id.w_code, Build.MODEL ?: "", pal.mu)
        v.show(R.id.w_code, on(cfg, HomeWidgets.F3) && tall)
        v.txt(R.id.w_android, ctx.getString(R.string.wd_android_line, Build.VERSION.RELEASE, Build.VERSION.SDK_INT.toString()), pal.tx)
        v.show(R.id.w_android, on(cfg, HomeWidgets.F1))
        val showBar = on(cfg, HomeWidgets.F2) && b.pct >= 0
        v.show(R.id.w_bat_row, showBar)
        if (showBar) {
            v.txt(R.id.w_bat_lbl, ctx.getString(R.string.sec_battery) + "  " + b.pct + "%" + if (b.charging) "  ⚡" else "", pal.mu)
            val bw = px(ctx0, Math.max(60, d.wDp - 24), 480, 200)
            val bh = px(ctx0, 10, 28, 14)
            v.setImageViewBitmap(R.id.w_bat_bar, WidgetArt.barBitmap(bw, bh, b.pct, pal, b.level, rtl))
        }
        v.tap(id, R.id.w_content) { HomeWidgets.openIntent(ctx0, "device", false, id, "body") }
        return v
    }
}
