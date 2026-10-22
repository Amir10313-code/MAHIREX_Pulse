package com.mahirex.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/** Shown when a widget is added (and when it is reconfigured): theme, accent colour, shown info, auto update. */
class WidgetConfigActivity : AppCompatActivity() {
    private lateinit var ui: Ui
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind = HomeWidgets.KIND_SPEED
    private var theme = 0
    private var accent = 0
    private var fields = 0
    private var interval = HomeWidgets.DEFAULT_INTERVAL
    private var opacity = 100
    private lateinit var previewBox: LinearLayout
    private val OPACITIES = intArrayOf(100, 85, 70, 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        ui = Ui(this)
        window.decorView.layoutDirection = ui.dir
        kind = HomeWidgets.kindOf(this, widgetId)
        val c = HomeWidgets.cfg(this, widgetId, kind)
        theme = c.theme
        accent = c.accent
        fields = c.fields
        interval = c.interval
        opacity = c.opacity
        setContentView(build())
    }

    private fun fieldLabels(): List<String> = when (kind) {
        HomeWidgets.KIND_TEST -> listOf(getString(R.string.sp_download), getString(R.string.sp_upload), getString(R.string.sp_ping) + " / " + getString(R.string.sp_jitter))
        HomeWidgets.KIND_HEALTH -> listOf(getString(R.string.sec_battery), getString(R.string.sec_storage), getString(R.string.wd_cfg_shortcuts))
        HomeWidgets.KIND_ORB -> listOf(getString(R.string.wd_cfg_f_state), getString(R.string.bat_temp), getString(R.string.bat_voltage))
        HomeWidgets.KIND_RINGS -> listOf(getString(R.string.sec_storage), getString(R.string.sec_ram), getString(R.string.wd_cfg_f_labels))
        HomeWidgets.KIND_ID -> listOf(getString(R.string.k_android_ver), getString(R.string.wd_cfg_f_batbar), getString(R.string.wd_cfg_f_code))
        else -> listOf(getString(R.string.sp_download), getString(R.string.sp_upload), getString(R.string.wd_cfg_status))
    }

    private fun build(): View {
        val root = FrameLayout(this)
        root.addView(AuroraView(this), FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(14), ui.dp(24), ui.dp(14), ui.dp(24))
        sv.addView(col)
        root.addView(sv, FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))

        col.addView(ui.tv(getString(R.string.wd_cfg_title), 22f, ui.col(R.color.text_primary), true), ui.lp(ui.WRAP, ui.WRAP, 0, 12, 6))

        // live preview: follows every change below
        val pc = ui.card(12, 24)
        pc.addView(ui.sectionTitle(getString(R.string.wd_cfg_preview)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        previewBox = LinearLayout(this)
        previewBox.orientation = LinearLayout.VERTICAL
        pc.addView(previewBox, ui.lp(ui.MATCH, ui.WRAP))
        col.addView(pc, ui.lp(ui.MATCH, ui.WRAP, 8))

        // theme
        val tc = ui.card(12, 24)
        tc.addView(ui.sectionTitle(getString(R.string.set_theme)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        val names = listOf(R.string.set_system, R.string.set_light, R.string.set_dark)
        val trow = LinearLayout(this)
        trow.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<View>()
        for ((i, n) in names.withIndex()) {
            val chip = ui.centered(ui.tv(getString(n), 13.5f, ui.col(R.color.text_primary), true))
            chip.setPadding(ui.dp(6), ui.dp(12), ui.dp(6), ui.dp(12))
            chip.setOnClickListener {
                theme = i
                paint(chips, theme)
                drawPreview()
            }
            chips.add(chip)
            trow.addView(chip, ui.lp(0, ui.WRAP, 0, 0, if (i == 0) 0 else 6, 0).also { it.weight = 1f })
        }
        paint(chips, theme)
        tc.addView(trow)
        col.addView(tc, ui.lp(ui.MATCH, ui.WRAP, 8))

        // accent colour
        val ac = ui.card(12, 24)
        ac.addView(ui.sectionTitle(getString(R.string.wd_cfg_color)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        val arow = LinearLayout(this)
        arow.orientation = LinearLayout.HORIZONTAL
        arow.gravity = Gravity.CENTER
        val dots = ArrayList<View>()
        for (i in HomeWidgets.ACCENT_LIGHT.indices) {
            val d = View(this)
            d.setOnClickListener {
                accent = i
                paintDots(dots)
                drawPreview()
            }
            dots.add(d)
            arow.addView(d, ui.lp(0, ui.dp(40), 0, 0, 4, 4).also { it.weight = 1f })
        }
        paintDots(dots)
        ac.addView(arow)
        col.addView(ac, ui.lp(ui.MATCH, ui.WRAP, 12))

        // shown info
        val fc = ui.card(12, 24)
        fc.addView(ui.sectionTitle(getString(R.string.wd_cfg_show)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        val bits = intArrayOf(HomeWidgets.F1, HomeWidgets.F2, HomeWidgets.F3)
        for ((i, l) in fieldLabels().withIndex()) {
            fc.addView(toggleRow(l, fields and bits[i] != 0) { on ->
                fields = if (on) fields or bits[i] else fields and bits[i].inv()
                drawPreview()
            })
        }
        col.addView(fc, ui.lp(ui.MATCH, ui.WRAP, 12))

        // background opacity (graphic widgets)
        if (HomeWidgets.isGraphic(kind)) {
            val oc = ui.card(12, 24)
            oc.addView(ui.sectionTitle(getString(R.string.wd_cfg_opacity)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
            val orow = LinearLayout(this)
            orow.orientation = LinearLayout.HORIZONTAL
            val ochips = ArrayList<View>()
            for ((i, o) in OPACITIES.withIndex()) {
                val chip = ui.centered(ui.tv(String.format(java.util.Locale.getDefault(), "%d%%", o), 13.5f, ui.col(R.color.text_primary), true))
                chip.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12))
                chip.setOnClickListener {
                    opacity = o
                    paintOpacity(ochips)
                    drawPreview()
                }
                ochips.add(chip)
                orow.addView(chip, ui.lp(0, ui.WRAP, 0, 0, if (i == 0) 0 else 6, 0).also { it.weight = 1f })
            }
            paintOpacity(ochips)
            oc.addView(orow)
            col.addView(oc, ui.lp(ui.MATCH, ui.WRAP, 12))
        }

        // automatic update interval
        val uc = ui.card(12, 24)
        uc.addView(ui.sectionTitle(getString(R.string.wd_cfg_interval)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        val graphic = HomeWidgets.isGraphic(kind)
        val ivs = HomeWidgets.intervalsFor(kind)
        val labels = if (graphic) listOf(R.string.wd_int_15m, R.string.wd_int_30m, R.string.wd_int_1h, R.string.wd_int_off)
        else listOf(R.string.wd_int_30s, R.string.wd_int_5m, R.string.wd_int_30m, R.string.wd_int_off)
        val irow = LinearLayout(this)
        irow.orientation = LinearLayout.HORIZONTAL
        val ichips = ArrayList<View>()
        for ((i, n) in labels.withIndex()) {
            val chip = ui.centered(ui.tv(getString(n), 13.5f, ui.col(R.color.text_primary), true))
            chip.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12))
            chip.setOnClickListener {
                interval = ivs[i]
                paintIntervals(ichips)
            }
            ichips.add(chip)
            irow.addView(chip, ui.lp(0, ui.WRAP, 0, 0, if (i == 0) 0 else 6, 0).also { it.weight = 1f })
        }
        paintIntervals(ichips)
        uc.addView(irow)
        uc.addView(ui.tv(getString(if (graphic) R.string.wd_cfg_auto_note_min else R.string.wd_cfg_auto_note), 12f, ui.col(R.color.text_muted)), ui.lp(ui.MATCH, ui.WRAP, 10, 4, 6, 6))
        col.addView(uc, ui.lp(ui.MATCH, ui.WRAP, 12))

        col.addView(ui.pillButton(getString(R.string.wd_cfg_save), Ic.CHECK, true) { save() }, ui.lp(ui.MATCH, ui.dp(52), 18))
        drawPreview()
        return root
    }

    private fun paintIntervals(chips: List<View>) {
        val sel = HomeWidgets.intervalsFor(kind).indexOf(interval)
        for ((i, c) in chips.withIndex()) ui.feel(c, if (i == sel) ui.glass(14, true) else ui.glass(14), 14)
    }

    private fun paintOpacity(chips: List<View>) {
        val sel = OPACITIES.indexOf(opacity)
        for ((i, c) in chips.withIndex()) ui.feel(c, if (i == sel) ui.glass(14, true) else ui.glass(14), 14)
    }

    /** a small copy of the widget drawn with the exact colours the real one will use */
    private fun drawPreview() {
        val pal = HomeWidgets.palette(this, theme, accent)
        previewBox.removeAllViews()
        if (HomeWidgets.isGraphic(kind)) {
            drawGraphicPreview(pal)
            return
        }
        val bg = GradientDrawable()
        bg.cornerRadius = ui.dpf(22f)
        bg.setColor(pal.bg)
        previewBox.background = bg
        previewBox.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        val title = when (kind) {
            HomeWidgets.KIND_TEST -> R.string.wd_test_title
            HomeWidgets.KIND_HEALTH -> R.string.wd_health_title
            else -> R.string.wd_speed_title
        }
        previewBox.addView(ui.tv(getString(title), 12f, pal.accent, true))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val a = if (kind == HomeWidgets.KIND_HEALTH) "66%" else "48.2"
        val b = if (kind == HomeWidgets.KIND_HEALTH) "91%" else "12.6"
        val la = if (kind == HomeWidgets.KIND_HEALTH) getString(R.string.sec_battery) else "↓ " + getString(R.string.sp_download)
        val lb = if (kind == HomeWidgets.KIND_HEALTH) getString(R.string.sec_storage) else "↑ " + getString(R.string.sp_upload)
        for (pair in listOf(Pair(la, a), Pair(lb, b))) {
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.addView(ui.tv(pair.first, 10.5f, pal.mu))
            cell.addView(ui.tv(pair.second, 24f, pal.accent, true))
            row.addView(cell, ui.lp(0, ui.WRAP).also { it.weight = 1f })
        }
        previewBox.addView(row, ui.lp(ui.MATCH, ui.WRAP, 6))
        val pill = ui.centered(ui.tv(getString(if (kind == HomeWidgets.KIND_TEST) R.string.wd_start_test else R.string.wd_measure), 12f, pal.accent, true))
        pill.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
        val pg = GradientDrawable()
        pg.cornerRadius = ui.dpf(16f)
        pg.setColor(ui.alpha(pal.accent, 51))
        pg.setStroke(ui.dp(1), ui.alpha(pal.accent, 153))
        pill.background = pg
        previewBox.addView(pill, ui.lp(ui.MATCH, ui.WRAP, 10))
    }

    /** the real design of the graphic widgets: same Canvas renderers, colours and strings, with the phone's current values */
    private fun drawGraphicPreview(pal: HomeWidgets.Pal) {
        val bg = GradientDrawable()
        bg.cornerRadius = ui.dpf(22f)
        bg.setColor(ui.alpha(pal.bg, opacity * 255 / 100))
        previewBox.background = bg
        previewBox.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        fun on(bit: Int) = fields and bit != 0
        fun ring(bmp: android.graphics.Bitmap, side: Int): ImageView {
            val iv = ImageView(this)
            iv.setImageBitmap(bmp)
            iv.layoutParams = LinearLayout.LayoutParams(ui.dp(side), ui.dp(side))
            return iv
        }
        fun line(label: String, value: String): View {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.addView(ui.tv(label, 11f, pal.mu), ui.lp(0, ui.WRAP).also { it.weight = 1f })
            r.addView(ui.tv(value, 13f, pal.tx, true))
            return r
        }
        when (kind) {
            HomeWidgets.KIND_ORB -> {
                val b = HomeWidgets.batSnap(this)
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val box = FrameLayout(this)
                box.addView(ring(HomeWidgets.orbBitmap(360, b.pct, b.charging, pal), 120))
                val centre = LinearLayout(this)
                centre.orientation = LinearLayout.VERTICAL
                centre.gravity = Gravity.CENTER
                centre.addView(ui.tv(if (b.pct >= 0) b.pct.toString() + "%" else "—", 24f, pal.tx, true))
                box.addView(centre, FrameLayout.LayoutParams(ui.dp(120), ui.dp(120)))
                row.addView(box)
                val info = LinearLayout(this)
                info.orientation = LinearLayout.VERTICAL
                info.addView(ui.tv(getString(R.string.wd_orb_title), 12f, pal.accent, true), ui.lp(ui.MATCH, ui.WRAP, 0, 4))
                if (on(HomeWidgets.F1)) info.addView(line(getString(R.string.bat_status), HomeWidgets.stateText(this, b)), ui.lp(ui.MATCH, ui.WRAP, 3))
                if (on(HomeWidgets.F2)) info.addView(line(getString(R.string.bat_temp), HomeWidgets.tempText(b)), ui.lp(ui.MATCH, ui.WRAP, 3))
                if (on(HomeWidgets.F3)) info.addView(line(getString(R.string.bat_voltage), HomeWidgets.voltText(b)), ui.lp(ui.MATCH, ui.WRAP, 3))
                row.addView(info, ui.lp(0, ui.WRAP, 0, 0, 12, 0).also { it.weight = 1f })
                previewBox.addView(row)
            }
            HomeWidgets.KIND_RINGS -> {
                val m = HomeWidgets.memSnap(this)
                var s1 = on(HomeWidgets.F1)
                var s2 = on(HomeWidgets.F2)
                if (!s1 && !s2) {
                    s1 = true
                    s2 = true
                }
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.addView(ring(HomeWidgets.dualBitmap(360, if (s1) m.stoPct.coerceAtLeast(0) else null, if (s2) m.ramPct.coerceAtLeast(0) else null, pal, accent), 120))
                val info = LinearLayout(this)
                info.orientation = LinearLayout.VERTICAL
                info.addView(ui.tv(getString(R.string.wd_rings_title), 12f, pal.accent, true), ui.lp(ui.MATCH, ui.WRAP, 0, 4))
                if (s1) {
                    info.addView(ui.tv(getString(R.string.sec_storage) + "  " + m.stoPct + "%", 13f, pal.tx, true), ui.lp(ui.MATCH, ui.WRAP, 3))
                    if (on(HomeWidgets.F3)) info.addView(ui.tv(HomeWidgets.usedTotal(m.stoUsed, m.stoTotal), 11f, pal.mu))
                }
                if (s2) {
                    info.addView(ui.tv(getString(R.string.sec_ram) + "  " + m.ramPct + "%", 13f, pal.tx, true), ui.lp(ui.MATCH, ui.WRAP, 8))
                    if (on(HomeWidgets.F3)) info.addView(ui.tv(HomeWidgets.usedTotal(m.ramUsed, m.ramTotal), 11f, pal.mu))
                }
                row.addView(info, ui.lp(0, ui.WRAP, 0, 0, 12, 0).also { it.weight = 1f })
                previewBox.addView(row)
            }
            else -> {
                val b = HomeWidgets.batSnap(this)
                previewBox.addView(ui.tv(getString(R.string.wd_id_title), 11f, pal.accent, true))
                previewBox.addView(ui.tv(HomeWidgets.deviceTitle(this), 19f, pal.tx, true), ui.lp(ui.MATCH, ui.WRAP, 2))
                if (on(HomeWidgets.F3)) previewBox.addView(ui.tv(android.os.Build.MODEL ?: "", 11f, pal.mu))
                if (on(HomeWidgets.F1)) previewBox.addView(ui.tv("Android " + android.os.Build.VERSION.RELEASE + "  ·  API " + android.os.Build.VERSION.SDK_INT, 12f, pal.tx, true), ui.lp(ui.MATCH, ui.WRAP, 6))
                if (on(HomeWidgets.F2) && b.pct >= 0) {
                    previewBox.addView(ui.tv(getString(R.string.sec_battery) + "  " + b.pct + "%" + if (b.charging) "  ⚡" else "", 10.5f, pal.mu), ui.lp(ui.MATCH, ui.WRAP, 8, 3))
                    val bar = ImageView(this)
                    bar.setImageBitmap(HomeWidgets.barBitmap(480, 20, b.pct, pal, b.low, rtl))
                    bar.scaleType = ImageView.ScaleType.FIT_XY
                    previewBox.addView(bar, ui.lp(ui.MATCH, ui.dp(10)))
                }
            }
        }
    }

    private fun paint(chips: List<View>, sel: Int) {
        for ((i, c) in chips.withIndex()) ui.feel(c, if (i == sel) ui.glass(14, true) else ui.glass(14), 14)
    }

    private fun paintDots(dots: List<View>) {
        for ((i, d) in dots.withIndex()) {
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(HomeWidgets.ACCENT_LIGHT[i])
            if (i == accent) g.setStroke(ui.dp(3), ui.col(R.color.text_primary))
            d.background = g
        }
    }

    private fun toggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        var on = initial
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12))
        ui.feel(row, null, 14)
        row.addView(ui.tv(label, 15f, ui.col(R.color.text_primary)), ui.lp(0, ui.WRAP, 0, 0, 0, 10).also { it.weight = 1f })
        val sw = androidx.appcompat.widget.SwitchCompat(this)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, v ->
            on = v
            onChange(v)
        }
        row.addView(sw)
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
        return row
    }

    private fun save() {
        HomeWidgets.saveCfg(this, widgetId, HomeWidgets.Cfg(theme, accent, fields, interval, opacity))
        HomeWidgets.update(this, AppWidgetManager.getInstance(this), widgetId, kind, true)
        HomeWidgets.scheduleNext(this)
        val r = Intent()
        r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_OK, r)
        finish()
    }
}
