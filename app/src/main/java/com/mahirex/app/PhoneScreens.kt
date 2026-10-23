package com.mahirex.app

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.database.Cursor
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.util.Locale

// ====================================================================== shared building blocks

private fun launch(act: MainActivity, i: Intent) {
    try {
        act.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(act, R.string.t_unavailable, Toast.LENGTH_SHORT).show()
    }
}

private fun kv(act: MainActivity, key: String, value: String, tone: Int = Tone.NONE): View {
    val ui = act.ui
    val row = LinearLayout(act)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(ui.dp(4), ui.dp(7), ui.dp(4), ui.dp(7))
    row.addView(ui.tv(key, 13.5f, ui.col(R.color.text_muted)), ui.lp(0, ui.WRAP, 0, 0, 0, 10).also { it.weight = 1f })
    val v = ui.tv(value, 14f, ui.toneColor(tone) ?: ui.col(R.color.text_primary), true)
    v.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
    row.addView(v, LinearLayout.LayoutParams(ui.WRAP, ui.WRAP))
    return row
}

private fun cardWithTitle(act: MainActivity, icon: Int, color: Int, title: String): LinearLayout {
    val ui = act.ui
    val c = ui.card(14, 24)
    val h = LinearLayout(act)
    h.orientation = LinearLayout.HORIZONTAL
    h.gravity = Gravity.CENTER_VERTICAL
    h.addView(ui.iconTile(icon, color, 36, 20))
    h.addView(ui.tv(title, 15.5f, ui.col(R.color.text_primary), true), ui.lp(ui.WRAP, ui.WRAP, 0, 0, 10, 0))
    c.addView(h, ui.lp(ui.MATCH, ui.WRAP, 0, 6))
    return c
}

private fun note(act: MainActivity, text: String, tone: Int = Tone.NONE): TextView {
    val ui = act.ui
    return ui.tv(text, 12.5f, ui.toneColor(tone) ?: ui.col(R.color.text_muted))
}

private fun actionRow(act: MainActivity, icon: Int, color: Int, title: String, sub: String, onClick: () -> Unit): View {
    val ui = act.ui
    val row = LinearLayout(act)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10))
    ui.feel(row, null, 14)
    row.addView(ui.iconTile(icon, color, 40, 22))
    val tb = LinearLayout(act)
    tb.orientation = LinearLayout.VERTICAL
    tb.addView(ui.tv(title, 15f, ui.col(R.color.text_primary), true))
    if (sub.isNotEmpty()) tb.addView(ui.tv(sub, 12f, ui.col(R.color.text_muted)))
    row.addView(tb, ui.lp(0, ui.WRAP, 0, 0, 12, 8).also { it.weight = 1f })
    val m = ui.col(R.color.text_muted)
    val ch = ui.icon(if (ui.rtl) Ic.CHEV_L else Ic.CHEV_R, 18, m, m)
    ch.glow = false
    row.addView(ch, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
    ui.onTap(row) { onClick() }
    return row
}

private fun bar(act: MainActivity, pct: Float, color: Int): View {
    val ui = act.ui
    val p = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
    p.max = 100
    p.progress = pct.toInt().coerceIn(0, 100)
    p.progressTintList = ColorStateList.valueOf(color)
    p.progressBackgroundTintList = ColorStateList.valueOf(ui.alpha(ui.col(R.color.text_muted), 70))
    p.layoutParams = ui.lp(ui.MATCH, ui.dp(8), 4, 4)
    return p
}

private fun levelColor(ui: Ui, score: Int): Int = when {
    score >= 75 -> ui.col(R.color.ok)
    score >= 50 -> ui.col(R.color.warn)
    else -> ui.col(R.color.bad)
}

private fun toneOfPct(usedPct: Float): Int = when {
    usedPct >= 95f -> Tone.BAD
    usedPct >= 88f -> Tone.WARN
    else -> Tone.OK
}

private fun gaugeBox(act: MainActivity, g: GaugeView): View {
    val ui = act.ui
    val box = LinearLayout(act)
    box.gravity = Gravity.CENTER_HORIZONTAL
    box.addView(g, LinearLayout.LayoutParams(ui.dp(150), ui.WRAP))
    return box
}

// ====================================================================== TOOLS hub
class ToolsScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui

    private class Tool(val icon: Int, val color: Int, val title: Int, val sub: Int, val go: () -> Unit)

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        val tools = listOf(
            Tool(Ic.HEART, 0xFFFF6B8A.toInt(), R.string.tool_health, R.string.tool_health_d) { act.go(Dest.HEALTH, 0, false) },
            Tool(Ic.STORAGE, 0xFFFFB547.toInt(), R.string.tool_storage, R.string.tool_storage_d) { act.go(Dest.STORAGE, 0, false) },
            Tool(Ic.TRASH, 0xFF2EE6A0.toInt(), R.string.tool_cleaner, R.string.tool_cleaner_d) { act.go(Dest.CLEANER, 0, false) },
            Tool(Ic.MEMORY, 0xFF2EE6E0.toInt(), R.string.tool_ram, R.string.tool_ram_d) { act.go(Dest.RAM, 0, false) },
            Tool(Ic.BATTERY, 0xFF27D98B.toInt(), R.string.tool_battery, R.string.tool_battery_d) { act.go(Dest.BATTERY, 0, false) },
            Tool(Ic.FOLDER, 0xFF4DA3FF.toInt(), R.string.tool_files, R.string.tool_files_d) { act.go(Dest.FILES, 0, false) },
            Tool(Ic.PHONE, 0xFFA56BFF.toInt(), R.string.tool_device, R.string.tool_device_d) { act.go(Dest.DEVICE, 0, false) },
            Tool(Ic.GEAR, 0xFF9B7BFF.toInt(), R.string.tool_settings, R.string.tool_settings_d) { launch(act, Intent(Settings.ACTION_SETTINGS)) }
        )
        var idx = 0
        var line: LinearLayout? = null
        for (t in tools) {
            if (idx % 2 == 0) {
                line = LinearLayout(act)
                line.orientation = LinearLayout.HORIZONTAL
                col.addView(line, ui.lp(ui.MATCH, ui.WRAP, if (idx == 0) 8 else 10))
            }
            val card = toolCard(t)
            line!!.addView(card, ui.lp(0, ui.MATCH, 0, 0, if (idx % 2 == 0) 0 else 10, 0).also { it.weight = 1f })
            // one short fade/slide-in when the page opens (no running animation afterwards)
            card.alpha = 0f
            card.translationY = ui.dpf(14f)
            card.animate().alpha(1f).translationY(0f).setStartDelay(idx * 35L).setDuration(220).start()
            idx++
        }

        // home-screen widgets
        val wc = cardWithTitle(act, Ic.LAYERS, 0xFF4DA3FF.toInt(), act.getString(R.string.wd_section))
        wc.addView(note(act, act.getString(R.string.wd_section_d)), ui.lp(ui.MATCH, ui.WRAP, 0, 6))
        wc.addView(widgetPreview(HomeWidgets.KIND_SPEED))
        wc.addView(actionRow(act, Ic.GAUGE, 0xFF4DA3FF.toInt(), act.getString(R.string.wd_speed_title), act.getString(R.string.wd_speed_sub), { pin(SpeedWidgetProvider::class.java) }))
        wc.addView(widgetPreview(HomeWidgets.KIND_TEST))
        wc.addView(actionRow(act, Ic.PLAY, 0xFFFF6BB5.toInt(), act.getString(R.string.wd_test_title), act.getString(R.string.wd_test_sub), { pin(TestWidgetProvider::class.java) }))
        wc.addView(widgetPreview(HomeWidgets.KIND_HEALTH))
        wc.addView(actionRow(act, Ic.HEART, 0xFFFF6B8A.toInt(), act.getString(R.string.wd_health_title), act.getString(R.string.wd_health_sub), { pin(HealthWidgetProvider::class.java) }))
        wc.addView(widgetPreview(HomeWidgets.KIND_ORB))
        wc.addView(actionRow(act, Ic.BATTERY, 0xFF27D98B.toInt(), act.getString(R.string.wd_orb_title), act.getString(R.string.wd_orb_sub), { pin(OrbWidgetProvider::class.java) }))
        wc.addView(widgetPreview(HomeWidgets.KIND_RINGS))
        wc.addView(actionRow(act, Ic.STORAGE, 0xFFA56BFF.toInt(), act.getString(R.string.wd_rings_title), act.getString(R.string.wd_rings_sub), { pin(RingsWidgetProvider::class.java) }))
        wc.addView(widgetPreview(HomeWidgets.KIND_ID))
        wc.addView(actionRow(act, Ic.PHONE, 0xFF4DA3FF.toInt(), act.getString(R.string.wd_id_title), act.getString(R.string.wd_id_sub), { pin(IdentityWidgetProvider::class.java) }))
        col.addView(wc, ui.lp(ui.MATCH, ui.WRAP, 14))
        col.addView(brandFooter(act))
        return sv
    }

    private fun toolCard(t: Tool): View {
        val c = LinearLayout(act)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
        ui.feel(c, ui.glass(22), 22)
        c.addView(ui.iconTile(t.icon, t.color, 44, 24))
        c.addView(ui.tv(act.getString(t.title), 15f, ui.col(R.color.text_primary), true), ui.lp(ui.MATCH, ui.WRAP, 10))
        val d = ui.tv(act.getString(t.sub), 12f, ui.col(R.color.text_muted))
        d.minLines = 2
        c.addView(d, ui.lp(ui.MATCH, ui.WRAP, 3))
        ui.onTap(c) { t.go() }
        return c
    }

    /** the real widget (its own RemoteViews) at its home-screen size, in the user's language and with the default look */
    private fun widgetPreview(kind: Int): View {
        val room = (act.resources.displayMetrics.widthPixels / act.resources.displayMetrics.density).toInt() - 28 - 28
        val v = WidgetBuilder.previewView(act, kind, HomeWidgets.Cfg(0, 0, HomeWidgets.defaultFields(kind), HomeWidgets.DEFAULT_INTERVAL), room)
        (v.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, ui.dp(10), 0, ui.dp(6))
        return v
    }

    private fun pin(cls: Class<*>) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val m = AppWidgetManager.getInstance(act)
                if (m.isRequestPinAppWidgetSupported) {
                    m.requestPinAppWidget(ComponentName(act, cls), null, null)
                    return
                }
            }
        } catch (e: Exception) {
            // fall through to the manual hint
        }
        Toast.makeText(act, R.string.wd_manual, Toast.LENGTH_LONG).show()
    }
}

// ====================================================================== PHONE HEALTH (overview)
class HealthScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private lateinit var body: LinearLayout

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        col.addView(body)
        col.addView(brandFooter(act))
        refresh()
        return sv
    }

    override fun onResume() {
        refresh()
    }

    private fun refresh() {
        body.removeAllViews()
        val res = HealthScore.compute(act)
        val hero = ui.card(16, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val g = GaugeView(act)
        val c = levelColor(ui, res.score)
        g.setColors(c, ui.col(R.color.accent3))
        g.setData(res.score.toFloat(), res.score.toString(), act.getString(R.string.hs_score))
        hero.addView(gaugeBox(act, g))
        val level = when {
            res.parts.isEmpty() -> R.string.hs_level_na
            res.score >= 75 -> R.string.hs_level_good
            res.score >= 50 -> R.string.hs_level_fair
            else -> R.string.hs_level_low
        }
        hero.addView(ui.centered(ui.tv(act.getString(level), 17f, c, true)), ui.lp(ui.WRAP, ui.WRAP, 6))
        hero.addView(ui.centered(note(act, act.getString(R.string.hs_disclaimer))), ui.lp(ui.MATCH, ui.WRAP, 8, 0, 8, 8))
        body.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        // summary cards (all real readings)
        val st = Dev.storage(act)
        val mi = Dev.ram(act)
        val bat = Dev.battery(act)
        val sc = ui.card(10, 24)
        if (st.total > 0) {
            sc.addView(summaryRow(Ic.STORAGE, 0xFFFFB547.toInt(), act.getString(R.string.tool_storage),
                Fmt.size(st.used) + " / " + Fmt.size(st.total), st.usedPct, toneOfPct(st.usedPct)) { act.go(Dest.STORAGE, 0, false) })
        }
        if (mi.totalMem > 0) {
            val used = (mi.totalMem - mi.availMem) * 100f / mi.totalMem
            sc.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
            sc.addView(summaryRow(Ic.MEMORY, 0xFF2EE6E0.toInt(), act.getString(R.string.tool_ram),
                Fmt.size(mi.totalMem - mi.availMem) + " / " + Fmt.size(mi.totalMem), used, if (mi.lowMemory) Tone.BAD else Tone.NONE) { act.go(Dest.RAM, 0, false) })
        }
        if (bat != null && bat.pct >= 0) {
            sc.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
            val tone = if (bat.pct <= 15 && !bat.charging) Tone.BAD else Tone.OK
            sc.addView(summaryRow(Ic.BATTERY, 0xFF27D98B.toInt(), act.getString(R.string.tool_battery),
                bat.pct.toString() + "%  ·  " + batteryStatus(act, bat), bat.pct.toFloat(), tone) { act.go(Dest.BATTERY, 0, false) })
        }
        body.addView(sc, ui.lp(ui.MATCH, ui.WRAP, 12))

        val dc = ui.card(10, 24)
        dc.addView(actionRow(act, Ic.PHONE, 0xFFA56BFF.toInt(), Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL,
            "Android " + Build.VERSION.RELEASE, { act.go(Dest.DEVICE, 0, false) }))
        dc.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
        dc.addView(actionRow(act, Ic.GEAR, 0xFF9B7BFF.toInt(), act.getString(R.string.tool_settings), act.getString(R.string.tool_settings_d),
            { launch(act, Intent(Settings.ACTION_SETTINGS)) }))
        body.addView(dc, ui.lp(ui.MATCH, ui.WRAP, 12))

        // how the score is calculated
        val hc = cardWithTitle(act, Ic.INFO, ui.col(R.color.accent), act.getString(R.string.hs_how))
        hc.addView(note(act, act.getString(R.string.hs_how_d)), ui.lp(ui.MATCH, ui.WRAP, 0, 6))
        for (p in res.parts) {
            hc.addView(kv(act, act.getString(p.nameRes) + "  (" + p.weight + ")", p.score.toString() + "  ·  " + p.detail,
                if (p.score >= 75) Tone.OK else if (p.score >= 50) Tone.WARN else Tone.BAD))
        }
        body.addView(hc, ui.lp(ui.MATCH, ui.WRAP, 12))
    }

    private fun summaryRow(icon: Int, color: Int, title: String, sub: String, pct: Float, tone: Int, go: () -> Unit): View {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(6), ui.dp(8), ui.dp(6), ui.dp(8))
        ui.feel(box, null, 14)
        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(ui.iconTile(icon, color, 36, 20))
        top.addView(ui.tv(title, 15f, ui.col(R.color.text_primary), true), ui.lp(0, ui.WRAP, 0, 0, 10, 8).also { it.weight = 1f })
        top.addView(ui.tv(sub, 12.5f, ui.toneColor(tone) ?: ui.col(R.color.text_muted), true))
        box.addView(top)
        box.addView(bar(act, pct, ui.toneColor(tone) ?: color))
        ui.onTap(box) { go() }
        return box
    }
}

internal fun batteryStatus(act: MainActivity, b: Dev.Battery): String = act.getString(
    when (b.status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> R.string.bat_charging
        BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.bat_discharging
        BatteryManager.BATTERY_STATUS_FULL -> R.string.bat_full
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.bat_not_charging
        else -> R.string.bat_unknown
    }
)

// ====================================================================== STORAGE management
class StorageScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private lateinit var body: LinearLayout
    private var alive = true
    private var cacheBytes = -1L

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        col.addView(body)
        col.addView(brandFooter(act))
        refresh()
        return sv
    }

    override fun onResume() {
        alive = true
        refresh()
        Thread {
            val n = Dev.dirSize(act.cacheDir) + Dev.dirSize(act.externalCacheDir)
            act.runOnUiThread {
                if (alive) {
                    cacheBytes = n
                    refresh()
                }
            }
        }.start()
    }

    override fun onPause() {
        alive = false
    }

    private fun refresh() {
        body.removeAllViews()
        val st = Dev.storage(act)
        val hero = ui.card(16, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val g = GaugeView(act)
        val tone = toneOfPct(st.usedPct)
        g.setColors(ui.toneColor(tone) ?: ui.col(R.color.ok), 0xFFFFB547.toInt())
        g.setData(st.usedPct, String.format(Locale.US, "%.0f%%", st.usedPct), act.getString(R.string.sec_storage))
        hero.addView(gaugeBox(act, g))
        hero.addView(kv(act, act.getString(R.string.st_total), Fmt.size(st.total)), ui.lp(ui.MATCH, ui.WRAP, 8))
        hero.addView(kv(act, act.getString(R.string.st_used), Fmt.size(st.used) + String.format(Locale.US, "  (%.0f%%)", st.usedPct)))
        hero.addView(kv(act, act.getString(R.string.st_free), Fmt.size(st.free), tone))
        if (st.total > 0 && st.freePct < 10f) {
            hero.addView(
                note(act, act.getString(if (st.freePct < 5f) R.string.st_warn_critical else R.string.st_warn_low), if (st.freePct < 5f) Tone.BAD else Tone.WARN),
                ui.lp(ui.MATCH, ui.WRAP, 10, 0, 6, 6)
            )
        }
        body.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        // suggestions
        val sc = cardWithTitle(act, Ic.LIST, 0xFFFFB547.toInt(), act.getString(R.string.st_suggest))
        if (cacheBytes > 0) {
            sc.addView(actionRow(act, Ic.TRASH, 0xFF2EE6A0.toInt(), act.getString(R.string.st_sug_cache, Fmt.size(cacheBytes)), act.getString(R.string.st_sug_cache_d), { act.go(Dest.CLEANER, 0, false) }))
        }
        sc.addView(actionRow(act, Ic.FOLDER, 0xFF4DA3FF.toInt(), act.getString(R.string.st_sug_large), act.getString(R.string.st_sug_large_d), { act.go(Dest.FILES, 0, false) }))
        sc.addView(actionRow(act, Ic.GEAR, 0xFFA56BFF.toInt(), act.getString(R.string.st_sug_apps), act.getString(R.string.st_sug_apps_d),
            { launch(act, Intent(Settings.ACTION_APPLICATION_SETTINGS)) }))
        sc.addView(actionRow(act, Ic.STORAGE, 0xFFFFB547.toInt(), act.getString(R.string.st_sys_storage), act.getString(R.string.st_sys_storage_d),
            { launch(act, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }))
        body.addView(sc, ui.lp(ui.MATCH, ui.WRAP, 12))
    }
}

// ====================================================================== STORAGE CLEANER (own cache only)
class CleanerScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private var alive = true
    private var busy = false
    private lateinit var sizeTv: TextView
    private lateinit var detailTv: TextView
    private lateinit var resultBox: LinearLayout
    private lateinit var cleanBtn: LinearLayout
    private var cacheB = -1L
    private var extB = -1L
    private var shareB = -1L

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        val hero = ui.card(18, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.addView(ui.iconTile(Ic.TRASH, 0xFF2EE6A0.toInt(), 64, 34))
        sizeTv = ui.centered(ui.tv("…", 30f, ui.col(R.color.text_primary), true))
        hero.addView(sizeTv, ui.lp(ui.WRAP, ui.WRAP, 10))
        hero.addView(ui.centered(note(act, act.getString(R.string.cl_size_label))), ui.lp(ui.WRAP, ui.WRAP, 2))
        detailTv = ui.centered(note(act, ""))
        hero.addView(detailTv, ui.lp(ui.MATCH, ui.WRAP, 8))
        cleanBtn = ui.pillButton(act.getString(R.string.cl_clear), Ic.TRASH, true) { confirm() }
        hero.addView(cleanBtn, ui.lp(ui.dp(220), ui.dp(50), 14))
        resultBox = LinearLayout(act)
        resultBox.orientation = LinearLayout.VERTICAL
        hero.addView(resultBox, ui.lp(ui.MATCH, ui.WRAP, 10))
        col.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        // what is and is not touched
        val ic = cardWithTitle(act, Ic.SHIELD, ui.col(R.color.ok), act.getString(R.string.cl_scope))
        ic.addView(note(act, act.getString(R.string.cl_scope_d)), ui.lp(ui.MATCH, ui.WRAP, 0, 4))
        ic.addView(actionRow(act, Ic.GEAR, 0xFFA56BFF.toInt(), act.getString(R.string.cl_other_apps), act.getString(R.string.cl_other_apps_d),
            { launch(act, Intent(Settings.ACTION_APPLICATION_SETTINGS)) }))
        col.addView(ic, ui.lp(ui.MATCH, ui.WRAP, 12))

        // three different things
        val dc = cardWithTitle(act, Ic.INFO, ui.col(R.color.accent), act.getString(R.string.cl_diff))
        dc.addView(diffRow(act.getString(R.string.cl_diff_cache), act.getString(R.string.cl_diff_cache_d)))
        dc.addView(diffRow(act.getString(R.string.cl_diff_files), act.getString(R.string.cl_diff_files_d)))
        dc.addView(diffRow(act.getString(R.string.cl_diff_ram), act.getString(R.string.cl_diff_ram_d)))
        col.addView(dc, ui.lp(ui.MATCH, ui.WRAP, 12))
        col.addView(brandFooter(act))
        measure()
        return sv
    }

    private fun diffRow(t: String, d: String): View {
        val b = LinearLayout(act)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6))
        b.addView(ui.tv(t, 14f, ui.col(R.color.text_primary), true))
        b.addView(note(act, d), ui.lp(ui.MATCH, ui.WRAP, 2))
        return b
    }

    override fun onResume() {
        alive = true
    }

    override fun onPause() {
        alive = false
    }

    private fun measure() {
        Thread {
            val a = Dev.dirSize(act.cacheDir)
            val b = Dev.dirSize(act.externalCacheDir)
            val s = Dev.dirSize(java.io.File(act.cacheDir, "share"))
            act.runOnUiThread {
                if (!alive) return@runOnUiThread
                cacheB = a
                extB = b
                shareB = s
                show()
            }
        }.start()
    }

    private fun show() {
        val total = cacheB + extB
        sizeTv.text = Fmt.size(Math.max(total, 0L))
        detailTv.text = act.getString(R.string.cl_detail, Fmt.size(Math.max(cacheB, 0L)), Fmt.size(Math.max(extB, 0L)), Fmt.size(Math.max(shareB, 0L)))
        cleanBtn.alpha = if (total > 0) 1f else 0.5f
    }

    private fun confirm() {
        if (busy) return
        if (cacheB + extB <= 0) {
            Toast.makeText(act, R.string.cl_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(act)
            .setTitle(R.string.cl_confirm_title)
            .setMessage(act.getString(R.string.cl_confirm_msg, Fmt.size(cacheB + extB)))
            .setPositiveButton(R.string.cl_clear) { _, _ -> clean() }
            .setNegativeButton(R.string.t_cancel, null)
            .show()
    }

    private fun clean() {
        busy = true
        val before = Dev.freeNow()
        Thread {
            var freed = Dev.clearDir(act.cacheDir)
            freed += Dev.clearDir(act.externalCacheDir)
            val after = Dev.freeNow()
            act.runOnUiThread {
                busy = false
                if (!alive) return@runOnUiThread
                cacheB = Dev.dirSize(act.cacheDir)
                extB = Dev.dirSize(act.externalCacheDir)
                shareB = Dev.dirSize(java.io.File(act.cacheDir, "share"))
                show()
                resultBox.removeAllViews()
                resultBox.addView(kv(act, act.getString(R.string.cl_freed), Fmt.size(freed), Tone.OK))
                if (before >= 0 && after >= 0) {
                    resultBox.addView(kv(act, act.getString(R.string.cl_free_before), Fmt.size(before)))
                    resultBox.addView(kv(act, act.getString(R.string.cl_free_after), Fmt.size(after)))
                    resultBox.addView(note(act, act.getString(R.string.cl_measure_note)), ui.lp(ui.MATCH, ui.WRAP, 4))
                }
            }
        }.start()
    }
}

// ====================================================================== FILE management shortcuts + large file finder
class FilesScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private var alive = true
    private lateinit var resultBox: LinearLayout

    private class Found(val uri: Uri, val name: String, val size: Long) {
        var checked = false
    }

    private var found: List<Found> = emptyList()

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        val ac = cardWithTitle(act, Ic.FOLDER, 0xFF4DA3FF.toInt(), act.getString(R.string.fl_open))
        ac.addView(actionRow(act, Ic.FOLDER, 0xFF4DA3FF.toInt(), act.getString(R.string.fl_files_app), act.getString(R.string.fl_files_app_d), { openFilesApp() }))
        ac.addView(actionRow(act, Ic.DOWNLOAD, 0xFF2EE6A0.toInt(), act.getString(R.string.fl_downloads), act.getString(R.string.fl_downloads_d),
            { launch(act, Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) }))
        ac.addView(actionRow(act, Ic.LIST, 0xFFA56BFF.toInt(), act.getString(R.string.fl_pick), act.getString(R.string.fl_pick_d), { act.pickAndOpenFile() }))
        col.addView(ac, ui.lp(ui.MATCH, ui.WRAP, 8))

        val lc = cardWithTitle(act, Ic.STORAGE, 0xFFFFB547.toInt(), act.getString(R.string.fl_large))
        lc.addView(note(act, act.getString(R.string.fl_large_d)), ui.lp(ui.MATCH, ui.WRAP, 0, 8))
        lc.addView(ui.pillButton(act.getString(R.string.fl_choose_folder), Ic.FOLDER, true) { act.pickFolder { uri -> scan(uri) } }, ui.lp(ui.MATCH, ui.dp(48), 2))
        resultBox = LinearLayout(act)
        resultBox.orientation = LinearLayout.VERTICAL
        lc.addView(resultBox, ui.lp(ui.MATCH, ui.WRAP, 10))
        col.addView(lc, ui.lp(ui.MATCH, ui.WRAP, 12))

        val sc = cardWithTitle(act, Ic.SHIELD, ui.col(R.color.ok), act.getString(R.string.fl_safe))
        sc.addView(note(act, act.getString(R.string.fl_safe_d)))
        col.addView(sc, ui.lp(ui.MATCH, ui.WRAP, 12))
        col.addView(brandFooter(act))
        return sv
    }

    override fun onResume() {
        alive = true
    }

    override fun onPause() {
        alive = false
    }

    private fun openFilesApp() {
        try {
            val i = Intent("android.provider.action.BROWSE")
            i.data = DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
            return
        } catch (e: Exception) {
            // try the generic picker below
        }
        act.pickAndOpenFile()
    }

    private fun scan(tree: Uri) {
        resultBox.removeAllViews()
        resultBox.addView(note(act, act.getString(R.string.fl_scanning)))
        Thread {
            val list = scanTree(tree)
            act.runOnUiThread {
                if (!alive) return@runOnUiThread
                found = list
                renderFound()
            }
        }.start()
    }

    private fun scanTree(tree: Uri): List<Found> {
        val out = ArrayList<Found>()
        try {
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.addLast(Pair(DocumentsContract.getTreeDocumentId(tree), 0))
            var visited = 0
            while (queue.isNotEmpty() && visited < 6000 && alive) {
                val (id, depth) = queue.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
                var c: Cursor? = null
                try {
                    c = act.contentResolver.query(
                        children,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE
                        ), null, null, null
                    )
                    while (c != null && c.moveToNext()) {
                        visited++
                        val did = c.getString(0) ?: continue
                        val name = c.getString(1) ?: ""
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < 4) queue.addLast(Pair(did, depth + 1))
                        } else if (size > 0) {
                            out.add(Found(DocumentsContract.buildDocumentUriUsingTree(tree, did), name, size))
                        }
                    }
                } catch (e: Exception) {
                    // skip folders that cannot be read
                } finally {
                    c?.close()
                }
            }
        } catch (e: Exception) {
            // return what was found
        }
        return out.sortedByDescending { it.size }.take(15)
    }

    private fun renderFound() {
        resultBox.removeAllViews()
        if (found.isEmpty()) {
            resultBox.addView(note(act, act.getString(R.string.fl_none)))
            return
        }
        resultBox.addView(note(act, act.getString(R.string.fl_found, found.size)), ui.lp(ui.MATCH, ui.WRAP, 0, 6))
        for (f in found) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4))
            val cb = CheckBox(act)
            cb.isChecked = f.checked
            cb.setOnCheckedChangeListener { _, v -> f.checked = v }
            row.addView(cb)
            val n = ui.tv(f.name, 13.5f, ui.col(R.color.text_primary))
            n.maxLines = 2
            row.addView(n, ui.lp(0, ui.WRAP, 0, 0, 4, 8).also { it.weight = 1f })
            row.addView(ui.tv(Fmt.size(f.size), 13f, ui.col(R.color.accent), true))
            resultBox.addView(row)
        }
        resultBox.addView(
            ui.pillButton(act.getString(R.string.fl_delete_selected), Ic.TRASH, false) { confirmDelete() },
            ui.lp(ui.MATCH, ui.dp(46), 10)
        )
    }

    private fun confirmDelete() {
        val sel = found.filter { it.checked }
        if (sel.isEmpty()) {
            Toast.makeText(act, R.string.fl_select_first, Toast.LENGTH_SHORT).show()
            return
        }
        val total = sel.sumOf { it.size }
        AlertDialog.Builder(act)
            .setTitle(R.string.fl_confirm_title)
            .setMessage(act.getString(R.string.fl_confirm_msg, sel.size, Fmt.size(total)))
            .setPositiveButton(R.string.t_delete) { _, _ -> delete(sel) }
            .setNegativeButton(R.string.t_cancel, null)
            .show()
    }

    private fun delete(sel: List<Found>) {
        Thread {
            var ok = 0
            val gone = HashSet<Uri>()
            for (f in sel) {
                try {
                    if (DocumentsContract.deleteDocument(act.contentResolver, f.uri)) {
                        ok++
                        gone.add(f.uri)
                    }
                } catch (e: Exception) {
                    // keep going with the next file
                }
            }
            act.runOnUiThread {
                Toast.makeText(act, act.getString(R.string.fl_deleted, ok), Toast.LENGTH_LONG).show()
                if (alive) {
                    found = found.filter { !gone.contains(it.uri) }
                    renderFound()
                }
            }
        }.start()
    }
}

// ====================================================================== RAM
class RamScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private lateinit var body: LinearLayout
    private lateinit var resultTv: TextView
    private var lastResult: RamCleaner.Result? = null
    private var cleaning = false

    private fun confirmClean() {
        if (cleaning) return
        AlertDialog.Builder(act)
            .setTitle(R.string.ram_clean)
            .setMessage(R.string.ram_clean_q)
            .setPositiveButton(R.string.ram_clean) { _, _ -> runClean() }
            .setNegativeButton(R.string.t_cancel, null)
            .show()
    }

    private fun runClean() {
        cleaning = true
        resultTv.setTextColor(ui.col(R.color.text_muted))
        resultTv.text = act.getString(R.string.ram_clean_running)
        resultTv.visibility = View.VISIBLE
        RamCleaner.clean(act) { r ->
            cleaning = false
            if (act.isFinishing || act.isDestroyed) return@clean
            lastResult = r
            refresh()
        }
    }

    private fun showResult(r: RamCleaner.Result) {
        resultTv.visibility = View.VISIBLE
        when {
            r.apps == 0 -> {
                resultTv.text = act.getString(R.string.ram_clean_none)
                resultTv.setTextColor(ui.col(R.color.text_muted))
            }
            r.freed > 0 -> {
                resultTv.text = act.getString(R.string.ram_clean_done, r.apps, Fmt.size(r.freed))
                resultTv.setTextColor(ui.col(R.color.ok))
            }
            else -> {
                resultTv.text = act.getString(R.string.ram_clean_nofree, r.apps)
                resultTv.setTextColor(ui.col(R.color.text_muted))
            }
        }
    }


    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        col.addView(body)
        col.addView(brandFooter(act))
        refresh()
        return sv
    }

    override fun onResume() {
        refresh()
    }

    private fun refresh() {
        body.removeAllViews()
        val mi = Dev.ram(act)
        val total = mi.totalMem
        val used = total - mi.availMem
        val pct = if (total > 0) used * 100f / total else 0f
        val hero = ui.card(16, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val g = GaugeView(act)
        g.setColors(if (mi.lowMemory) ui.col(R.color.bad) else 0xFF2EE6E0.toInt(), ui.col(R.color.accent3))
        g.setData(pct, String.format(Locale.US, "%.0f%%", pct), "RAM")
        hero.addView(gaugeBox(act, g))
        // nominal (the size on the box, e.g. 4 GB) and the real usable size, same logic as the device report
        val adv = try {
            Collector(act, act.applicationContext).ramAdvertised(total)
        } catch (e: Exception) {
            Pair(total, false)
        }
        val gib = 1024L * 1024L * 1024L
        val advBase = if (adv.first % gib == 0L) "${adv.first / gib} GB" else Fmt.size(adv.first)
        val advText = if (adv.second) advBase else act.getString(R.string.v_ram_est, advBase)
        hero.addView(kv(act, act.getString(R.string.k_ram_nominal), advText), ui.lp(ui.MATCH, ui.WRAP, 8))
        hero.addView(kv(act, act.getString(R.string.k_ram_usable), Fmt.size(total)))
        hero.addView(kv(act, act.getString(R.string.ram_used), Fmt.size(used)))
        hero.addView(kv(act, act.getString(R.string.ram_avail), Fmt.size(mi.availMem)))
        hero.addView(kv(act, act.getString(R.string.ram_threshold), Fmt.size(mi.threshold)))
        hero.addView(
            kv(act, act.getString(R.string.ram_state), act.getString(if (mi.lowMemory) R.string.ram_low else R.string.ram_ok), if (mi.lowMemory) Tone.BAD else Tone.OK)
        )
        val pss = Dev.ownPss(act)
        if (pss > 0) hero.addView(kv(act, act.getString(R.string.ram_own), Fmt.size(pss)))
        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER
        btns.addView(ui.pillButton(act.getString(R.string.ram_clean), Ic.TRASH, true) { confirmClean() }, ui.lp(ui.WRAP, ui.dp(44), 0, 0, 0, 8))
        btns.addView(ui.pillButton(act.getString(R.string.btn_refresh), Ic.REFRESH, false) { refresh() }, ui.lp(ui.WRAP, ui.dp(44)))
        hero.addView(btns, ui.lp(ui.WRAP, ui.WRAP, 12))
        resultTv = note(act, "")
        resultTv.visibility = View.GONE
        hero.addView(resultTv, ui.lp(ui.MATCH, ui.WRAP, 10))
        lastResult?.let { showResult(it) }
        body.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        val ec = cardWithTitle(act, Ic.INFO, ui.col(R.color.accent), act.getString(R.string.ram_vs))
        ec.addView(note(act, act.getString(R.string.ram_vs_d)), ui.lp(ui.MATCH, ui.WRAP, 0, 4))
        body.addView(ec, ui.lp(ui.MATCH, ui.WRAP, 12))

        val tc = cardWithTitle(act, Ic.LIST, 0xFF2EE6E0.toInt(), act.getString(R.string.ram_tips))
        for (t in intArrayOf(R.string.ram_tip1, R.string.ram_tip2, R.string.ram_tip3, R.string.ram_tip4)) {
            tc.addView(note(act, "•  " + act.getString(t)), ui.lp(ui.MATCH, ui.WRAP, 3, 3))
        }
        tc.addView(actionRow(act, Ic.GEAR, 0xFFA56BFF.toInt(), act.getString(R.string.ram_apps), act.getString(R.string.ram_apps_d),
            { launch(act, Intent(Settings.ACTION_APPLICATION_SETTINGS)) }), ui.lp(ui.MATCH, ui.WRAP, 6))
        body.addView(tc, ui.lp(ui.MATCH, ui.WRAP, 12))

        val nc = ui.card(14, 24)
        nc.addView(note(act, act.getString(R.string.ram_honest)))
        body.addView(nc, ui.lp(ui.MATCH, ui.WRAP, 12))
    }
}

// ====================================================================== BATTERY
class BatteryScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private lateinit var body: LinearLayout
    private var rcv: BroadcastReceiver? = null

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        body = LinearLayout(act)
        body.orientation = LinearLayout.VERTICAL
        col.addView(body)
        col.addView(brandFooter(act))
        refresh()
        return sv
    }

    // the system pushes battery changes (no polling) and the receiver only lives while this page is visible
    override fun onResume() {
        refresh()
        if (rcv == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    refresh()
                }
            }
            rcv = r
            ContextCompat.registerReceiver(act, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onPause() {
        val r = rcv
        if (r != null) {
            try {
                act.unregisterReceiver(r)
            } catch (e: Exception) {
                // already gone
            }
        }
        rcv = null
    }

    private fun healthText(h: Int): Int = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> R.string.bat_h_good
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.bat_h_overheat
        BatteryManager.BATTERY_HEALTH_DEAD -> R.string.bat_h_dead
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.bat_h_overvolt
        BatteryManager.BATTERY_HEALTH_COLD -> R.string.bat_h_cold
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.bat_h_fail
        else -> R.string.bat_unknown
    }

    private fun refresh() {
        body.removeAllViews()
        val b = Dev.battery(act)
        val hero = ui.card(16, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        if (b == null || b.pct < 0) {
            hero.addView(note(act, act.getString(R.string.bat_no_data)))
            body.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))
            return
        }
        val g = GaugeView(act)
        val low = b.pct <= 15 && !b.charging
        g.setColors(if (low) ui.col(R.color.bad) else ui.col(R.color.ok), ui.col(R.color.accent3))
        g.setData(b.pct.toFloat(), b.pct.toString() + "%", act.getString(R.string.sec_battery))
        hero.addView(gaugeBox(act, g))
        hero.addView(kv(act, act.getString(R.string.bat_status), batteryStatus(act, b)), ui.lp(ui.MATCH, ui.WRAP, 8))
        val plug = when (b.plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> R.string.bat_p_ac
            BatteryManager.BATTERY_PLUGGED_USB -> R.string.bat_p_usb
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.bat_p_wireless
            else -> R.string.bat_p_none
        }
        hero.addView(kv(act, act.getString(R.string.bat_power), act.getString(plug)))
        val t = b.tempC
        if (t != null) {
            hero.addView(kv(act, act.getString(R.string.bat_temp), String.format(Locale.US, "%.1f °C", t), if (t >= 45f) Tone.BAD else if (t >= 40f) Tone.WARN else Tone.OK))
        }
        val v = b.voltageMv
        if (v != null) hero.addView(kv(act, act.getString(R.string.bat_voltage), String.format(Locale.US, "%.2f V", v / 1000.0)))
        if (!b.tech.isNullOrEmpty()) hero.addView(kv(act, act.getString(R.string.bat_tech), b.tech))
        // health is shown only when Android reports it; it is never guessed from the charge percentage
        if (b.health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
            hero.addView(kv(act, act.getString(R.string.bat_health), act.getString(healthText(b.health)), if (b.health == BatteryManager.BATTERY_HEALTH_GOOD) Tone.OK else Tone.BAD))
        } else {
            hero.addView(kv(act, act.getString(R.string.bat_health), act.getString(R.string.bat_not_reported)))
        }
        val cy = b.cycles
        // same row as the others; when the phone does not provide the value the row says so in a short, muted text
        hero.addView(kv(act, act.getString(R.string.bat_cycles), cy?.toString() ?: act.getString(R.string.bat_cycles_na), if (cy == null) Tone.MUTED else Tone.NONE))
        if (t != null && t >= 45f) hero.addView(note(act, act.getString(R.string.bat_warn_hot), Tone.BAD), ui.lp(ui.MATCH, ui.WRAP, 8))
        if (low) hero.addView(note(act, act.getString(R.string.bat_warn_low), Tone.WARN), ui.lp(ui.MATCH, ui.WRAP, 8))
        if (b.health != BatteryManager.BATTERY_HEALTH_UNKNOWN && b.health != BatteryManager.BATTERY_HEALTH_GOOD) {
            hero.addView(note(act, act.getString(R.string.bat_warn_health), Tone.BAD), ui.lp(ui.MATCH, ui.WRAP, 8))
        }
        body.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        val tc = cardWithTitle(act, Ic.BOLT, 0xFF27D98B.toInt(), act.getString(R.string.bat_tips))
        for (x in intArrayOf(R.string.bat_tip1, R.string.bat_tip2, R.string.bat_tip3, R.string.bat_tip4, R.string.bat_tip5)) {
            tc.addView(note(act, "•  " + act.getString(x)), ui.lp(ui.MATCH, ui.WRAP, 3, 3))
        }
        body.addView(tc, ui.lp(ui.MATCH, ui.WRAP, 12))

        val sc = ui.card(10, 24)
        sc.addView(actionRow(act, Ic.BATTERY, 0xFF27D98B.toInt(), act.getString(R.string.bat_usage), act.getString(R.string.bat_usage_d),
            { launch(act, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }))
        sc.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
        sc.addView(actionRow(act, Ic.BOLT, 0xFFFFB547.toInt(), act.getString(R.string.bat_saver), act.getString(R.string.bat_saver_d),
            { launch(act, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }))
        body.addView(sc, ui.lp(ui.MATCH, ui.WRAP, 12))
    }
}

// ====================================================================== DEVICE status
class DeviceScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui

    override fun build(): View {
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        val c = ui.card(14, 24)
        val dm = act.resources.displayMetrics
        c.addView(kv(act, act.getString(R.string.k_manufacturer), Build.MANUFACTURER))
        val modelRow = kv(act, act.getString(R.string.k_model), Build.MODEL)
        c.addView(modelRow)
        c.addView(kv(act, act.getString(R.string.k_android_ver), Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")"))
        if (Build.VERSION.SDK_INT >= 23) c.addView(kv(act, act.getString(R.string.k_sec_patch), Build.VERSION.SECURITY_PATCH))
        c.addView(kv(act, act.getString(R.string.k_abi_main), if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "—"))
        c.addView(kv(act, act.getString(R.string.k_cores), Runtime.getRuntime().availableProcessors().toString()))
        c.addView(kv(act, act.getString(R.string.dv_screen), dm.widthPixels.toString() + " × " + dm.heightPixels + "  ·  " + dm.densityDpi + " dpi"))
        val online = Net.isOnline(act)
        val tr = when (Net.transport(act)) {
            Net.T_WIFI -> R.string.sp_net_wifi
            Net.T_CELL -> R.string.sp_net_cell
            Net.T_ETH -> R.string.sp_net_eth
            Net.T_VPN -> R.string.sp_net_vpn
            Net.T_NONE -> R.string.sp_net_none
            else -> R.string.sp_net_other
        }
        c.addView(kv(act, act.getString(R.string.dv_network), act.getString(tr), if (online) Tone.OK else Tone.BAD))
        col.addView(c, ui.lp(ui.MATCH, ui.WRAP, 8))

        // official name: resolved off the UI thread; the row appears above "Model" only when a real name was found
        DeviceNames.resolve(act) { name ->
            if (name != null && !act.isFinishing && !act.isDestroyed) {
                val at = c.indexOfChild(modelRow)
                if (at >= 0) c.addView(kv(act, act.getString(R.string.k_official_name), name), at)
            }
        }

        val ac = ui.card(10, 24)
        ac.addView(actionRow(act, Ic.INFO, ui.col(R.color.accent), act.getString(R.string.dv_full), act.getString(R.string.dv_full_d), { act.go(Dest.HOME, 0, true) }))
        ac.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
        ac.addView(actionRow(act, Ic.GEAR, 0xFF9B7BFF.toInt(), act.getString(R.string.tool_settings), act.getString(R.string.tool_settings_d),
            { launch(act, Intent(Settings.ACTION_SETTINGS)) }))
        ac.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
        ac.addView(actionRow(act, Ic.GEAR, 0xFFA56BFF.toInt(), act.getString(R.string.dv_about_phone), "", { launch(act, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }))
        col.addView(ac, ui.lp(ui.MATCH, ui.WRAP, 12))
        col.addView(brandFooter(act))
        return sv
    }
}
