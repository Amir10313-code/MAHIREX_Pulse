package com.mahirex.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Speed dial: a 270 degree arc with a non-linear scale (0-5-10-25-50-100-250-500-1000 Mbps) so slow and fast
 * links are both readable. It only redraws when a new value arrives (no endless animation).
 */
class SpeedGaugeView(ctx: Context) : View(ctx) {
    private val marks = doubleArrayOf(0.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0)
    private var shown = 0.0
    private var target = 0.0
    private var anim: ValueAnimator? = null
    var unit: String = "Mbps"
    var caption: String = ""
        set(v) {
            field = v
            invalidate()
        }
    private var colA = ContextCompat.getColor(ctx, R.color.accent3)
    private var colB = ContextCompat.getColor(ctx, R.color.accent)
    private val d = ctx.resources.displayMetrics.density
    private val sd = ctx.resources.displayMetrics.scaledDensity
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG)
    private val num = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cap = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    init {
        track.style = Paint.Style.STROKE
        track.strokeCap = Paint.Cap.ROUND
        track.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, R.color.text_muted), 55)
        arcP.style = Paint.Style.STROKE
        arcP.strokeCap = Paint.Cap.ROUND
        tick.style = Paint.Style.STROKE
        tick.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, R.color.text_muted), 120)
        num.color = ContextCompat.getColor(ctx, R.color.text_primary)
        num.textAlign = Paint.Align.CENTER
        num.typeface = Typeface.DEFAULT_BOLD
        sub.color = ContextCompat.getColor(ctx, R.color.text_muted)
        sub.textAlign = Paint.Align.CENTER
        cap.color = ContextCompat.getColor(ctx, R.color.accent)
        cap.textAlign = Paint.Align.CENTER
        cap.typeface = Typeface.DEFAULT_BOLD
        dot.color = 0xFFFFFFFF.toInt()
    }

    fun setColors(a: Int, b: Int) {
        colA = a
        colB = b
        invalidate()
    }

    private fun frac(v: Double): Float {
        if (v <= 0.0) return 0f
        for (i in 1 until marks.size) {
            if (v <= marks[i]) return ((i - 1) + (v - marks[i - 1]) / (marks[i] - marks[i - 1])).toFloat() / (marks.size - 1)
        }
        return 1f
    }

    /** animate smoothly to a new value (called a few times per second while testing) */
    fun setValue(v: Double, instant: Boolean = false) {
        target = v
        anim?.cancel()
        if (instant) {
            shown = v
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(shown.toFloat(), v.toFloat())
        a.duration = 220
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            shown = (it.animatedValue as Float).toDouble()
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        var w = MeasureSpec.getSize(wSpec)
        if (w <= 0) w = (260 * d).toInt()
        w = Math.min(w, (320 * d).toInt())
        setMeasuredDimension(w, (w * 0.86f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val sw = w * 0.075f
        val rad = w / 2f - sw
        val cx = w / 2f
        val cy = w / 2f
        oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
        track.strokeWidth = sw
        arcP.strokeWidth = sw
        c.drawArc(oval, 135f, 270f, false, track)

        // ticks
        tick.strokeWidth = 1.2f * d
        for (i in marks.indices) {
            val ang = Math.toRadians((135.0 + 270.0 * i / (marks.size - 1)))
            val r1 = rad - sw * 0.9f
            val r2 = rad - sw * 1.4f
            c.drawLine(
                cx + (r1 * Math.cos(ang)).toFloat(), cy + (r1 * Math.sin(ang)).toFloat(),
                cx + (r2 * Math.cos(ang)).toFloat(), cy + (r2 * Math.sin(ang)).toFloat(), tick
            )
        }

        val f = frac(shown)
        if (f > 0.004f) {
            val sweep = 270f * f
            arcP.shader = SweepGradient(cx, cy, intArrayOf(colB, colA, colB), floatArrayOf(0f, 0.5f, 1f))
            c.save()
            c.rotate(0f, cx, cy)
            c.drawArc(oval, 135f, sweep, false, arcP)
            c.restore()
            val ang = Math.toRadians(135.0 + sweep)
            dot.setShadowLayer(sw * 0.5f, 0f, 0f, colA)
            c.drawCircle(cx + (rad * Math.cos(ang)).toFloat(), cy + (rad * Math.sin(ang)).toFloat(), sw * 0.32f, dot)
        }

        val text = if (shown >= 100) String.format(java.util.Locale.US, "%.0f", shown)
        else if (shown >= 10) String.format(java.util.Locale.US, "%.1f", shown)
        else String.format(java.util.Locale.US, "%.2f", shown)
        num.textSize = w * 0.2f
        val fm = num.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f - w * 0.02f, num)
        sub.textSize = w * 0.058f
        c.drawText(unit, cx, cy + w * 0.17f, sub)
        if (caption.isNotEmpty()) {
            cap.textSize = w * 0.052f
            c.drawText(caption, cx, cy + rad * 0.98f + sw * 0.1f, cap)
        }
    }
}

/** Two-line history chart (download / upload) for the last tests, oldest on the left. */
class TrendView(ctx: Context) : View(ctx) {
    private var a: DoubleArray = DoubleArray(0)
    private var b: DoubleArray = DoubleArray(0)
    private val d = ctx.resources.displayMetrics.density
    private val la = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pa = Path()
    private val pf = Path()
    var colA: Int = ContextCompat.getColor(ctx, R.color.accent3)
    var colB: Int = ContextCompat.getColor(ctx, R.color.accent2)

    init {
        for (p in arrayOf(la, lb)) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.2f * d
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
        }
        fillA.style = Paint.Style.FILL
        grid.style = Paint.Style.STROKE
        grid.strokeWidth = 1f
        grid.color = ContextCompat.getColor(ctx, R.color.divider)
        txt.color = ContextCompat.getColor(ctx, R.color.text_muted)
        txt.textSize = 10f * ctx.resources.displayMetrics.scaledDensity
    }

    /** values oldest -> newest; upload entries < 0 are skipped */
    fun setData(down: DoubleArray, up: DoubleArray) {
        a = down
        b = up
        invalidate()
    }

    private fun drawLine(c: Canvas, v: DoubleArray, mx: Double, p: Paint, area: Boolean, w: Float, h: Float, pad: Float) {
        if (v.size < 2) return
        pa.reset()
        pf.reset()
        val step = (w - pad * 2) / (v.size - 1)
        var started = false
        var lastX = 0f
        for (i in v.indices) {
            if (v[i] < 0) continue
            val x = pad + i * step
            val y = h - pad - (v[i] / mx).toFloat() * (h - pad * 2)
            if (!started) {
                pa.moveTo(x, y)
                pf.moveTo(x, h - pad)
                pf.lineTo(x, y)
                started = true
            } else {
                pa.lineTo(x, y)
                pf.lineTo(x, y)
            }
            lastX = x
        }
        if (!started) return
        if (area) {
            pf.lineTo(lastX, h - pad)
            pf.close()
            fillA.shader = LinearGradient(0f, 0f, 0f, h, ColorUtils.setAlphaComponent(colA, 80), ColorUtils.setAlphaComponent(colA, 0), Shader.TileMode.CLAMP)
            c.drawPath(pf, fillA)
        }
        p.color = if (area) colA else colB
        c.drawPath(pa, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (k in 1..3) c.drawLine(0f, h * k / 4f, w, h * k / 4f, grid)
        var mx = 1.0
        for (x in a) if (x > mx) mx = x
        for (x in b) if (x > mx) mx = x
        mx *= 1.15
        val pad = 6f * d
        drawLine(c, a, mx, la, true, w, h, pad)
        drawLine(c, b, mx, lb, false, w, h, pad)
        txt.textAlign = Paint.Align.LEFT
        c.drawText(String.format(java.util.Locale.US, "%.0f", mx) + " Mbps", pad, txt.textSize + 2f * d, txt)
    }
}
