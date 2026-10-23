package com.mahirex.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnLayout
import java.io.File
import java.util.Locale

abstract class Screen {
    abstract fun build(): View
    open fun onData() {}
    open fun onProgress(i: Int, n: Int, name: String) {}
    open fun onResume() {}
    open fun onPause() {}
}

internal fun pageScroll(ctx: Context): ScrollView {
    val sv = ScrollView(ctx)
    sv.overScrollMode = View.OVER_SCROLL_NEVER
    sv.isVerticalScrollBarEnabled = false
    return sv
}

internal fun pageColumn(act: MainActivity): LinearLayout {
    val l = LinearLayout(act)
    l.orientation = LinearLayout.VERTICAL
    val ui = act.ui
    l.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(28))
    return l
}

/** brand footer used at the bottom of the pages */
internal fun brandFooter(act: MainActivity): View {
    val ui = act.ui
    val box = LinearLayout(act)
    box.orientation = LinearLayout.VERTICAL
    box.gravity = Gravity.CENTER_HORIZONTAL
    box.setPadding(ui.dp(16), ui.dp(26), ui.dp(16), ui.dp(10))
    box.addView(ui.wordmark(17f), LinearLayout.LayoutParams(ui.WRAP, ui.WRAP))
    val more = ui.centered(ui.tv(act.getString(R.string.tag_more).uppercase(), 10.5f, ui.col(R.color.text_muted)))
    more.letterSpacing = 0.18f
    box.addView(more, ui.lp(ui.WRAP, ui.WRAP, 6))
    val know = ui.centered(ui.tv(act.getString(R.string.tag_know), 12.5f, ui.col(R.color.accent), false))
    know.setTypeface(know.typeface, Typeface.ITALIC)
    box.addView(know, ui.lp(ui.WRAP, ui.WRAP, 4))
    return box
}

// ====================================================================== HOME (my phone)
class HomeScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private lateinit var scan: ScanRingView
    private lateinit var scanTitle: TextView
    private lateinit var scanSub: TextView
    private lateinit var gaugeCard: LinearLayout
    private lateinit var catCard: LinearLayout
    private lateinit var exploreCard: LinearLayout
    private lateinit var gBat: GaugeView
    private lateinit var gRam: GaugeView
    private lateinit var gSto: GaugeView

    override fun build(): View {
        val sv = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        // hero
        val hero = ui.card(18, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        scan = ScanRingView(act)
        hero.addView(scan, LinearLayout.LayoutParams(ui.dp(170), ui.dp(170)))
        scanTitle = ui.centered(ui.tv("", 19f, ui.col(R.color.text_primary), true))
        hero.addView(scanTitle, ui.lp(ui.WRAP, ui.WRAP, 10))
        scanSub = ui.centered(ui.tv("", 13f, ui.col(R.color.text_muted)))
        hero.addView(scanSub, ui.lp(ui.WRAP, ui.WRAP, 4))
        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER
        btns.addView(ui.pillButton(act.getString(R.string.btn_export), Ic.SHARE, true) { act.showExport() }, ui.lp(ui.WRAP, ui.dp(44), 0, 0, 0, 8))
        btns.addView(ui.pillButton(act.getString(R.string.btn_refresh), Ic.REFRESH, false) { act.startLoad() }, ui.lp(ui.WRAP, ui.dp(44)))
        hero.addView(btns, ui.lp(ui.WRAP, ui.WRAP, 16))
        col.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        // quick access to the new tools
        val quick = LinearLayout(act)
        quick.orientation = LinearLayout.HORIZONTAL
        quick.addView(quickTile(Ic.GAUGE, R.string.sp_title, R.string.sp_quick_d, 0xFF4DA3FF.toInt()) { act.go(Dest.SPEED, 0, false) }, ui.lp(0, ui.MATCH, 0, 0, 0, 0).also { it.weight = 1f })
        quick.addView(quickTile(Ic.HEART, R.string.menu_tools, R.string.tools_quick_d, 0xFFFF6B8A.toInt()) { act.go(Dest.TOOLS, 0, false) }, ui.lp(0, ui.MATCH, 0, 0, 10, 0).also { it.weight = 1f })
        col.addView(quick, ui.lp(ui.MATCH, ui.WRAP, 12))

        // gauges
        gaugeCard = ui.card(14, 24)
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        gBat = GaugeView(act)
        gBat.setColors(ui.col(R.color.ok), ui.col(R.color.accent3))
        gRam = GaugeView(act)
        gSto = GaugeView(act)
        gSto.setColors(0xFFFFB547.toInt(), 0xFFFF6BB5.toInt())
        row.addView(gBat, ui.weight(ui.WRAP))
        row.addView(gRam, ui.weight(ui.WRAP))
        row.addView(gSto, ui.weight(ui.WRAP))
        gaugeCard.addView(row)
        col.addView(gaugeCard, ui.lp(ui.MATCH, ui.WRAP, 12))

        // categories
        catCard = ui.card(10, 24)
        col.addView(catCard, ui.lp(ui.MATCH, ui.WRAP, 12))

        // explore strip
        exploreCard = ui.card(12, 24)
        exploreCard.addView(ui.sectionTitle(act.getString(R.string.home_explore)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 6))
        val items = listOf(
            Triple(Ic.PHONE, R.string.ft_device, "device"),
            Triple(Ic.CHIP, R.string.ft_hw, "cpu"),
            Triple(Ic.WIFI, R.string.ft_net, "network"),
            Triple(Ic.SENSOR, R.string.ft_sensors, "sensors"),
            Triple(Ic.GEAR, R.string.ft_sys, "system"),
            Triple(Ic.GAUGE, R.string.ft_live, "live")
        )
        val palette = intArrayOf(0xFF4DA3FF.toInt(), 0xFF2EE6A0.toInt(), 0xFF4DA3FF.toInt(), 0xFFA56BFF.toInt(), 0xFFA56BFF.toInt(), 0xFF2EE6E0.toInt())
        for (r in 0 until 2) {
            val line = LinearLayout(act)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 3) {
                val k = r * 3 + c
                line.addView(exploreTile(items[k].first, act.getString(items[k].second), palette[k], items[k].third), ui.weight(ui.WRAP))
            }
            exploreCard.addView(line, ui.lp(ui.MATCH, ui.WRAP, if (r == 0) 0 else 6))
        }
        col.addView(exploreCard, ui.lp(ui.MATCH, ui.WRAP, 12))
        col.addView(brandFooter(act))
        refresh()
        return sv
    }

    private fun quickTile(icon: Int, title: Int, sub: Int, color: Int, go: () -> Unit): View {
        val t = LinearLayout(act)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
        ui.feel(t, ui.glass(24), 24)
        t.addView(ui.iconTile(icon, color, 42, 24))
        t.addView(ui.tv(act.getString(title), 15f, ui.col(R.color.text_primary), true), ui.lp(ui.MATCH, ui.WRAP, 10))
        val d = ui.tv(act.getString(sub), 12f, ui.col(R.color.text_muted))
        d.minLines = 2
        t.addView(d, ui.lp(ui.MATCH, ui.WRAP, 2))
        ui.onTap(t) { go() }
        return t
    }

    private fun exploreTile(icon: Int, title: String, color: Int, target: String): View {
        val t = LinearLayout(act)
        t.orientation = LinearLayout.VERTICAL
        t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12))
        ui.feel(t, null, 16)
        val ic = ui.icon(icon, 36, color, ui.col(R.color.accent2))
        t.addView(ic, LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)))
        val tt = ui.centered(ui.tv(title, 11.5f, ui.col(R.color.text_primary)))
        tt.maxLines = 2
        t.addView(tt, ui.lp(ui.MATCH, ui.WRAP, 8))
        ui.onTap(t) {
            if (target == "live") {
                act.go(Dest.LIVE, 0, false)
            } else {
                val r = act.report
                if (r != null) {
                    val idx = r.sections.indexOfFirst { it.id == target }
                    act.go(Dest.DETAILS, if (idx < 0) 0 else idx, false)
                }
            }
        }
        return t
    }

    private fun catRow(sec: InfoSection, idx: Int): View {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10))
        ui.feel(row, null, 14)
        row.addView(ui.iconTile(Ic.forSection(sec.id), Ic.colorFor(sec.id)))
        val tb = LinearLayout(act)
        tb.orientation = LinearLayout.VERTICAL
        tb.addView(ui.tv(sec.title, 15.5f, ui.col(R.color.text_primary), true))
        if (sec.preview.isNotEmpty()) {
            val p = ui.tv(sec.preview, 12f, ui.col(R.color.text_muted))
            p.maxLines = 1
            p.ellipsize = TextUtils.TruncateAt.END
            tb.addView(p)
        }
        row.addView(tb, ui.lp(0, ui.WRAP, 0, 0, 12, 8).also { it.weight = 1f })
        val m = ui.col(R.color.text_muted)
        val ch = ui.icon(if (ui.rtl) Ic.CHEV_L else Ic.CHEV_R, 18, m, m)
        ch.glow = false
        row.addView(ch, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
        ui.onTap(row) { act.go(Dest.DETAILS, idx, false) }
        return row
    }

    override fun onData() {
        refresh()
    }

    override fun onProgress(i: Int, n: Int, name: String) {
        if (act.loading) refresh()
    }

    private fun pctOf(part: Double?, total: Double?): Float {
        if (part == null || total == null || total <= 0.0) return 0f
        return (part * 100.0 / total).toFloat()
    }

    private fun refresh() {
        val r = act.report
        if (act.loading || r == null) {
            scan.loading = true
            scanTitle.text = act.getString(R.string.scan_loading)
            scanSub.text = if (act.progN > 0) act.getString(R.string.scan_progress, act.progI, act.progN) + "  ·  " + act.progName else ""
            gaugeCard.visibility = View.GONE
            catCard.visibility = View.GONE
            exploreCard.visibility = View.GONE
            return
        }
        scan.loading = false
        scanTitle.text = act.getString(R.string.scan_done)
        scanSub.text = act.getString(R.string.scan_ready)
        gaugeCard.visibility = View.VISIBLE
        catCard.visibility = View.VISIBLE
        exploreCard.visibility = View.VISIBLE
        val bat = (r.num("battery_pct")?.toFloat() ?: 0f).coerceAtLeast(0f)
        gBat.setData(bat, bat.toInt().toString() + "%", act.getString(R.string.sec_battery))
        val ramP = pctOf(r.num("ram_used"), r.num("ram_usable"))
        gRam.setData(ramP, ramP.toInt().toString() + "%", "RAM")
        val tot = r.num("storage_total")
        val free = r.num("storage_free")
        val stoP = if (tot != null && free != null) pctOf(tot - free, tot) else 0f
        gSto.setData(stoP, stoP.toInt().toString() + "%", act.getString(R.string.sec_storage))

        catCard.removeAllViews()
        catCard.addView(ui.sectionTitle(act.getString(R.string.home_details)), ui.lp(ui.WRAP, ui.WRAP, 6, 4, 8))
        var first = true
        for ((idx, sec) in r.sections.withIndex()) {
            if (sec.id == "summary") continue
            if (!first) catCard.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 8, 8))
            first = false
            catCard.addView(catRow(sec, idx))
        }
    }
}

// ====================================================================== DETAILS (used for my phone and for other phones)
class ReportScreen(
    private val act: MainActivity,
    private val report: Report,
    private val startIdx: Int
) : Screen() {
    private val ui = act.ui
    private lateinit var scroll: ScrollView
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var chipRow: LinearLayout
    private lateinit var content: LinearLayout
    private val chipViews = ArrayList<TextView>()
    private val cards = ArrayList<View>()
    private var activeIdx = -1
    private var pendingStartScroll = false

    override fun build(): View {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL

        chipScroll = HorizontalScrollView(act)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.overScrollMode = View.OVER_SCROLL_NEVER
        chipScroll.layoutDirection = View.LAYOUT_DIRECTION_LTR
        chipRow = LinearLayout(act)
        chipRow.layoutDirection = View.LAYOUT_DIRECTION_LTR
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(10))
        chipRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (pendingStartScroll && chipRow.width > 0) {
                pendingStartScroll = false
                chipScroll.scrollTo(if (ui.rtl) chipRow.width else 0, 0)
            }
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP))

        scroll = ScrollView(act)
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        content = LinearLayout(act)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, ui.dp(4), 0, ui.dp(24))
        scroll.addView(content)
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> updateActive(y) }
        root.addView(scroll, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))

        render()
        if (startIdx in cards.indices && startIdx > 0) {
            scroll.doOnLayout {
                scroll.scrollTo(0, cards[startIdx].top)
                setActive(startIdx)
            }
        }
        return root
    }

    private fun render() {
        content.removeAllViews()
        chipRow.removeAllViews()
        chipViews.clear()
        cards.clear()
        activeIdx = -1
        for (sec in report.sections) {
            val card = LinearLayout(act)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10))
            card.background = ui.glass(22)

            val head = LinearLayout(act)
            head.orientation = LinearLayout.HORIZONTAL
            head.gravity = Gravity.CENTER_VERTICAL
            head.addView(ui.iconTile(Ic.forSection(sec.id), Ic.colorFor(sec.id), 36, 20))
            val title = ui.tv(sec.title, 17f, ui.col(R.color.accent), true)
            head.addView(title, ui.lp(ui.WRAP, ui.WRAP, 0, 0, 10, 0))
            card.addView(head, ui.lp(ui.MATCH, ui.WRAP, 0, 6))

            var indent = 0
            for (item in sec.items) {
                if (item.header) {
                    if (item.level >= 2) {
                        indent = ui.dp(12)
                        val h = ui.tv("\u25C2 " + item.key, 13f, ui.col(R.color.text_primary), true)
                        h.setPaddingRelative(ui.dp(12), ui.dp(12), 0, ui.dp(2))
                        card.addView(h)
                    } else {
                        indent = 0
                        val h = ui.tv(item.key, 14.5f, ui.col(R.color.accent3), true)
                        h.setPadding(0, ui.dp(14), 0, ui.dp(4))
                        card.addView(h)
                    }
                } else {
                    val row = LinearLayout(act)
                    row.orientation = LinearLayout.HORIZONTAL
                    row.setPaddingRelative(indent, ui.dp(8), 0, ui.dp(8))
                    row.gravity = Gravity.CENTER_VERTICAL
                    val k = ui.tv(item.key, 13f, ui.col(R.color.text_muted))
                    row.addView(k, LinearLayout.LayoutParams(0, ui.WRAP, 4f))
                    val vlp = LinearLayout.LayoutParams(0, ui.WRAP, 6f)
                    vlp.marginStart = ui.dp(8)
                    val tc = ui.toneColor(item.tone)
                    if (tc != null) {
                        val box = LinearLayout(act)
                        box.orientation = LinearLayout.HORIZONTAL
                        box.addView(ui.pill(item.value, tc, 13f))
                        row.addView(box, vlp)
                    } else {
                        val v = ui.tv(item.value, 14f, ui.col(R.color.text_primary))
                        v.setTextIsSelectable(true)
                        row.addView(v, vlp)
                    }
                    card.addView(row)
                    card.addView(ui.divider(), LinearLayout.LayoutParams(ui.MATCH, 1))
                }
            }
            val lp = LinearLayout.LayoutParams(ui.MATCH, ui.WRAP)
            lp.setMargins(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6))
            content.addView(card, lp)
            cards.add(card)

            val idxNow = cards.size - 1
            val chip = ui.centered(ui.tv(sec.title, 13f, ui.col(R.color.text_primary)))
            chip.maxLines = 1
            chip.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
            ui.feel(chip, ui.glass(20), 20)
            chip.setOnClickListener {
                setActive(idxNow)
                scroll.post { scroll.smoothScrollTo(0, card.top) }
            }
            chipViews.add(chip)
        }
        val order = if (ui.rtl) chipViews.indices.reversed().toList() else chipViews.indices.toList()
        for (k in order) {
            val clp = LinearLayout.LayoutParams(ui.WRAP, ui.dp(40))
            clp.marginStart = ui.dp(8)
            chipRow.addView(chipViews[k], clp)
        }
        pendingStartScroll = true
        chipRow.requestLayout()
        setActive(0)
        content.addView(brandFooter(act))
    }

    private fun setActive(i: Int) {
        if (i == activeIdx || i !in chipViews.indices) return
        activeIdx = i
        for ((k, c) in chipViews.withIndex()) {
            if (k == i) {
                ui.feel(c, ui.glass(20, true), 20)
                c.setTextColor(ui.col(R.color.accent))
                c.setTypeface(Typeface.DEFAULT_BOLD)
            } else {
                ui.feel(c, ui.glass(20), 20)
                c.setTextColor(ui.col(R.color.text_primary))
                c.setTypeface(Typeface.DEFAULT)
            }
        }
        val chip = chipViews[i]
        chip.post {
            chip.requestRectangleOnScreen(Rect(-ui.dp(40), 0, chip.width + ui.dp(40), chip.height), false)
        }
    }

    private fun updateActive(y: Int) {
        if (cards.isEmpty()) return
        var idx = 0
        for (k in cards.indices) {
            if (cards[k].top <= y + ui.dp(60)) idx = k
        }
        if (!scroll.canScrollVertically(1)) idx = cards.size - 1
        setActive(idx)
    }
}

// ====================================================================== OTHER PHONE (read a shared report)
class OtherScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui

    override fun build(): View {
        val sv = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        val r = act.otherReport
        if (r == null) buildLanding(col) else buildDashboard(col, r)
        col.addView(brandFooter(act))
        return sv
    }

    private fun buildLanding(col: LinearLayout) {
        val hero = ui.card(20, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val ring = ScanRingView(act)
        ring.loading = false
        hero.addView(ring, LinearLayout.LayoutParams(ui.dp(120), ui.dp(120)))
        hero.addView(ui.centered(ui.tv(act.getString(R.string.other_hero_title), 19f, ui.col(R.color.text_primary), true)), ui.lp(ui.WRAP, ui.WRAP, 10))
        hero.addView(ui.centered(ui.tv(act.getString(R.string.other_hero_text), 13.5f, ui.col(R.color.text_muted))), ui.lp(ui.WRAP, ui.WRAP, 8))
        hero.addView(
            ui.pillButton(act.getString(R.string.btn_open_report), Ic.IMPORT, true) { act.openReportPicker() },
            ui.lp(ui.WRAP, ui.dp(48), 18)
        )
        col.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        val how = ui.card(16, 24)
        how.addView(ui.sectionTitle(act.getString(R.string.howto_title)), ui.lp(ui.WRAP, ui.WRAP, 0, 8))
        val steps = intArrayOf(R.string.howto_1, R.string.howto_2, R.string.howto_3)
        for ((i, s) in steps.withIndex()) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.isBaselineAligned = false
            row.gravity = Gravity.TOP
            row.setPadding(0, ui.dp(8), 0, ui.dp(8))
            val num = ui.centered(ui.tv((i + 1).toString(), 14f, ui.col(R.color.accent), true))
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(ui.alpha(ui.col(R.color.accent), 40))
            g.setStroke(ui.dp(1), ui.col(R.color.accent))
            num.background = g
            row.addView(num, LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)))
            val st = ui.tv(act.getString(s), 13.5f, ui.col(R.color.text_primary))
            st.setPadding(0, ui.dp(4), 0, ui.dp(4))
            row.addView(st, ui.lp(0, ui.WRAP, 0, 0, 12, 0).also { it.weight = 1f })
            how.addView(row)
        }
        col.addView(how, ui.lp(ui.MATCH, ui.WRAP, 12))
    }

    private fun tile(icon: Int, color: Int, label: String, value: String): View {
        val t = LinearLayout(act)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        t.background = ui.glass(18)
        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(ui.iconTile(icon, color, 30, 18))
        val l = ui.tv(label, 12f, ui.col(R.color.text_muted), true)
        l.maxLines = 1
        top.addView(l, ui.lp(ui.WRAP, ui.WRAP, 0, 0, 8, 0))
        t.addView(top)
        val v = ui.tv(value, 13.5f, ui.col(R.color.text_primary), true)
        v.maxLines = 3
        v.ellipsize = TextUtils.TruncateAt.END
        t.addView(v, ui.lp(ui.MATCH, ui.WRAP, 8))
        return t
    }

    private fun buildDashboard(col: LinearLayout, r: Report) {
        val persian = r.lang == "fa"
        val name = ((r.str("manufacturer") ?: "") + " " + (r.str("model") ?: "")).trim()
        val head = ui.card(18, 26)
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val logo = LogoView(act)
        row.addView(logo, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
        val tb = LinearLayout(act)
        tb.orientation = LinearLayout.VERTICAL
        val title = if (name.isEmpty()) act.getString(R.string.menu_other_phone) else act.getString(R.string.other_from, name)
        tb.addView(ui.tv(title, 17f, ui.col(R.color.text_primary), true))
        if (r.createdAt > 0L) {
            tb.addView(ui.tv(act.getString(R.string.other_created, Fmt.date(r.createdAt, persian)), 12.5f, ui.col(R.color.text_muted)), ui.lp(ui.WRAP, ui.WRAP, 3))
        }
        row.addView(tb, ui.lp(0, ui.WRAP, 0, 0, 12, 0).also { it.weight = 1f })
        head.addView(row)
        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.addView(ui.pillButton(act.getString(R.string.other_full), Ic.LIST, true) { act.go(Dest.OTHER_DETAILS, 0, false) }, ui.lp(ui.WRAP, ui.dp(44), 0, 0, 0, 8))
        btns.addView(ui.pillButton(act.getString(R.string.btn_open_report), Ic.IMPORT, false) { act.openReportPicker() }, ui.lp(ui.WRAP, ui.dp(44)))
        head.addView(btns, ui.lp(ui.WRAP, ui.WRAP, 14))
        val btns2 = LinearLayout(act)
        btns2.addView(ui.pillButton(act.getString(R.string.other_close), Ic.CLOSE, false) { act.closeOther() }, ui.lp(ui.WRAP, ui.dp(40)))
        head.addView(btns2, ui.lp(ui.WRAP, ui.WRAP, 8))
        col.addView(head, ui.lp(ui.MATCH, ui.WRAP, 8))

        if (r.fromText) {
            val n = ui.card(14, 18)
            n.addView(ui.tv(act.getString(R.string.other_txt_note), 13f, ui.col(R.color.warn)))
            col.addView(n, ui.lp(ui.MATCH, ui.WRAP, 12))
            return
        }

        // gauges
        val bat = r.num("battery_pct")
        val ramU = r.num("ram_used")
        val ramT = r.num("ram_usable")
        val stT = r.num("storage_total")
        val stF = r.num("storage_free")
        if (bat != null || (ramU != null && ramT != null) || (stT != null && stF != null)) {
            val gc = ui.card(14, 24)
            val gr = LinearLayout(act)
            gr.orientation = LinearLayout.HORIZONTAL
            val gB = GaugeView(act)
            gB.setColors(ui.col(R.color.ok), ui.col(R.color.accent3))
            val gR = GaugeView(act)
            val gS = GaugeView(act)
            gS.setColors(0xFFFFB547.toInt(), 0xFFFF6BB5.toInt())
            val bp = (bat ?: 0.0).toFloat().coerceAtLeast(0f)
            gB.setData(bp, bp.toInt().toString() + "%", act.getString(R.string.sec_battery))
            val rp = if (ramU != null && ramT != null && ramT > 0) (ramU * 100.0 / ramT).toFloat() else 0f
            gR.setData(rp, rp.toInt().toString() + "%", "RAM")
            val sp = if (stT != null && stF != null && stT > 0) ((stT - stF) * 100.0 / stT).toFloat() else 0f
            gS.setData(sp, sp.toInt().toString() + "%", act.getString(R.string.sec_storage))
            gr.addView(gB, ui.weight(ui.WRAP))
            gr.addView(gR, ui.weight(ui.WRAP))
            gr.addView(gS, ui.weight(ui.WRAP))
            gc.addView(gr)
            val ramTot = r.num("ram_total")
            if (ramTot != null && stT != null) {
                val info = ui.centered(
                    ui.tv(
                        "RAM " + Fmt.sizeShort(ramTot.toLong()) + "   •   " + act.getString(R.string.sec_storage) + " " + Fmt.sizeShort(stT.toLong()),
                        12.5f, ui.col(R.color.text_muted)
                    )
                )
                gc.addView(info, ui.lp(ui.MATCH, ui.WRAP, 4))
            }
            col.addView(gc, ui.lp(ui.MATCH, ui.WRAP, 12))
        }

        // spec tiles
        val tiles = ArrayList<View>()
        val cpu = r.str("cpu")
        if (cpu != null) {
            val cores = r.num("cores")?.toInt()
            tiles.add(tile(Ic.CHIP, 0xFF2EE6A0.toInt(), act.getString(R.string.sec_cpu), cpu + if (cores != null) "\n" + cores + " " + act.getString(R.string.g_cores) else ""))
        }
        r.str("gpu")?.let { tiles.add(tile(Ic.GPU, 0xFFFF9F43.toInt(), act.getString(R.string.sec_gpu), it)) }
        val disp = r.str("display")
        if (disp != null) {
            var v: String = disp
            r.num("refresh")?.let { v += "  @ " + String.format(Locale.US, "%.0f Hz", it) }
            r.num("diag")?.let { v += "\n" + String.format(Locale.US, "%.1f\"", it) }
            tiles.add(tile(Ic.DISPLAY, 0xFFFF6BB5.toInt(), act.getString(R.string.sec_display), v))
        }
        val cams = r.num("cameras")
        if (cams != null) {
            var v = cams.toInt().toString()
            r.num("max_mp")?.let { v += "  •  " + String.format(Locale.US, "%.0f MP", it) }
            tiles.add(tile(Ic.CAMERA, 0xFFFF6B8A.toInt(), act.getString(R.string.sec_camera), v))
        }
        r.num("sensors")?.let { tiles.add(tile(Ic.SENSOR, 0xFF9B7BFF.toInt(), act.getString(R.string.sec_sensors), it.toInt().toString())) }
        val an = r.str("android")
        if (an != null) tiles.add(tile(Ic.LAYERS, 0xFFA56BFF.toInt(), act.getString(R.string.g_android), "Android " + an + (r.num("api")?.let { " (API " + it.toInt() + ")" } ?: "")))
        val temp = r.num("battery_temp")
        if (temp != null) {
            var v = String.format(Locale.US, "%.1f °C", temp)
            r.num("battery_mv")?.let { v += "  •  " + it.toInt() + " mV" }
            tiles.add(tile(Ic.BATTERY, 0xFF27D98B.toInt(), act.getString(R.string.sec_battery), v))
        }
        val sec = r.bool("secure")
        if (sec != null) {
            val rooted = r.bool("rooted") == true
            tiles.add(tile(Ic.SHIELD, 0xFF5CE08A.toInt(), act.getString(R.string.sec_security), (if (sec) "\uD83D\uDD12 " else "\uD83D\uDD13 ") + act.getString(if (sec) R.string.yes else R.string.no) + if (rooted) "  •  root" else ""))
        }
        var i = 0
        while (i < tiles.size) {
            val line = LinearLayout(act)
            line.orientation = LinearLayout.HORIZONTAL
            val lp1 = LinearLayout.LayoutParams(0, ui.WRAP, 1f)
            lp1.marginEnd = ui.dp(6)
            line.addView(tiles[i], lp1)
            val lp2 = LinearLayout.LayoutParams(0, ui.WRAP, 1f)
            lp2.marginStart = ui.dp(6)
            if (i + 1 < tiles.size) line.addView(tiles[i + 1], lp2) else line.addView(View(act), lp2)
            col.addView(line, ui.lp(ui.MATCH, ui.WRAP, 12))
            i += 2
        }
    }
}

// ====================================================================== SETTINGS
class SettingsScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui

    private class Opt(val icon: Int?, val label: String)

    override fun build(): View {
        val sv = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        // appearance (drop-down menu)
        val modes = listOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val themeOpts = listOf(
            Opt(Ic.PHONE, act.getString(R.string.set_system)),
            Opt(Ic.SUN, act.getString(R.string.set_light)),
            Opt(Ic.MOON, act.getString(R.string.set_dark))
        )
        val themeSel = modes.indexOf(Prefs.nightMode()).coerceAtLeast(0)
        col.addView(dropdown(Ic.HALF, act.getString(R.string.set_theme), themeOpts, themeSel) { applyTheme(modes[it]) }, ui.lp(ui.MATCH, ui.WRAP, 8))

        // language (drop-down menu)
        val tags = ArrayList<String>()
        val langOpts = ArrayList<Opt>()
        tags.add("")
        langOpts.add(Opt(Ic.PHONE, act.getString(R.string.set_system)))
        for (item in Lang.items) {
            tags.add(item.tag)
            langOpts.add(Opt(null, item.name))
        }
        val langSel = tags.indexOf(Lang.current()).coerceAtLeast(0)
        col.addView(dropdown(Ic.GLOBE, act.getString(R.string.set_language), langOpts, langSel) { Lang.set(tags[it]) }, ui.lp(ui.MATCH, ui.WRAP, 12))

        val note = ui.centered(ui.tv(act.getString(R.string.set_auto_note), 12.5f, ui.col(R.color.text_muted)))
        col.addView(note, ui.lp(ui.MATCH, ui.WRAP, 14))
        col.addView(brandFooter(act))
        return sv
    }

    private fun applyTheme(mode: Int) {
        Prefs.setNightMode(mode)
        AppCompatDelegate.setDefaultNightMode(mode)
        // AppCompat only rebuilds the screen when the effective theme changes. Make sure the screen
        // really matches the chosen mode (e.g. choosing "night" again after "day").
        val wantNight: Boolean? = when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> null
        }
        act.window.decorView.postDelayed({
            if (wantNight != null && !act.isFinishing && !act.isDestroyed) {
                val isNight = (act.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isNight != wantNight) act.recreate()
            }
        }, 450)
    }

    /** card with a title and the current choice; tapping it unfolds the list of options */
    private fun dropdown(icon: Int, title: String, opts: List<Opt>, initial: Int, onPick: (Int) -> Unit): View {
        var sel = initial
        var open = false
        val muted = ui.col(R.color.text_muted)
        val card = ui.card(8, 24)

        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.isBaselineAligned = false
        head.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12))
        ui.feel(head, null, 18)
        head.addView(ui.icon(icon, 22), LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
        val tb = LinearLayout(act)
        tb.orientation = LinearLayout.VERTICAL
        tb.addView(ui.tv(title, 16f, ui.col(R.color.text_primary), true))
        val valueTv = ui.tv(opts[sel].label, 13.5f, ui.col(R.color.accent), true)
        tb.addView(valueTv, ui.lp(ui.WRAP, ui.WRAP, 2))
        head.addView(tb, ui.lp(0, ui.WRAP, 0, 0, 12, 12).also { it.weight = 1f })
        val chev = ui.icon(Ic.CHEV_R, 18, muted, muted)
        chev.glow = false
        chev.rotation = 90f
        head.addView(chev, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
        card.addView(head, ui.lp(ui.MATCH, ui.WRAP))

        val list = LinearLayout(act)
        list.orientation = LinearLayout.VERTICAL
        list.visibility = View.GONE
        card.addView(list, ui.lp(ui.MATCH, ui.WRAP))

        fun collapse() {
            if (!open) return
            open = false
            chev.animate().rotation(90f).setDuration(220).start()
            val from = list.height
            val anim = ValueAnimator.ofInt(from, 0)
            anim.duration = 240
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener {
                list.layoutParams.height = it.animatedValue as Int
                list.alpha = (it.animatedValue as Int) / Math.max(from, 1).toFloat()
                list.requestLayout()
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    list.visibility = View.GONE
                    list.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    list.alpha = 1f
                }
            })
            anim.start()
        }

        fun fill() {
            list.removeAllViews()
            list.addView(ui.divider(), ui.lp(ui.MATCH, 1, 2, 6, 6, 6))
            for ((i, o) in opts.withIndex()) {
                val selected = i == sel
                val row = LinearLayout(act)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.isBaselineAligned = false
                row.setPadding(ui.dp(12), ui.dp(13), ui.dp(12), ui.dp(13))
                val bg: GradientDrawable? = if (selected) {
                    val g = GradientDrawable()
                    g.cornerRadius = ui.dpf(14f)
                    g.setColor(ui.alpha(ui.col(R.color.accent), 38))
                    g.setStroke(ui.dp(1), ui.alpha(ui.col(R.color.accent), 150))
                    g
                } else null
                ui.feel(row, bg, 14)
                val c = if (selected) ui.col(R.color.accent) else muted
                if (o.icon != null) {
                    val ic = ui.icon(o.icon, 20, c, if (selected) ui.col(R.color.accent2) else c)
                    ic.glow = selected
                    row.addView(ic, ui.lp(ui.dp(22), ui.dp(22), 0, 0, 0, 12))
                }
                row.addView(
                    ui.tv(o.label, 15f, if (selected) ui.col(R.color.accent) else ui.col(R.color.text_primary), selected),
                    LinearLayout.LayoutParams(0, ui.WRAP, 1f)
                )
                if (selected) {
                    row.addView(ui.icon(Ic.CHECK, 20, ui.col(R.color.ok), ui.col(R.color.ok)), LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
                }
                row.setOnClickListener {
                    if (i != sel) {
                        sel = i
                        valueTv.text = o.label
                        fill()
                        card.postDelayed({ onPick(i) }, 260)
                    }
                    card.postDelayed({ collapse() }, 140)
                }
                list.addView(row, ui.lp(ui.MATCH, ui.WRAP, 2))
            }
        }

        fun expand() {
            if (open) return
            open = true
            fill()
            chev.animate().rotation(-90f).setDuration(220).start()
            val w = card.width - card.paddingLeft - card.paddingRight
            list.visibility = View.VISIBLE
            list.measure(
                View.MeasureSpec.makeMeasureSpec(Math.max(w, 1), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val target = list.measuredHeight
            val anim = ValueAnimator.ofInt(0, target)
            anim.duration = 260
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener {
                list.layoutParams.height = it.animatedValue as Int
                list.alpha = (it.animatedValue as Int) / Math.max(target, 1).toFloat()
                list.requestLayout()
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    list.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    list.alpha = 1f
                    list.requestLayout()
                }
            })
            list.layoutParams.height = 0
            anim.start()
        }

        head.setOnClickListener { if (open) collapse() else expand() }
        return card
    }
}

// ====================================================================== ABOUT
class AboutScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui

    override fun build(): View {
        val sv = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        val hero = ui.card(20, 28)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val brand = ImageView(act)
        brand.setImageResource(R.drawable.brand_icon)
        brand.scaleType = ImageView.ScaleType.FIT_CENTER
        hero.addView(brand, LinearLayout.LayoutParams(ui.dp(170), ui.dp(170)))
        hero.addView(ui.wordmark(28f), ui.lp(ui.WRAP, ui.WRAP, 10))
        val tag = ui.centered(ui.tv(act.getString(R.string.tagline), 13f, ui.col(R.color.text_muted)))
        hero.addView(tag, ui.lp(ui.WRAP, ui.WRAP, 6))
        val ver = try {
            act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "2.0"
        } catch (e: Throwable) {
            "2.0"
        }
        hero.addView(ui.pill(act.getString(R.string.about_version, ver), ui.col(R.color.accent), 12.5f), ui.lp(ui.WRAP, ui.WRAP, 14))
        col.addView(hero, ui.lp(ui.MATCH, ui.WRAP, 8))

        val d = ui.card(16, 24)
        d.addView(ui.centered(ui.tv(act.getString(R.string.about_desc), 14f, ui.col(R.color.text_primary))))
        col.addView(d, ui.lp(ui.MATCH, ui.WRAP, 12))

        val dev = ui.card(16, 24)
        dev.gravity = Gravity.CENTER_HORIZONTAL
        dev.addView(ui.centered(ui.tv(act.getString(R.string.about_dev), 12.5f, ui.col(R.color.text_muted), true)))
        val dn = GradText(act)
        dn.text = act.getString(R.string.dev_name)
        dn.textSize = 24f
        dn.setTypeface(dn.typeface, Typeface.BOLD)
        dn.letterSpacing = 0.18f
        dev.addView(dn, ui.lp(ui.WRAP, ui.WRAP, 6))
        col.addView(dev, ui.lp(ui.MATCH, ui.WRAP, 12))

        val p = ui.card(16, 24)
        val ph = LinearLayout(act)
        ph.orientation = LinearLayout.HORIZONTAL
        ph.gravity = Gravity.CENTER_VERTICAL
        ph.addView(ui.icon(Ic.SHIELD, 22), LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)))
        ph.addView(ui.tv(act.getString(R.string.about_privacy_title), 15f, ui.col(R.color.text_primary), true), ui.lp(ui.WRAP, ui.WRAP, 0, 0, 10, 0))
        p.addView(ph)
        p.addView(ui.tv(act.getString(R.string.about_privacy), 13.5f, ui.col(R.color.text_muted)), ui.lp(ui.MATCH, ui.WRAP, 8))
        col.addView(p, ui.lp(ui.MATCH, ui.WRAP, 12))
        col.addView(brandFooter(act))
        return sv
    }
}

// ====================================================================== LIVE (real-time analysis)
class LiveScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private lateinit var ramSpark: SparkView
    private lateinit var ramVal: TextView
    private lateinit var tempSpark: SparkView
    private lateinit var tempVal: TextView
    private lateinit var lvlSpark: SparkView
    private lateinit var lvlVal: TextView
    private lateinit var curSpark: SparkView
    private lateinit var curVal: TextView
    private lateinit var cpuSpark: SparkView
    private lateinit var cpuVal: TextView
    private var cpuOk = true

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            sample()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun panel(parent: LinearLayout, title: String, iconType: Int, color: Int, fixed: Boolean): Pair<SparkView, TextView> {
        val c = ui.card(14, 22)
        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(ui.iconTile(iconType, color, 30, 18))
        head.addView(ui.tv(title, 13.5f, ui.col(R.color.text_muted), true), ui.lp(0, ui.WRAP, 0, 0, 10, 0).also { it.weight = 1f })
        val v = ui.tv("--", 20f, ui.col(R.color.text_primary), true)
        head.addView(v)
        c.addView(head)
        val sp = SparkView(act)
        sp.setColors(color, ui.col(R.color.accent2))
        if (fixed) {
            sp.fixedMin = 0f
            sp.fixedMax = 100f
        }
        c.addView(sp, ui.lp(ui.MATCH, ui.dp(80), 10))
        parent.addView(c, ui.lp(ui.MATCH, ui.WRAP, 0, 12))
        return Pair(sp, v)
    }

    override fun build(): View {
        val sv = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)
        col.addView(ui.tv(act.getString(R.string.live_hint), 12.5f, ui.col(R.color.text_muted)), ui.lp(ui.WRAP, ui.WRAP, 4, 12, 4))
        val a = panel(col, act.getString(R.string.live_ram), Ic.MEMORY, ui.col(R.color.accent3), true)
        ramSpark = a.first
        ramVal = a.second
        val b = panel(col, act.getString(R.string.live_cpu), Ic.CHIP, 0xFF2EE6A0.toInt(), true)
        cpuSpark = b.first
        cpuVal = b.second
        val c = panel(col, act.getString(R.string.live_temp), Ic.GAUGE, 0xFFFF9F43.toInt(), false)
        tempSpark = c.first
        tempVal = c.second
        val d = panel(col, act.getString(R.string.live_level), Ic.BATTERY, ui.col(R.color.ok), true)
        lvlSpark = d.first
        lvlVal = d.second
        val e = panel(col, act.getString(R.string.live_current), Ic.SENSOR, 0xFFFF6BB5.toInt(), false)
        curSpark = e.first
        curVal = e.second
        col.addView(brandFooter(act))
        return sv
    }

    override fun onResume() {
        if (running) return
        running = true
        handler.post(tick)
    }

    override fun onPause() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (e: Throwable) {
        null
    }

    private fun sample() {
        try {
            val am = act.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            if (mi.totalMem > 0) {
                val used = (mi.totalMem - mi.availMem) * 100f / mi.totalMem
                ramSpark.push(used)
                ramVal.text = String.format(Locale.US, "%.0f%%", used)
            }
        } catch (e: Throwable) {
            // ignore
        }
        try {
            val i: Intent? = act.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temp >= 0) {
                    tempSpark.push(temp / 10f)
                    tempVal.text = String.format(Locale.US, "%.1f °C", temp / 10.0)
                }
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0 && scale > 0) {
                    val p = level * 100f / scale
                    lvlSpark.push(p)
                    lvlVal.text = String.format(Locale.US, "%.0f%%", p)
                }
            }
            val bm = act.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (cur != Int.MIN_VALUE && cur != 0) {
                curSpark.push(cur / 1000f)
                curVal.text = (cur / 1000).toString() + " mA"
            } else {
                curVal.text = act.getString(R.string.live_na)
                curVal.textSize = 12f
            }
        } catch (e: Throwable) {
            // ignore
        }
        if (cpuOk) {
            var sum = 0.0
            var n = 0
            val cores = Runtime.getRuntime().availableProcessors()
            for (k in 0 until cores) {
                val base = "/sys/devices/system/cpu/cpu" + k + "/cpufreq/"
                val cur = readLong(base + "scaling_cur_freq")
                val max = readLong(base + "cpuinfo_max_freq")
                if (cur != null && max != null && max > 0) {
                    sum += cur * 100.0 / max
                    n++
                }
            }
            if (n > 0) {
                val p = (sum / n).toFloat()
                cpuSpark.push(p)
                cpuVal.text = String.format(Locale.US, "%.0f%%", p)
            } else {
                cpuOk = false
                cpuVal.text = act.getString(R.string.live_na)
                cpuVal.textSize = 12f
            }
        }
    }
}
