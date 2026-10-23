package com.mahirex.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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
    private var corner = 1
    private var style = 0
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
        corner = c.corner
        style = c.style
        setContentView(build())
    }

    /** one toggle per shown element; the bit of a toggle is 1 shl its position (F1 = 1, F2 = 2, ...) */
    private fun fieldLabels(): List<String> = when (kind) {
        HomeWidgets.KIND_TEST -> listOf(getString(R.string.sp_download), getString(R.string.sp_upload), getString(R.string.sp_ping) + " / " + getString(R.string.sp_jitter), getString(R.string.wd_cfg_f_time))
        HomeWidgets.KIND_HEALTH -> listOf(getString(R.string.sec_battery), getString(R.string.sec_storage), getString(R.string.wd_cfg_shortcuts), getString(R.string.sec_ram), getString(R.string.bat_temp))
        HomeWidgets.KIND_ORB -> listOf(getString(R.string.wd_cfg_f_state), getString(R.string.bat_temp), getString(R.string.bat_voltage))
        HomeWidgets.KIND_RINGS -> listOf(getString(R.string.sec_storage), getString(R.string.sec_ram), getString(R.string.wd_cfg_f_labels))
        HomeWidgets.KIND_ID -> listOf(getString(R.string.k_android_ver), getString(R.string.wd_cfg_f_batbar), getString(R.string.wd_cfg_f_code))
        else -> listOf(getString(R.string.sp_download), getString(R.string.sp_upload), getString(R.string.wd_cfg_status), getString(R.string.wd_cfg_f_spark), getString(R.string.wd_cfg_f_last))
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
        for ((i, l) in fieldLabels().withIndex()) {
            val bit = 1 shl i
            fc.addView(toggleRow(l, fields and bit != 0) { on ->
                fields = if (on) fields or bit else fields and bit.inv()
                drawPreview()
            })
        }
        col.addView(fc, ui.lp(ui.MATCH, ui.WRAP, 12))

        // background opacity
        col.addView(chipCard(getString(R.string.wd_cfg_opacity), OPACITIES.map { String.format(java.util.Locale.getDefault(), "%d%%", it) }, OPACITIES.indexOf(opacity)) { i ->
            opacity = OPACITIES[i]
            drawPreview()
        }, ui.lp(ui.MATCH, ui.WRAP, 12))

        // corner style
        col.addView(chipCard(getString(R.string.wd_cfg_corner), listOf(getString(R.string.wd_corner_sharp), getString(R.string.wd_corner_std), getString(R.string.wd_corner_round)), corner) { i ->
            corner = i
            drawPreview()
        }, ui.lp(ui.MATCH, ui.WRAP, 12))

        // ring / gauge style
        col.addView(chipCard(getString(R.string.wd_cfg_gstyle), listOf(getString(R.string.wd_gs_thin), getString(R.string.wd_gs_thick)), style) { i ->
            style = i
            drawPreview()
        }, ui.lp(ui.MATCH, ui.WRAP, 12))

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

    /** a card with one row of equal chips; [onPick] gets the chosen position */
    private fun chipCard(title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit): View {
        val card = ui.card(12, 24)
        card.addView(ui.sectionTitle(title), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<View>()
        for ((i, n) in labels.withIndex()) {
            val chip = ui.centered(ui.tv(n, 13.5f, ui.col(R.color.text_primary), true))
            chip.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12))
            chip.setOnClickListener {
                paint(chips, i)
                onPick(i)
            }
            chips.add(chip)
            row.addView(chip, ui.lp(0, ui.WRAP, 0, 0, if (i == 0) 0 else 6, 0).also { it.weight = 1f })
        }
        paint(chips, selected)
        card.addView(row)
        return card
    }

    /** the real widget (its own RemoteViews) at its home-screen size; it is redrawn after every change above */
    private fun drawPreview() {
        previewBox.removeAllViews()
        val room = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 28 - 28
        val cfg = HomeWidgets.Cfg(theme, accent, fields, interval, opacity, corner, style)
        previewBox.addView(WidgetBuilder.previewView(this, kind, cfg, room))
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
        HomeWidgets.saveCfg(this, widgetId, HomeWidgets.Cfg(theme, accent, fields, interval, opacity, corner, style))
        HomeWidgets.update(this, AppWidgetManager.getInstance(this), widgetId, kind, true)
        HomeWidgets.scheduleNext(this)
        val r = Intent()
        r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_OK, r)
        finish()
    }
}
