package com.mahirex.app

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Dest { HOME, DETAILS, OTHER, OTHER_DETAILS, SETTINGS, ABOUT, LIVE, SPEED, TOOLS, HEALTH, STORAGE, CLEANER, FILES, RAM, BATTERY, DEVICE }

class Nav(val dest: Dest, val arg: Int)

class MainActivity : AppCompatActivity() {

    lateinit var ui: Ui
    var report: Report? = null
    var otherReport: Report? = null
    var loading = false
    var progI = 0
    var progN = 0
    var progName = ""
    var speedAutoStart = false

    private var screen: Screen? = null
    private val stack = ArrayList<Nav>()

    private lateinit var host: FrameLayout
    private lateinit var leftSlot: FrameLayout
    private lateinit var centerBox: LinearLayout
    private lateinit var drawerLayer: FrameLayout
    private lateinit var scrim: View
    private lateinit var panel: LinearLayout
    private val drawerItems = ArrayList<Pair<Dest, View>>()
    private var drawerOpen = false
    private var panelW = 0
    private var pendingExport: String? = null

    private val createTxt = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) writeTo(uri)
    }
    private val createJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) writeTo(uri)
    }
    private var treeCallback: ((Uri) -> Unit)? = null
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) treeCallback?.invoke(uri)
    }
    private val viewDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewUri(uri)
    }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importUri(uri)
    }

    // ------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        // layout direction follows the language that is really shown (see Ui.rtl)
        window.decorView.layoutDirection = ui.dir
        window.decorView.textDirection = if (ui.rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
        setContentView(buildRoot())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerOpen) closeDrawer()
                else if (stack.size > 1) back()
                else finish()
            }
        })

        otherReport = Cache.other
        val cached = Cache.report
        if (cached != null && Cache.lang == langTag()) {
            report = cached
        } else {
            startLoad()
        }

        val sd = savedInstanceState?.getIntArray("sd")
        val sa = savedInstanceState?.getIntArray("sa")
        if (sd != null && sa != null && sd.isNotEmpty() && sd.size == sa.size) {
            for (i in sd.indices) stack.add(Nav(Dest.values()[sd[i]], sa[i]))
            showTop()
        } else {
            go(Dest.HOME, 0, true)
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray("sd", IntArray(stack.size) { stack[it].dest.ordinal })
        outState.putIntArray("sa", IntArray(stack.size) { stack[it].arg })
    }

    override fun onResume() {
        super.onResume()
        screen?.onResume()
    }

    override fun onPause() {
        screen?.onPause()
        super.onPause()
    }

    private fun langTag(): String = resources.configuration.locales.get(0).toLanguageTag()

    fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------ root layout
    private fun buildRoot(): View {
        val root = FrameLayout(this)
        root.addView(AuroraView(this), FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.addView(buildTopBar(), LinearLayout.LayoutParams(ui.MATCH, ui.dp(60)))
        host = FrameLayout(this)
        column.addView(host, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))

        drawerLayer = buildDrawer()
        root.addView(drawerLayer, FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))
        return root
    }

    private fun buildTopBar(): View {
        val bar = FrameLayout(this)
        bar.layoutDirection = View.LAYOUT_DIRECTION_LTR   // hamburger always sits on the right
        bar.setPadding(ui.dp(10), 0, ui.dp(10), 0)

        leftSlot = FrameLayout(this)
        bar.addView(leftSlot, FrameLayout.LayoutParams(ui.dp(44), ui.dp(44), Gravity.START or Gravity.CENTER_VERTICAL))

        centerBox = LinearLayout(this)
        centerBox.orientation = LinearLayout.HORIZONTAL
        centerBox.gravity = Gravity.CENTER
        bar.addView(centerBox, FrameLayout.LayoutParams(ui.WRAP, ui.MATCH, Gravity.CENTER))

        val burger = iconButton(Ic.MENU) { toggleDrawer() }
        bar.addView(burger, FrameLayout.LayoutParams(ui.dp(44), ui.dp(44), Gravity.END or Gravity.CENTER_VERTICAL))
        return bar
    }

    private fun iconButton(type: Int, onClick: () -> Unit): View {
        val b = FrameLayout(this)
        ui.feel(b, ui.glass(16), 16)
        val ic = ui.icon(type, 24)
        b.addView(ic, FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun updateChrome() {
        val top = stack.lastOrNull() ?: return
        leftSlot.removeAllViews()
        centerBox.removeAllViews()
        when (top.dest) {
            Dest.HOME -> {
                centerBox.addView(LogoView(this), LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)))
                centerBox.addView(ui.wordmark(16f), ui.lp(ui.WRAP, ui.WRAP, 0, 0, 8, 0))
                leftSlot.addView(iconButton(Ic.REFRESH) { startLoad() }, FrameLayout.LayoutParams(ui.dp(44), ui.dp(44)))
            }
            else -> {
                val title = when (top.dest) {
                    Dest.DETAILS, Dest.OTHER_DETAILS -> getString(R.string.details_title)
                    Dest.OTHER -> getString(R.string.menu_other_phone)
                    Dest.SETTINGS -> getString(R.string.menu_settings)
                    Dest.ABOUT -> getString(R.string.menu_about)
                    Dest.LIVE -> getString(R.string.ft_live)
                    Dest.SPEED -> getString(R.string.sp_title)
                    Dest.TOOLS -> getString(R.string.menu_tools)
                    Dest.HEALTH -> getString(R.string.tool_health)
                    Dest.STORAGE -> getString(R.string.tool_storage)
                    Dest.CLEANER -> getString(R.string.tool_cleaner)
                    Dest.FILES -> getString(R.string.tool_files)
                    Dest.RAM -> getString(R.string.tool_ram)
                    Dest.BATTERY -> getString(R.string.tool_battery)
                    Dest.DEVICE -> getString(R.string.tool_device)
                    else -> ""
                }
                val t = ui.tv(title, 18f, ui.col(R.color.text_primary), true)
                t.maxLines = 1
                centerBox.addView(t)
                if (stack.size > 1) {
                    leftSlot.addView(iconButton(Ic.BACK) { back() }, FrameLayout.LayoutParams(ui.dp(44), ui.dp(44)))
                }
            }
        }
        // highlight the matching drawer entry
        val root = when (top.dest) {
            Dest.HOME, Dest.DETAILS, Dest.LIVE -> Dest.HOME
            Dest.OTHER, Dest.OTHER_DETAILS -> Dest.OTHER
            Dest.HEALTH, Dest.STORAGE, Dest.CLEANER, Dest.FILES, Dest.RAM, Dest.BATTERY, Dest.DEVICE -> Dest.TOOLS
            else -> top.dest
        }
        markDrawer(root)
    }

    /** selected drawer entry gets the glowing outline and an accent label */
    private fun markDrawer(root: Dest) {
        for (p in drawerItems) {
            val sel = p.first == root
            ui.feel(p.second, if (sel) ui.glass(16, true) else null, 16)
            val label = (p.second as LinearLayout).getChildAt(1) as TextView
            label.setTextColor(if (sel) ui.col(R.color.accent) else ui.col(R.color.text_primary))
        }
    }

    // ------------------------------------------------------------ navigation
    fun go(dest: Dest, arg: Int, root: Boolean) {
        if (root) stack.clear()
        stack.add(Nav(dest, arg))
        showTop()
    }

    private fun back() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            showTop()
        }
    }

    private fun showTop() {
        var top = stack.lastOrNull() ?: Nav(Dest.HOME, 0)
        // pages that need data fall back to home when the data is missing
        if ((top.dest == Dest.DETAILS && report == null) || (top.dest == Dest.OTHER_DETAILS && otherReport == null)) {
            stack.clear()
            top = Nav(Dest.HOME, 0)
            stack.add(top)
        }
        screen?.onPause()
        val s: Screen = when (top.dest) {
            Dest.HOME -> HomeScreen(this)
            Dest.DETAILS -> ReportScreen(this, report!!, top.arg)
            Dest.OTHER -> OtherScreen(this)
            Dest.OTHER_DETAILS -> ReportScreen(this, otherReport!!, top.arg)
            Dest.SETTINGS -> SettingsScreen(this)
            Dest.ABOUT -> AboutScreen(this)
            Dest.LIVE -> LiveScreen(this)
            Dest.SPEED -> SpeedScreen(this)
            Dest.TOOLS -> ToolsScreen(this)
            Dest.HEALTH -> HealthScreen(this)
            Dest.STORAGE -> StorageScreen(this)
            Dest.CLEANER -> CleanerScreen(this)
            Dest.FILES -> FilesScreen(this)
            Dest.RAM -> RamScreen(this)
            Dest.BATTERY -> BatteryScreen(this)
            Dest.DEVICE -> DeviceScreen(this)
        }
        screen = s
        host.removeAllViews()
        host.addView(s.build(), FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))
        updateChrome()
        closeDrawer(false)
        s.onResume()
    }

    // ------------------------------------------------------------ drawer (slides in from the right)
    private fun buildDrawer(): FrameLayout {
        val layer = FrameLayout(this)
        layer.visibility = View.GONE
        scrim = View(this)
        scrim.setBackgroundColor(0xAA000000.toInt())
        scrim.alpha = 0f
        scrim.setOnClickListener { closeDrawer() }
        layer.addView(scrim, FrameLayout.LayoutParams(ui.MATCH, ui.MATCH))

        panelW = Math.min(ui.dp(300), (resources.displayMetrics.widthPixels * 0.82f).toInt())
        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(ui.dp(16), ui.dp(22), ui.dp(16), ui.dp(16))
        val bg = GradientDrawable()
        bg.setColor(ui.col(R.color.card_solid))
        bg.cornerRadii = floatArrayOf(ui.dpf(28f), ui.dpf(28f), 0f, 0f, 0f, 0f, ui.dpf(28f), ui.dpf(28f))
        bg.setStroke(ui.dp(1), ui.alpha(ui.col(R.color.accent3), 110))
        panel.background = bg
        panel.isClickable = true
        layer.addView(panel, FrameLayout.LayoutParams(panelW, ui.MATCH, Gravity.RIGHT))

        // header
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.gravity = Gravity.CENTER_HORIZONTAL
        head.addView(LogoView(this), LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)))
        head.addView(ui.wordmark(21f), ui.lp(ui.WRAP, ui.WRAP, 8))
        val tag = ui.centered(ui.tv(getString(R.string.tagline), 11.5f, ui.col(R.color.text_muted)))
        head.addView(tag, ui.lp(ui.WRAP, ui.WRAP, 4))
        panel.addView(head, ui.lp(ui.MATCH, ui.WRAP, 0, 18))
        panel.addView(ui.divider(), ui.lp(ui.MATCH, 1, 0, 10))

        addDrawerItem(Ic.PHONE, R.string.menu_my_phone, Dest.HOME)
        addDrawerItem(Ic.GAUGE, R.string.sp_title, Dest.SPEED)
        addDrawerItem(Ic.HEART, R.string.menu_tools, Dest.TOOLS)
        addDrawerItem(Ic.IMPORT, R.string.menu_other_phone, Dest.OTHER)
        addDrawerItem(Ic.GEAR, R.string.menu_settings, Dest.SETTINGS)
        addDrawerItem(Ic.INFO, R.string.menu_about, Dest.ABOUT)

        val spacer = View(this)
        panel.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "2.0"
        } catch (e: Throwable) {
            "2.0"
        }
        panel.addView(ui.centered(ui.tv(getString(R.string.about_version, ver), 11.5f, ui.col(R.color.text_muted))))
        return layer
    }

    private fun addDrawerItem(icon: Int, label: Int, dest: Dest) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ui.dp(12), 0, ui.dp(12), 0)
        ui.feel(row, null, 16)
        val ic = ui.icon(icon, 24)
        row.addView(ic, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
        row.addView(ui.tv(getString(label), 16f, ui.col(R.color.text_primary), true), ui.lp(ui.WRAP, ui.WRAP, 0, 0, 14, 0))
        row.setOnClickListener {
            // show the chosen entry right away, slide the drawer away, then open the page
            markDrawer(dest)
            closeDrawer()
            row.postDelayed({ if (!isFinishing && !isDestroyed) go(dest, 0, true) }, 200)
        }
        panel.addView(row, ui.lp(ui.MATCH, ui.dp(50), 4))
        drawerItems.add(Pair(dest, row))
    }

    private fun toggleDrawer() {
        if (drawerOpen) closeDrawer() else openDrawer()
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawerLayer.visibility = View.VISIBLE
        panel.translationX = panelW.toFloat()
        panel.animate().translationX(0f).setDuration(240).start()
        scrim.animate().alpha(1f).setDuration(240).start()
    }

    private fun closeDrawer(animate: Boolean = true) {
        if (!drawerOpen) return
        drawerOpen = false
        if (!animate) {
            drawerLayer.visibility = View.GONE
            return
        }
        panel.animate().translationX(panelW.toFloat()).setDuration(200).start()
        scrim.animate().alpha(0f).setDuration(200).withEndAction {
            if (!drawerOpen) drawerLayer.visibility = View.GONE
        }.start()
    }

    // ------------------------------------------------------------ data
    fun startLoad() {
        if (loading) return
        loading = true
        progI = 0
        progN = 0
        progName = ""
        screen?.onData()
        val appCtx = applicationContext
        val lctx: Context = this
        val tag = langTag()
        Thread {
            val result: Report? = try {
                Collector(lctx, appCtx).collect { i, n, name ->
                    runOnUiThread {
                        progI = i
                        progN = n
                        progName = name
                        screen?.onProgress(i, n, name)
                    }
                }
            } catch (e: Throwable) {
                null
            }
            runOnUiThread {
                loading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result != null) {
                    report = result
                    Cache.report = result
                    Cache.lang = tag
                }
                screen?.onData()
            }
        }.start()
    }

    // ------------------------------------------------------------ export
    private fun fileBase(): String {
        val model = (report?.str("model") ?: "Phone").replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "MAHIREX_" + model + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    fun showExport() {
        val r = report
        if (r == null) {
            toast(R.string.no_data)
            return
        }
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18))
        box.background = ui.solidGlass(26)
        box.layoutDirection = ui.dir
        box.addView(ui.tv(getString(R.string.btn_export), 18f, ui.col(R.color.text_primary), true))
        val note = ui.tv(getString(R.string.export_privacy), 12.5f, ui.col(R.color.warn))
        box.addView(note, ui.lp(ui.MATCH, ui.WRAP, 8, 12))
        val dialog = AlertDialog.Builder(this).setView(box).create()
        box.addView(
            ui.pillButton(getString(R.string.export_share), Ic.SHARE, true) { dialog.dismiss(); shareJson(r) },
            ui.lp(ui.MATCH, ui.dp(48), 4)
        )
        box.addView(
            ui.pillButton(getString(R.string.export_json), Ic.DOWNLOAD, false) {
                dialog.dismiss()
                pendingExport = ReportIO.toJson(r)
                createJson.launch(fileBase() + ".json")
            },
            ui.lp(ui.MATCH, ui.dp(48), 10)
        )
        box.addView(
            ui.pillButton(getString(R.string.export_txt), Ic.LIST, false) {
                dialog.dismiss()
                pendingExport = ReportIO.toText(this, r)
                createTxt.launch(fileBase() + ".txt")
            },
            ui.lp(ui.MATCH, ui.dp(48), 10)
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun writeTo(uri: Uri) {
        val text = pendingExport
        if (text == null) {
            toast(R.string.saved_fail)
            return
        }
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            toast(R.string.saved_ok)
        } catch (e: Exception) {
            toast(R.string.saved_fail)
        }
        pendingExport = null
    }

    private fun shareJson(r: Report) {
        try {
            val dir = File(cacheDir, "share")
            dir.mkdirs()
            val f = File(dir, fileBase() + ".json")
            f.writeText(ReportIO.toJson(r), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.clipData = ClipData.newRawUri("", uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, getString(R.string.share_chooser)))
        } catch (e: Exception) {
            toast(R.string.saved_fail)
        }
    }

    // ------------------------------------------------------------ import (other phones)
    fun openReportPicker() {
        openDoc.launch(arrayOf("*/*"))
    }

    fun closeOther() {
        otherReport = null
        Cache.other = null
        go(Dest.OTHER, 0, true)
    }

    private fun importUri(uri: Uri) {
        val text: String? = try {
            contentResolver.openInputStream(uri)?.use { ins ->
                val buf = ins.readBytes()
                if (buf.size > 8_000_000) null else String(buf, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
        val r = if (text == null) null else ReportIO.parse(text)
        if (r == null) {
            toast(R.string.other_bad_file)
            return
        }
        otherReport = r
        Cache.other = r
        go(Dest.OTHER, 0, true)
    }

    fun pickFolder(cb: (Uri) -> Unit) {
        treeCallback = cb
        openTree.launch(null)
    }

    fun pickAndOpenFile() {
        viewDoc.launch(arrayOf("*/*"))
    }

    private fun viewUri(uri: Uri) {
        try {
            val v = Intent(Intent.ACTION_VIEW)
            v.setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(v)
        } catch (e: Exception) {
            toast(R.string.t_unavailable)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == HomeWidgets.ACTION_OPEN) {
            // launched from a home-screen widget
            val dest = when (i.getStringExtra(HomeWidgets.EXTRA_DEST)) {
                "speed" -> Dest.SPEED
                "health" -> Dest.HEALTH
                "cleaner" -> Dest.CLEANER
                "storage" -> Dest.STORAGE
                "battery" -> Dest.BATTERY
                "ram" -> Dest.RAM
                "device" -> Dest.DEVICE
                else -> Dest.HOME
            }
            speedAutoStart = dest == Dest.SPEED && i.getBooleanExtra("auto", false)
            i.action = null
            go(dest, 0, true)
            return
        }
        var uri: Uri? = null
        if (i.action == Intent.ACTION_VIEW) {
            uri = i.data
        } else if (i.action == Intent.ACTION_SEND) {
            uri = i.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) importUri(uri)
    }
}
