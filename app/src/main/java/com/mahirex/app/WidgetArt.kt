package com.mahirex.app

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils

/**
 * Canvas drawing of the widget graphics (rings, orb, gauge, sparkline, bar, glow).
 * Only graphics are drawn here; every number and label is a real TextView, so text stays crisp and RTL-correct.
 * All colours come from the widget palette ([HomeWidgets.Pal]); nothing is hard-coded.
 */
internal object WidgetArt {
    const val LVL_OK = 0
    const val LVL_WARN = 1
    const val LVL_BAD = 2

    fun partnerOf(pal: HomeWidgets.Pal, c: Int): Int = ColorUtils.blendARGB(c, pal.tint, 0.5f)

    fun accentAt(i: Int, dark: Boolean): Int {
        val a = if (dark) HomeWidgets.ACCENT_DARK else HomeWidgets.ACCENT_LIGHT
        return a[((i % a.size) + a.size) % a.size]
    }

    /** health colour of a usage / charge value: ok, warning, critical */
    fun levelOf(pct: Int, isBattery: Boolean, charging: Boolean): Int = when {
        pct < 0 -> LVL_OK
        isBattery -> if (charging) LVL_OK else if (pct <= 15) LVL_BAD else if (pct <= 30) LVL_WARN else LVL_OK
        else -> if (pct >= 90) LVL_BAD else if (pct >= 75) LVL_WARN else LVL_OK
    }

    /** gradient colours of a ring: [base] -> partner, amber / red when the level asks for attention, green end while charging */
    fun ringCols(pal: HomeWidgets.Pal, lvl: Int, charging: Boolean, base: Int = pal.accent): IntArray = when {
        lvl >= LVL_BAD -> intArrayOf(pal.badA, pal.badB)
        lvl == LVL_WARN -> intArrayOf(pal.warnA, pal.warnB)
        charging -> intArrayOf(base, pal.good)
        else -> intArrayOf(base, partnerOf(pal, base))
    }

    private fun stroke(s: Float, thick: Boolean, thin: Float, fat: Float) = s * (if (thick) fat else thin)

    /**
     * Round-capped arc with a gradient stroke and a soft glow, from 12 o'clock clockwise.
     * The glow is drawn first and the arc last with full opacity (a paint colour with a low alpha would also dim a shader).
     * The gradient is closed (c1 .. c2 .. c1) so the round caps beyond both ends keep the colours of their own end.
     */
    fun gradArc(c: Canvas, r: RectF, stroke: Float, frac: Float, cols: IntArray, track: Int, glowAlpha: Int, glowR: Float) {
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
        val sg = SweepGradient(cx, cy, intArrayOf(cols[0], cols[1], cols[0]), floatArrayOf(0f, if (f >= 0.995f) 0.5f else f, 1f))
        val m = Matrix()
        m.postRotate(-90f, cx, cy)
        sg.setLocalMatrix(m)
        val g = Paint(p)
        g.shader = sg
        g.maskFilter = BlurMaskFilter(glowR, BlurMaskFilter.Blur.NORMAL)
        g.alpha = glowAlpha
        c.drawArc(r, -90f, 360f * f, false, g)
        p.shader = sg
        p.alpha = 255
        c.drawArc(r, -90f, 360f * f, false, p)
    }

    /** Battery Orb ring (the numbers are TextViews on top) */
    fun orbBitmap(px: Int, pct: Int, charging: Boolean, pal: HomeWidgets.Pal, thick: Boolean): Bitmap {
        val s = px.toFloat()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val stroke = stroke(s, thick, 0.075f, 0.115f)
        val glowR = s * 0.035f
        val inset = stroke / 2f + glowR * 1.8f
        val r = RectF(inset, inset, s - inset, s - inset)
        val cols = ringCols(pal, levelOf(pct, true, charging), charging)
        val rad = r.width() / 2f - stroke / 2f
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = RadialGradient(
            s / 2f, s / 2f, rad,
            intArrayOf(ColorUtils.setAlphaComponent(cols[0], if (pal.dark) 70 else 40), ColorUtils.setAlphaComponent(cols[1], 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, rad, body)
        val guide = Paint(Paint.ANTI_ALIAS_FLAG)
        guide.style = Paint.Style.STROKE
        guide.strokeWidth = Math.max(1f, s * 0.006f)
        guide.color = pal.guide
        c.drawCircle(s / 2f, s / 2f, rad * 0.82f, guide)
        gradArc(c, r, stroke, if (pct > 0) pct.coerceAtMost(100) / 100f else 0f, cols, pal.track, if (pal.dark) 150 else 90, glowR)
        return b
    }

    /** Storage (outer) and RAM (inner) as concentric rings; a null value is not drawn (a single ring is drawn outer) */
    fun dualBitmap(px: Int, storagePct: Int?, ramPct: Int?, pal: HomeWidgets.Pal, accentIdx: Int, thick: Boolean): Bitmap {
        val s = px.toFloat()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val two = storagePct != null && ramPct != null
        val stroke = stroke(s, thick, if (two) 0.075f else 0.1f, if (two) 0.105f else 0.14f)
        val gap = s * 0.04f
        val glowR = s * 0.03f
        var inset = stroke / 2f + glowR * 1.8f
        val items = ArrayList<Pair<Int, IntArray>>()
        if (storagePct != null) items.add(Pair(storagePct, ringCols(pal, levelOf(storagePct, false, false), false)))
        if (ramPct != null) items.add(Pair(ramPct, ringCols(pal, levelOf(ramPct, false, false), false, accentAt(accentIdx + 2, pal.dark))))
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
            gradArc(c, r, stroke, if (item.first > 0) item.first.coerceAtMost(100) / 100f else 0f, item.second, pal.track, if (pal.dark) 140 else 80, glowR)
            inset += stroke + gap
        }
        return b
    }

    /** colour of the dot of a legend entry: the start colour of the matching ring */
    fun dualDot(pal: HomeWidgets.Pal, pct: Int, second: Boolean, accentIdx: Int): Int =
        ringCols(pal, levelOf(pct, false, false), false, if (second) accentAt(accentIdx + 2, pal.dark) else pal.accent)[0]

    /** one ring of the Phone health widget; a lightning bolt marks a charging battery */
    fun healthRing(px: Int, pct: Int, lvl: Int, charging: Boolean, pal: HomeWidgets.Pal, thick: Boolean, bolt: Boolean): Bitmap {
        val s = px.toFloat()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val stroke = stroke(s, thick, 0.085f, 0.135f)
        val glowR = s * 0.04f
        val inset = stroke / 2f + glowR * 1.8f
        val r = RectF(inset, inset, s - inset, s - inset)
        val cols = ringCols(pal, lvl, charging)
        val rad = r.width() / 2f - stroke / 2f
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        body.shader = RadialGradient(
            s / 2f, s / 2f, rad,
            intArrayOf(ColorUtils.setAlphaComponent(cols[0], if (pal.dark) 60 else 34), ColorUtils.setAlphaComponent(cols[1], 0)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, rad, body)
        gradArc(c, r, stroke, if (pct > 0) pct.coerceAtMost(100) / 100f else 0f, cols, pal.track, if (pal.dark) 140 else 80, glowR)
        if (bolt) {
            val h = s * 0.17f
            val w = h * 0.62f
            val x0 = s / 2f - w / 2f
            val y0 = s * 0.66f
            val path = Path()
            path.moveTo(x0 + w * 0.62f, y0)
            path.lineTo(x0, y0 + h * 0.58f)
            path.lineTo(x0 + w * 0.46f, y0 + h * 0.58f)
            path.lineTo(x0 + w * 0.36f, y0 + h)
            path.lineTo(x0 + w, y0 + h * 0.36f)
            path.lineTo(x0 + w * 0.54f, y0 + h * 0.36f)
            path.close()
            val bp = Paint(Paint.ANTI_ALIAS_FLAG)
            bp.color = cols[1]
            c.drawPath(path, bp)
        }
        return b
    }

    /** semicircle speedometer: gradient arc up to [frac], tick marks and a needle with a hub */
    fun gaugeBitmap(w: Int, h: Int, frac: Float, pal: HomeWidgets.Pal, thick: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val wf = w.toFloat()
        val stroke = stroke(wf, thick, 0.06f, 0.095f)
        val glowR = wf * 0.025f
        val cx = wf / 2f
        val cy = h * 0.72f
        val rad = Math.min(wf / 2f - stroke / 2f - glowR * 1.8f, cy - stroke / 2f - glowR * 1.8f)
        val r = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
        val f = frac.coerceIn(0f, 1f)
        val cols = ringCols(pal, LVL_OK, false)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.color = pal.track
        c.drawArc(r, 180f, 180f, false, p)
        if (f > 0f) {
            val ff = f.coerceAtLeast(0.02f)
            val sg = SweepGradient(cx, cy, intArrayOf(cols[0], cols[0], cols[1], cols[1]), floatArrayOf(0f, 0.5f, 0.5f + 0.5f * ff, 1f))
            val g = Paint(p)
            g.shader = sg
            g.maskFilter = BlurMaskFilter(glowR, BlurMaskFilter.Blur.NORMAL)
            g.alpha = if (pal.dark) 140 else 80
            c.drawArc(r, 180f, 180f * ff, false, g)
            p.shader = sg
            p.alpha = 255
            c.drawArc(r, 180f, 180f * ff, false, p)
        }
        // ticks inside the arc
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = pal.mu
        tp.strokeWidth = Math.max(1.5f, wf * 0.006f)
        tp.strokeCap = Paint.Cap.ROUND
        val tin = rad - stroke * 1.05f
        val tout = tin - wf * 0.03f
        for (i in 0..8) {
            val a = Math.toRadians(180.0 + 22.5 * i)
            val len = if (i % 2 == 0) tout - wf * 0.012f else tout
            c.drawLine((cx + tin * Math.cos(a)).toFloat(), (cy + tin * Math.sin(a)).toFloat(), (cx + len * Math.cos(a)).toFloat(), (cy + len * Math.sin(a)).toFloat(), tp)
        }
        // needle
        val ang = Math.toRadians(180.0 + 180.0 * f)
        val nl = tout - wf * 0.02f
        val bw = wf * 0.022f
        val nx = (cx + nl * Math.cos(ang)).toFloat()
        val ny = (cy + nl * Math.sin(ang)).toFloat()
        val px = (-Math.sin(ang) * bw).toFloat()
        val py = (Math.cos(ang) * bw).toFloat()
        val needle = Path()
        needle.moveTo(nx, ny)
        needle.lineTo(cx + px, cy + py)
        needle.lineTo(cx - px, cy - py)
        needle.close()
        val np = Paint(Paint.ANTI_ALIAS_FLAG)
        np.color = pal.tx
        c.drawPath(needle, np)
        np.color = cols[0]
        c.drawCircle(cx, cy, wf * 0.032f, np)
        np.color = pal.bg
        c.drawCircle(cx, cy, wf * 0.014f, np)
        return b
    }

    /** smooth sparkline of up to two series (newest on the right); each series is scaled to the common maximum */
    fun sparkBitmap(w: Int, h: Int, down: List<Float>, up: List<Float>, pal: HomeWidgets.Pal, thick: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val sw = Math.max(2f, h * (if (thick) 0.1f else 0.065f))
        val pad = sw * 1.6f
        var max = 0f
        for (v in down) max = Math.max(max, v)
        for (v in up) max = Math.max(max, v)
        if (max <= 0f) max = 1f
        // faint baseline
        val bl = Paint(Paint.ANTI_ALIAS_FLAG)
        bl.color = pal.guide
        bl.strokeWidth = Math.max(1f, h * 0.02f)
        c.drawLine(0f, h - pad, w.toFloat(), h - pad, bl)
        fun line(vals: List<Float>, c1: Int, c2: Int, fill: Boolean) {
            if (vals.size < 2) return
            val n = vals.size
            val xs = FloatArray(n) { pad + (w - 2 * pad) * it / (n - 1) }
            val ys = FloatArray(n) { h - pad - (h - 2 * pad) * (vals[it] / max).coerceIn(0f, 1f) }
            val path = Path()
            path.moveTo(xs[0], ys[0])
            for (i in 1 until n) {
                val mx = (xs[i - 1] + xs[i]) / 2f
                path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
            }
            if (fill) {
                val area = Path(path)
                area.lineTo(xs[n - 1], h.toFloat())
                area.lineTo(xs[0], h.toFloat())
                area.close()
                val fp = Paint(Paint.ANTI_ALIAS_FLAG)
                fp.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), ColorUtils.setAlphaComponent(c1, if (pal.dark) 90 else 60), ColorUtils.setAlphaComponent(c1, 0), Shader.TileMode.CLAMP)
                c.drawPath(area, fp)
            }
            val lp = Paint(Paint.ANTI_ALIAS_FLAG)
            lp.style = Paint.Style.STROKE
            lp.strokeWidth = sw
            lp.strokeCap = Paint.Cap.ROUND
            lp.strokeJoin = Paint.Join.ROUND
            lp.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, c1, c2, Shader.TileMode.CLAMP)
            val gl = Paint(lp)
            gl.maskFilter = BlurMaskFilter(sw * 1.6f, BlurMaskFilter.Blur.NORMAL)
            gl.alpha = if (pal.dark) 120 else 70
            c.drawPath(path, gl)
            c.drawPath(path, lp)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            dot.color = c2
            c.drawCircle(xs[n - 1], ys[n - 1], sw * 1.25f, dot)
        }
        line(up, pal.tint, pal.accent, false)
        line(down, pal.accent, partnerOf(pal, pal.accent), true)
        return b
    }

    /** thin horizontal progress / battery bar; [rtl] fills it from the right edge */
    fun barBitmap(w: Int, h: Int, pct: Int, pal: HomeWidgets.Pal, lvl: Int, rtl: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rad = h / 2f
        p.color = pal.track
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rad, rad, p)
        if (pct > 0) {
            val cols = ringCols(pal, lvl, false)
            val fw = Math.max(h.toFloat(), w * pct.coerceAtMost(100) / 100f)
            val left = if (rtl) w - fw else 0f
            p.shader = LinearGradient(left, 0f, left + fw, 0f, if (rtl) cols[1] else cols[0], if (rtl) cols[0] else cols[1], Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(left, 0f, left + fw, h.toFloat()), rad, rad, p)
        }
        return b
    }

    /** soft accent glow behind a pill button (a static stand-in for a pulse: widgets must not animate continuously) */
    fun glowBitmap(w: Int, h: Int, color: Int, pal: HomeWidgets.Pal): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val blur = Math.max(4f, h * 0.22f)
        val inset = blur * 1.2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = ColorUtils.setAlphaComponent(color, if (pal.dark) 120 else 80)
        p.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        val rr = RectF(inset, inset, w - inset, h - inset)
        c.drawRoundRect(rr, rr.height() / 2f, rr.height() / 2f, p)
        return b
    }
}
