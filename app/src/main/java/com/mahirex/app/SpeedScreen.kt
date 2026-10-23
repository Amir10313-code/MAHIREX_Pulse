package com.mahirex.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.Locale

// ====================================================================== SPEED TEST screen
class SpeedScreen(private val act: MainActivity) : Screen() {
    private val ui = act.ui
    private val runner = SpeedRunner(act.applicationContext)
    private var alive = true
    private var testing = false
    private var servers: List<SpeedServer> = emptyList()
    private var curServer: SpeedServer? = null

    private lateinit var gauge: SpeedGaugeView
    private lateinit var phaseTv: TextView
    private lateinit var startBtn: LinearLayout
    private lateinit var netChip: TextView
    private lateinit var heroBox: LinearLayout
    private lateinit var vDown: TextView
    private lateinit var vUp: TextView
    private lateinit var vPing: TextView
    private lateinit var vJitter: TextView
    private lateinit var noteTv: TextView
    private lateinit var errTv: TextView
    private lateinit var serverName: TextView
    private lateinit var serverSub: TextView
    private lateinit var histBox: LinearLayout
    private lateinit var trend: TrendView

    private val colDown get() = ui.col(R.color.accent3)
    private val colUp get() = 0xFFFF6BB5.toInt()

    // ------------------------------------------------------------ build
    override fun build(): View {
        servers = ServerManager.load(act)
        val sv: ScrollView = pageScroll(act)
        val col = pageColumn(act)
        sv.addView(col)

        // hero: dial + start button
        heroBox = ui.card(16, 28)
        heroBox.gravity = Gravity.CENTER_HORIZONTAL
        netChip = ui.pill("", ui.col(R.color.accent), 11.5f)
        heroBox.addView(netChip, ui.lp(ui.WRAP, ui.WRAP, 0, 6))
        gauge = SpeedGaugeView(act)
        gauge.setColors(colDown, ui.col(R.color.accent))
        heroBox.addView(gauge, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        phaseTv = ui.centered(ui.tv(act.getString(R.string.sp_ready), 14f, ui.col(R.color.text_muted)))
        heroBox.addView(phaseTv, ui.lp(ui.MATCH, ui.WRAP, 4))
        startBtn = ui.pillButton(act.getString(R.string.sp_start), Ic.PLAY, true) { toggle() }
        heroBox.addView(startBtn, ui.lp(ui.dp(190), ui.dp(50), 14))
        col.addView(heroBox, ui.lp(ui.MATCH, ui.WRAP, 8))

        // error / notes
        errTv = ui.centered(ui.tv("", 13.5f, ui.col(R.color.bad), true))
        errTv.visibility = View.GONE
        col.addView(errTv, ui.lp(ui.MATCH, ui.WRAP, 10, 0, 8, 8))

        // results
        val rc = ui.card(10, 24)
        val r1 = LinearLayout(act)
        r1.orientation = LinearLayout.HORIZONTAL
        val r2 = LinearLayout(act)
        r2.orientation = LinearLayout.HORIZONTAL
        val t1 = tile(Ic.ARROW_DOWN, act.getString(R.string.sp_download), colDown, "Mbps")
        val t2 = tile(Ic.ARROW_UP, act.getString(R.string.sp_upload), colUp, "Mbps")
        val t3 = tile(Ic.GAUGE, act.getString(R.string.sp_ping), ui.col(R.color.ok), "ms")
        val t4 = tile(Ic.SENSOR, act.getString(R.string.sp_jitter), 0xFFFFB547.toInt(), "ms")
        vDown = t1.second
        vUp = t2.second
        vPing = t3.second
        vJitter = t4.second
        r1.addView(t1.first, ui.weight(ui.WRAP))
        r1.addView(t2.first, ui.weight(ui.WRAP))
        r2.addView(t3.first, ui.weight(ui.WRAP))
        r2.addView(t4.first, ui.weight(ui.WRAP))
        rc.addView(r1)
        rc.addView(r2, ui.lp(ui.MATCH, ui.WRAP, 4))
        noteTv = ui.tv("", 12f, ui.col(R.color.text_muted))
        noteTv.visibility = View.GONE
        rc.addView(noteTv, ui.lp(ui.MATCH, ui.WRAP, 8, 2, 6, 6))
        col.addView(rc, ui.lp(ui.MATCH, ui.WRAP, 12))

        // server card
        val sc = ui.card(14, 24)
        val srow = LinearLayout(act)
        srow.orientation = LinearLayout.HORIZONTAL
        srow.gravity = Gravity.CENTER_VERTICAL
        srow.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
        ui.feel(srow, null, 16)
        srow.addView(ui.iconTile(Ic.GLOBE, ui.col(R.color.accent), 42, 22))
        val stb = LinearLayout(act)
        stb.orientation = LinearLayout.VERTICAL
        stb.addView(ui.tv(act.getString(R.string.sp_server), 12f, ui.col(R.color.text_muted)))
        serverName = ui.tv("", 15.5f, ui.col(R.color.text_primary), true)
        serverName.maxLines = 1
        serverName.ellipsize = android.text.TextUtils.TruncateAt.END
        stb.addView(serverName)
        serverSub = ui.tv("", 12f, ui.col(R.color.text_muted))
        serverSub.maxLines = 1
        serverSub.ellipsize = android.text.TextUtils.TruncateAt.END
        stb.addView(serverSub)
        srow.addView(stb, ui.lp(0, ui.WRAP, 0, 0, 12, 8).also { it.weight = 1f })
        val m = ui.col(R.color.text_muted)
        val ch = ui.icon(if (ui.rtl) Ic.CHEV_L else Ic.CHEV_R, 18, m, m)
        ch.glow = false
        srow.addView(ch, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
        ui.onTap(srow) { if (!testing) showServers() }
        sc.addView(srow)
        col.addView(sc, ui.lp(ui.MATCH, ui.WRAP, 12))

        // history
        val hc = ui.card(14, 24)
        hc.addView(ui.sectionTitle(act.getString(R.string.sp_trend)), ui.lp(ui.WRAP, ui.WRAP, 2, 8, 4))
        trend = TrendView(act)
        trend.colA = colDown
        trend.colB = colUp
        hc.addView(trend, ui.lp(ui.MATCH, ui.dp(110), 0, 10))
        hc.addView(ui.sectionTitle(act.getString(R.string.sp_history)), ui.lp(ui.WRAP, ui.WRAP, 4, 6, 4))
        histBox = LinearLayout(act)
        histBox.orientation = LinearLayout.VERTICAL
        hc.addView(histBox)
        col.addView(hc, ui.lp(ui.MATCH, ui.WRAP, 12))

        // notes
        val nc = ui.card(14, 24)
        nc.addView(ui.tv(act.getString(R.string.sp_data_note), 12.5f, ui.col(R.color.text_muted)))
        nc.addView(ui.tv(act.getString(R.string.sp_privacy_note), 12.5f, ui.col(R.color.text_muted)), ui.lp(ui.MATCH, ui.WRAP, 8))
        col.addView(nc, ui.lp(ui.MATCH, ui.WRAP, 12))
        col.addView(brandFooter(act))

        refreshNet()
        refreshServerCard()
        refreshHistory()
        return sv
    }

    private fun tile(icon: Int, label: String, color: Int, unit: String): Pair<View, TextView> {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10))
        box.addView(ui.iconTile(icon, color, 38, 20))
        val tb = LinearLayout(act)
        tb.orientation = LinearLayout.VERTICAL
        tb.addView(ui.tv(label, 12f, ui.col(R.color.text_muted)))
        val v = ui.tv("—", 20f, ui.col(R.color.text_primary), true)
        v.maxLines = 1
        tb.addView(v)
        tb.addView(ui.tv(unit, 11f, ui.col(R.color.text_muted)))
        box.addView(tb, ui.lp(ui.WRAP, ui.WRAP, 0, 0, 10, 4))
        return Pair(box, v)
    }

    // ------------------------------------------------------------ lifecycle
    override fun onResume() {
        alive = true
        refreshNet()
        if (act.speedAutoStart) {
            act.speedAutoStart = false
            if (!testing) startTest()
        }
    }

    override fun onPause() {
        // leaving the screen stops every running request: nothing keeps using the network in the background
        alive = false
        if (testing) {
            runner.cancel()
            setIdle()
        }
    }

    // ------------------------------------------------------------ test control
    private fun toggle() {
        if (testing) {
            runner.cancel()
        } else {
            startTest()
        }
    }

    private fun chosen(): SpeedServer? {
        val id = SpeedStore.selectedId(act)
        return if (id.isEmpty()) null else servers.firstOrNull { it.id == id }
    }

    private fun startTest() {
        if (runner.running) return
        testing = true
        errTv.visibility = View.GONE
        noteTv.visibility = View.GONE
        vDown.text = "—"
        vUp.text = "—"
        vPing.text = "—"
        vJitter.text = "—"
        gauge.setValue(0.0, true)
        gauge.caption = ""
        setBtn(act.getString(R.string.sp_stop), Ic.CLOSE)
        phaseTv.text = act.getString(R.string.sp_phase_select)
        refreshNet()
        runner.start(servers, chosen(), listener)
    }

    private fun setIdle() {
        testing = false
        setBtn(act.getString(R.string.sp_again), Ic.REFRESH)
    }

    private fun setBtn(label: String, icon: Int) {
        (startBtn.getChildAt(0) as IconView).type = icon
        (startBtn.getChildAt(1) as TextView).text = label
    }

    private val listener = object : SpeedRunner.Listener {
        override fun onPhase(p: SpeedRunner.Phase, server: SpeedServer?) {
            if (!alive) return
            when (p) {
                SpeedRunner.Phase.SELECT -> phaseTv.text = act.getString(R.string.sp_phase_select)
                SpeedRunner.Phase.PING -> {
                    curServer = server
                    phaseTv.text = act.getString(R.string.sp_phase_ping)
                    refreshServerCard()
                }
                SpeedRunner.Phase.DOWN -> {
                    phaseTv.text = act.getString(R.string.sp_phase_down)
                    gauge.setColors(colDown, ui.col(R.color.accent))
                    gauge.caption = act.getString(R.string.sp_download)
                    gauge.setValue(0.0, true)
                }
                SpeedRunner.Phase.UP -> {
                    phaseTv.text = act.getString(R.string.sp_phase_up)
                    gauge.setColors(ui.col(R.color.accent2), colUp)
                    gauge.caption = act.getString(R.string.sp_upload)
                    gauge.setValue(0.0, true)
                    val cs = curServer
                    if (server != null && cs != null && server.id != cs.id) {
                        noteTv.text = act.getString(R.string.sp_up_via, server.label)
                        noteTv.visibility = View.VISIBLE
                    }
                }
                else -> {}
            }
        }

        override fun onPing(ping: Double, jitter: Double) {
            if (!alive) return
            vPing.text = String.format(Locale.US, "%.0f", ping)
            vJitter.text = String.format(Locale.US, "%.1f", jitter)
        }

        override fun onLive(p: SpeedRunner.Phase, mbps: Double) {
            if (!alive) return
            gauge.setValue(mbps)
        }

        override fun onPhaseResult(p: SpeedRunner.Phase, mbps: Double) {
            if (!alive) return
            val s = fmt(mbps)
            if (p == SpeedRunner.Phase.DOWN) vDown.text = s else if (p == SpeedRunner.Phase.UP) vUp.text = s
            gauge.setValue(mbps)
        }

        override fun onDone(r: SpeedResult) {
            if (!alive) return
            phaseTv.text = act.getString(R.string.sp_phase_done)
            gauge.caption = ""
            gauge.setColors(colDown, ui.col(R.color.accent))
            gauge.setValue(r.down)
            gauge.caption = act.getString(R.string.sp_download)
            if (r.up < 0) vUp.text = "—"
            setIdle()
            refreshHistory()
        }

        override fun onError(kind: Int) {
            if (!alive) return
            setIdle()
            phaseTv.text = act.getString(R.string.sp_ready)
            gauge.setValue(0.0, true)
            gauge.caption = ""
            val msg = when (kind) {
                SpeedRunner.ERR_OFFLINE -> R.string.sp_err_offline
                SpeedRunner.ERR_SERVER -> R.string.sp_err_server
                SpeedRunner.ERR_DOWNLOAD -> R.string.sp_err_down
                else -> R.string.sp_err_cancel
            }
            errTv.text = act.getString(msg)
            errTv.setTextColor(if (kind == SpeedRunner.ERR_CANCEL) ui.col(R.color.text_muted) else ui.col(R.color.bad))
            errTv.visibility = View.VISIBLE
            refreshNet()
        }
    }

    private fun fmt(v: Double): String =
        if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)

    // ------------------------------------------------------------ info rows
    private fun refreshNet() {
        val t = Net.transport(act)
        val name = when (t) {
            Net.T_WIFI -> R.string.sp_net_wifi
            Net.T_CELL -> R.string.sp_net_cell
            Net.T_ETH -> R.string.sp_net_eth
            Net.T_VPN -> R.string.sp_net_vpn
            Net.T_NONE -> R.string.sp_net_none
            else -> R.string.sp_net_other
        }
        netChip.text = act.getString(name)
    }

    private fun refreshServerCard() {
        val id = SpeedStore.selectedId(act)
        val s = servers.firstOrNull { it.id == id }
        if (s == null) {
            serverName.text = act.getString(R.string.sp_auto)
            val cs = curServer
            serverSub.text = if (cs != null) act.getString(R.string.sp_best, cs.label) else act.getString(R.string.sp_auto_hint)
        } else {
            serverName.text = s.label
            val p = ServerManager.cachedPing(s.id)
            serverSub.text = s.country + if (p != null) "  ·  " + String.format(Locale.US, "%.0f ms", p) else ""
        }
    }

    private fun isFa(): Boolean = act.getString(R.string.lang_code) == "fa"

    private fun refreshHistory() {
        val list = SpeedStore.history(act)
        histBox.removeAllViews()
        if (list.isEmpty()) {
            trend.setData(DoubleArray(0), DoubleArray(0))
            histBox.addView(ui.tv(act.getString(R.string.sp_history_empty), 13f, ui.col(R.color.text_muted)), ui.lp(ui.WRAP, ui.WRAP, 4, 8, 4))
            return
        }
        val chart = list.take(20).reversed()
        trend.setData(DoubleArray(chart.size) { chart[it].down }, DoubleArray(chart.size) { chart[it].up })
        for ((i, r) in list.take(8).withIndex()) {
            if (i > 0) histBox.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 0, 4, 4))
            val row = LinearLayout(act)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8))
            val up = if (r.up >= 0) fmt(r.up) else "—"
            row.addView(
                ui.tv(
                    "↓ " + fmt(r.down) + "   ↑ " + up + "   · " + String.format(Locale.US, "%.0f ms", r.ping),
                    14.5f, ui.col(R.color.text_primary), true
                )
            )
            row.addView(ui.tv(Fmt.date(r.time, isFa()) + "  ·  " + r.server, 11.5f, ui.col(R.color.text_muted)), ui.lp(ui.MATCH, ui.WRAP, 2))
            histBox.addView(row)
        }
        histBox.addView(
            ui.pillButton(act.getString(R.string.sp_history_clear), Ic.TRASH, false) { confirmClear() },
            ui.lp(ui.WRAP, ui.dp(42), 8)
        )
    }

    private fun confirmClear() {
        AlertDialog.Builder(act)
            .setMessage(R.string.sp_history_clear_q)
            .setPositiveButton(R.string.t_delete) { _, _ ->
                SpeedStore.clear(act)
                refreshHistory()
                Toast.makeText(act, R.string.sp_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.t_cancel, null)
            .show()
    }

    // ------------------------------------------------------------ server picker
    private fun showServers() {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(14))
        box.background = ui.solidGlass(26)
        box.layoutDirection = ui.dir
        box.addView(ui.tv(act.getString(R.string.sp_servers_title), 18f, ui.col(R.color.text_primary), true))
        val status = ui.tv("", 12f, ui.col(R.color.text_muted))
        box.addView(status, ui.lp(ui.MATCH, ui.WRAP, 4, 6))

        val rows = LinearLayout(act)
        rows.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(act)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(rows)
        box.addView(scroll, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))

        val dialog = AlertDialog.Builder(act).setView(box).create()
        val checking = HashSet<String>()
        var open = true

        fun fill() {
            rows.removeAllViews()
            val sel = SpeedStore.selectedId(act)
            rows.addView(serverRow(act.getString(R.string.sp_auto), act.getString(R.string.sp_auto_hint), "", null, sel.isEmpty()) {
                SpeedStore.setSelectedId(act, "")
                refreshServerCard()
                dialog.dismiss()
            })
            for (s in servers) {
                val p = ServerManager.cachedPing(s.id)
                val st: String = when {
                    checking.contains(s.id) -> act.getString(R.string.sp_status_checking)
                    p != null -> String.format(Locale.US, "%.0f ms", p)
                    ServerManager.isChecked(s.id) -> act.getString(R.string.sp_status_offline)
                    else -> act.getString(R.string.sp_status_unknown)
                }
                val color: Int? = when {
                    checking.contains(s.id) -> null
                    p != null -> ui.col(R.color.ok)
                    ServerManager.isChecked(s.id) -> ui.col(R.color.bad)
                    else -> null
                }
                val extra = if (s.canUpload) "" else "  ·  " + act.getString(R.string.sp_down_only)
                rows.addView(serverRow(s.label, s.place + extra, st, color, sel == s.id) {
                    SpeedStore.setSelectedId(act, s.id)
                    refreshServerCard()
                    dialog.dismiss()
                })
            }
        }
        fill()

        val btns = LinearLayout(act)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER
        btns.addView(
            ui.pillButton(act.getString(R.string.sp_check_latency), Ic.GAUGE, true) {
                if (checking.isNotEmpty()) return@pillButton
                if (!Net.isOnline(act)) {
                    status.text = act.getString(R.string.sp_err_offline)
                    return@pillButton
                }
                for (s in servers) checking.add(s.id)
                status.text = act.getString(R.string.sp_status_checking)
                fill()
                val list = ArrayList(servers)
                Thread {
                    ServerManager.probeAll(list, true, { !open }) { s, _ ->
                        act.runOnUiThread {
                            if (open) {
                                checking.remove(s.id)
                                fill()
                            }
                        }
                    }
                    act.runOnUiThread {
                        checking.clear()
                        if (open) {
                            status.text = ""
                            fill()
                            refreshServerCard()
                        }
                    }
                }.start()
            },
            LinearLayout.LayoutParams(0, ui.dp(44), 1f)
        )
        btns.addView(
            ui.pillButton(act.getString(R.string.sp_update_list), Ic.REFRESH, false) {
                if (!Net.isOnline(act)) {
                    status.text = act.getString(R.string.sp_err_offline)
                    return@pillButton
                }
                status.text = act.getString(R.string.sp_updating)
                val app = act.applicationContext
                Thread {
                    var added = -1
                    try {
                        added = ServerManager.refreshRemote(app)
                    } catch (e: Exception) {
                        added = -1
                    }
                    act.runOnUiThread {
                        if (!open) return@runOnUiThread
                        if (added >= 0) {
                            servers = ServerManager.load(act)
                            status.text = act.getString(R.string.sp_list_updated, added)
                            fill()
                        } else {
                            status.text = act.getString(R.string.sp_list_update_fail)
                        }
                    }
                }.start()
            },
            ui.lp(0, ui.dp(44), 0, 0, 8, 0).also { it.weight = 1f }
        )
        box.addView(btns, ui.lp(ui.MATCH, ui.WRAP, 10))
        dialog.setOnDismissListener { open = false }
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun serverRow(title: String, sub: String, status: String, statusColor: Int?, selected: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        val bg = if (selected) ui.glass(14, true) else null
        ui.feel(row, bg, 14)
        val tb = LinearLayout(act)
        tb.orientation = LinearLayout.VERTICAL
        tb.addView(ui.tv(title, 14.5f, if (selected) ui.col(R.color.accent) else ui.col(R.color.text_primary), true))
        tb.addView(ui.tv(sub, 11.5f, ui.col(R.color.text_muted)))
        row.addView(tb, ui.lp(0, ui.WRAP, 0, 0, 0, 8).also { it.weight = 1f })
        if (status.isNotEmpty()) {
            row.addView(ui.tv(status, 12.5f, statusColor ?: ui.col(R.color.text_muted), true))
        }
        row.setOnClickListener { onClick() }
        val lp = ui.lp(ui.MATCH, ui.WRAP, 3)
        row.layoutParams = lp
        return row
    }
}
