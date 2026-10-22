package com.mahirex.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser

// ====================================================================== glass card background
class GlassDrawable(
    private val radius: Float,
    fill: Int,
    private val strokeA: Int,
    private val strokeB: Int,
    private val strokeW: Float
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = fill
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = strokeW
    }

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        val h = strokeW / 2f
        rect.set(b.left + h, b.top + h, b.right - h, b.bottom - h)
        strokePaint.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            strokeA, strokeB, Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        c.drawRoundRect(rect, radius, radius, fillPaint)
        c.drawRoundRect(rect, radius, radius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// ====================================================================== animated neon background
class AuroraView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = ContextCompat.getColor(ctx, R.color.bg)
    private val g1 = ContextCompat.getColor(ctx, R.color.glow1)
    private val g2 = ContextCompat.getColor(ctx, R.color.glow2)
    private val g3 = ContextCompat.getColor(ctx, R.color.glow3)
    private val dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    private var visibleNow = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        visibleNow = visibility == View.VISIBLE
        if (visibleNow) postInvalidateOnAnimation()
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Int) {
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(ColorUtils.setAlphaComponent(color, a), ColorUtils.setAlphaComponent(color, 0)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(bg)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (SystemClock.uptimeMillis() % 36000L) / 36000f
        val a = (t * 2.0 * Math.PI).toFloat()
        val s = Math.sin(a.toDouble()).toFloat()
        val k = Math.cos(a.toDouble()).toFloat()
        val strong = if (dark) 95 else 85
        blob(c, w * (0.12f + 0.10f * s), h * (0.08f + 0.04f * k), w * 0.95f, g1, strong)
        blob(c, w * (0.98f - 0.10f * k), h * (0.50f + 0.06f * s), w * 0.85f, g2, strong - 15)
        blob(c, w * (0.25f + 0.10f * k), h * (0.98f - 0.04f * s), w * 0.95f, g3, strong - 35)
        if (visibleNow) postInvalidateDelayed(48)
    }
}

// ====================================================================== gradient text
class GradText(ctx: Context) : AppCompatTextView(ctx) {
    var colorA: Int = 0xFF52C0FF.toInt()
    var colorB: Int = 0xFFC04DFF.toInt()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colorA, colorB, Shader.TileMode.CLAMP)
    }
}

// ====================================================================== icons (line style, drawn in a 24x24 space)
object Ic {
    const val PHONE = 0
    const val CHIP = 1
    const val WIFI = 2
    const val SENSOR = 3
    const val GEAR = 4
    const val GAUGE = 5
    const val BATTERY = 6
    const val STORAGE = 7
    const val CAMERA = 8
    const val DISPLAY = 9
    const val SHIELD = 10
    const val SIM = 11
    const val INFO = 12
    const val MENU = 13
    const val GLOBE = 14
    const val HALF = 15
    const val SUN = 16
    const val MOON = 17
    const val SHARE = 18
    const val DOWNLOAD = 19
    const val IMPORT = 20
    const val REFRESH = 21
    const val CHEV_R = 22
    const val CHEV_L = 23
    const val BACK = 24
    const val CHECK = 25
    const val CLOSE = 26
    const val MEMORY = 27
    const val GPU = 28
    const val LIST = 29
    const val LAYERS = 30
    const val HEART = 31
    const val TRASH = 32
    const val FOLDER = 33
    const val PLAY = 34
    const val BOLT = 35
    const val ARROW_DOWN = 36
    const val ARROW_UP = 37

    private val r = RectF()
    private val path = Path()
    private val moonPath: Path by lazy {
        PathParser.createPathFromPathData(
            "M12,3c-4.97,0-9,4.03-9,9s4.03,9,9,9s9-4.03,9-9c0-0.46-0.04-0.92-0.1-1.36c-0.98,1.37-2.58,2.26-4.4,2.26c-2.98,0-5.4-2.42-5.4-5.4c0-1.81,0.89-3.42,2.26-4.4C12.92,3.04,12.46,3,12,3z"
        ) ?: Path()
    }

    fun forSection(id: String): Int = when (id) {
        "summary" -> INFO
        "device" -> PHONE
        "system" -> LAYERS
        "cpu" -> CHIP
        "gpu" -> GPU
        "ram" -> MEMORY
        "storage" -> STORAGE
        "display" -> DISPLAY
        "battery" -> BATTERY
        "network" -> WIFI
        "sim" -> SIM
        "camera" -> CAMERA
        "sensors" -> SENSOR
        "features" -> LIST
        "security" -> SHIELD
        else -> INFO
    }

    fun colorFor(id: String): Int = when (id) {
        "summary" -> 0xFF4DA3FF.toInt()
        "device" -> 0xFF4DA3FF.toInt()
        "system" -> 0xFFA56BFF.toInt()
        "cpu" -> 0xFF2EE6A0.toInt()
        "gpu" -> 0xFFFF9F43.toInt()
        "ram" -> 0xFF2EE6E0.toInt()
        "storage" -> 0xFFFFB547.toInt()
        "display" -> 0xFFFF6BB5.toInt()
        "battery" -> 0xFF27D98B.toInt()
        "network" -> 0xFF4DA3FF.toInt()
        "sim" -> 0xFFB58CFF.toInt()
        "camera" -> 0xFFFF6B8A.toInt()
        "sensors" -> 0xFF9B7BFF.toInt()
        "features" -> 0xFF2EC8E6.toInt()
        "security" -> 0xFF5CE08A.toInt()
        else -> 0xFF4DA3FF.toInt()
    }

    private fun rr(c: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float, p: Paint) {
        r.set(l, t, rt, b)
        c.drawRoundRect(r, rad, rad, p)
    }

    private fun ln(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, p: Paint) {
        c.drawLine(x1, y1, x2, y2, p)
    }

    private fun poly(c: Canvas, p: Paint, close: Boolean, vararg v: Float) {
        path.reset()
        path.moveTo(v[0], v[1])
        var i = 2
        while (i + 1 < v.size) {
            path.lineTo(v[i], v[i + 1])
            i += 2
        }
        if (close) path.close()
        c.drawPath(path, p)
    }

    private fun arc(c: Canvas, cx: Float, cy: Float, rad: Float, start: Float, sweep: Float, p: Paint) {
        r.set(cx - rad, cy - rad, cx + rad, cy + rad)
        c.drawArc(r, start, sweep, false, p)
    }

    /** p = stroke paint, f = fill paint (same gradient) */
    fun draw(c: Canvas, type: Int, p: Paint, f: Paint) {
        when (type) {
            PHONE -> {
                rr(c, 6.5f, 2f, 17.5f, 22f, 2.6f, p)
                ln(c, 10.5f, 18.6f, 13.5f, 18.6f, p)
                c.drawCircle(12f, 5.2f, 0.7f, f)
            }
            CHIP -> {
                rr(c, 7f, 7f, 17f, 17f, 2f, p)
                rr(c, 10.2f, 10.2f, 13.8f, 13.8f, 0.6f, p)
                val ps = floatArrayOf(9.5f, 12f, 14.5f)
                for (x in ps) {
                    ln(c, x, 3.5f, x, 7f, p)
                    ln(c, x, 17f, x, 20.5f, p)
                    ln(c, 3.5f, x, 7f, x, p)
                    ln(c, 17f, x, 20.5f, x, p)
                }
            }
            WIFI -> {
                arc(c, 12f, 19.2f, 4.4f, -135f, 90f, p)
                arc(c, 12f, 19.2f, 8.6f, -135f, 90f, p)
                arc(c, 12f, 19.2f, 12.8f, -135f, 90f, p)
                c.drawCircle(12f, 19.2f, 1.25f, f)
            }
            SENSOR -> {
                c.drawCircle(12f, 12f, 1.9f, f)
                c.drawCircle(12f, 12f, 5.3f, p)
                arc(c, 12f, 12f, 9.2f, -42f, 84f, p)
                arc(c, 12f, 12f, 9.2f, 138f, 84f, p)
            }
            GEAR -> {
                c.drawCircle(12f, 12f, 3.1f, p)
                c.drawCircle(12f, 12f, 6.6f, p)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val ca = Math.cos(a).toFloat()
                    val sa = Math.sin(a).toFloat()
                    ln(c, 12f + 6.6f * ca, 12f + 6.6f * sa, 12f + 9.8f * ca, 12f + 9.8f * sa, p)
                }
            }
            GAUGE -> {
                c.drawCircle(12f, 12f, 9.2f, p)
                ln(c, 12f, 12f, 16.2f, 8.2f, p)
                c.drawCircle(12f, 12f, 1.3f, f)
                ln(c, 12f, 4.8f, 12f, 6.6f, p)
                ln(c, 19.2f, 12f, 17.4f, 12f, p)
                ln(c, 4.8f, 12f, 6.6f, 12f, p)
            }
            BATTERY -> {
                rr(c, 2.5f, 7f, 19.5f, 17f, 2.4f, p)
                ln(c, 21.5f, 10.6f, 21.5f, 13.4f, p)
                rr(c, 5f, 9.4f, 13.5f, 14.6f, 1f, f)
            }
            STORAGE -> {
                rr(c, 4f, 4f, 20f, 10f, 2.2f, p)
                rr(c, 4f, 14f, 20f, 20f, 2.2f, p)
                c.drawCircle(7.6f, 7f, 0.95f, f)
                c.drawCircle(7.6f, 17f, 0.95f, f)
            }
            CAMERA -> {
                rr(c, 2.5f, 7f, 21.5f, 19.5f, 3f, p)
                c.drawCircle(12f, 13.2f, 3.6f, p)
                ln(c, 9f, 4.8f, 15f, 4.8f, p)
                c.drawCircle(18.2f, 10f, 0.8f, f)
            }
            DISPLAY -> {
                rr(c, 3f, 4f, 21f, 16f, 2.2f, p)
                ln(c, 8.5f, 20f, 15.5f, 20f, p)
                ln(c, 12f, 16f, 12f, 20f, p)
            }
            SHIELD -> {
                path.reset()
                path.moveTo(12f, 2.8f)
                path.lineTo(19.5f, 5.8f)
                path.lineTo(19.5f, 11.4f)
                path.cubicTo(19.5f, 16.1f, 16.4f, 19.6f, 12f, 21.2f)
                path.cubicTo(7.6f, 19.6f, 4.5f, 16.1f, 4.5f, 11.4f)
                path.lineTo(4.5f, 5.8f)
                path.close()
                c.drawPath(path, p)
                poly(c, p, false, 8.6f, 11.9f, 11f, 14.3f, 15.6f, 9.4f)
            }
            SIM -> {
                path.reset()
                path.moveTo(7f, 3f)
                path.lineTo(14.2f, 3f)
                path.lineTo(18.5f, 7.3f)
                path.lineTo(18.5f, 19.5f)
                path.quadTo(18.5f, 21f, 17f, 21f)
                path.lineTo(7f, 21f)
                path.quadTo(5.5f, 21f, 5.5f, 19.5f)
                path.lineTo(5.5f, 4.5f)
                path.quadTo(5.5f, 3f, 7f, 3f)
                path.close()
                c.drawPath(path, p)
                rr(c, 8.6f, 11f, 15.4f, 17.2f, 1.2f, p)
                ln(c, 12f, 11f, 12f, 17.2f, p)
                ln(c, 8.6f, 14f, 15.4f, 14f, p)
            }
            INFO -> {
                c.drawCircle(12f, 12f, 9.4f, p)
                ln(c, 12f, 11f, 12f, 16.8f, p)
                c.drawCircle(12f, 7.9f, 1.1f, f)
            }
            MENU -> {
                ln(c, 4f, 6.5f, 20f, 6.5f, p)
                ln(c, 4f, 12f, 20f, 12f, p)
                ln(c, 4f, 17.5f, 13.5f, 17.5f, p)
            }
            GLOBE -> {
                c.drawCircle(12f, 12f, 9.2f, p)
                r.set(7.6f, 2.8f, 16.4f, 21.2f)
                c.drawOval(r, p)
                ln(c, 2.8f, 12f, 21.2f, 12f, p)
                ln(c, 4.4f, 7.4f, 19.6f, 7.4f, p)
                ln(c, 4.4f, 16.6f, 19.6f, 16.6f, p)
            }
            HALF -> {
                c.drawCircle(12f, 12f, 9f, p)
                r.set(3f, 3f, 21f, 21f)
                c.drawArc(r, 90f, 180f, true, f)
            }
            SUN -> {
                c.drawCircle(12f, 12f, 4f, p)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val ca = Math.cos(a).toFloat()
                    val sa = Math.sin(a).toFloat()
                    ln(c, 12f + 6.8f * ca, 12f + 6.8f * sa, 12f + 9.3f * ca, 12f + 9.3f * sa, p)
                }
            }
            MOON -> c.drawPath(moonPath, p)
            SHARE -> {
                c.drawCircle(18f, 5.5f, 2.4f, p)
                c.drawCircle(6f, 12f, 2.4f, p)
                c.drawCircle(18f, 18.5f, 2.4f, p)
                ln(c, 8.1f, 10.8f, 15.9f, 6.7f, p)
                ln(c, 8.1f, 13.2f, 15.9f, 17.3f, p)
            }
            DOWNLOAD -> {
                ln(c, 12f, 3.5f, 12f, 15f, p)
                poly(c, p, false, 7.6f, 10.8f, 12f, 15.2f, 16.4f, 10.8f)
                poly(c, p, false, 4.5f, 15.5f, 4.5f, 19.5f, 19.5f, 19.5f, 19.5f, 15.5f)
            }
            IMPORT -> {
                rr(c, 6.5f, 2f, 17.5f, 22f, 2.6f, p)
                ln(c, 12f, 6.6f, 12f, 13.8f, p)
                poly(c, p, false, 9.4f, 11.2f, 12f, 13.8f, 14.6f, 11.2f)
                ln(c, 9.5f, 17.4f, 14.5f, 17.4f, p)
            }
            REFRESH -> {
                arc(c, 12f, 12f, 8f, 20f, 300f, p)
                poly(c, p, false, 18.9f, 2.8f, 18.1f, 6.9f, 14f, 6.2f)
            }
            CHEV_R -> poly(c, p, false, 9f, 5.5f, 15.5f, 12f, 9f, 18.5f)
            CHEV_L -> poly(c, p, false, 15f, 5.5f, 8.5f, 12f, 15f, 18.5f)
            BACK -> {
                ln(c, 20f, 12f, 5f, 12f, p)
                poly(c, p, false, 11f, 6f, 5f, 12f, 11f, 18f)
            }
            CHECK -> poly(c, p, false, 5f, 12.6f, 10f, 17.4f, 19f, 7f)
            CLOSE -> {
                ln(c, 6f, 6f, 18f, 18f, p)
                ln(c, 18f, 6f, 6f, 18f, p)
            }
            MEMORY -> {
                rr(c, 2.5f, 6.5f, 21.5f, 16.5f, 1.8f, p)
                ln(c, 7f, 10f, 7f, 13f, p)
                ln(c, 12f, 10f, 12f, 13f, p)
                ln(c, 17f, 10f, 17f, 13f, p)
                ln(c, 6f, 16.5f, 6f, 19.5f, p)
                ln(c, 10f, 16.5f, 10f, 19.5f, p)
                ln(c, 14f, 16.5f, 14f, 19.5f, p)
                ln(c, 18f, 16.5f, 18f, 19.5f, p)
            }
            GPU -> {
                rr(c, 2.5f, 5.5f, 21.5f, 16.5f, 2f, p)
                c.drawCircle(12f, 11f, 3.4f, p)
                c.drawCircle(12f, 11f, 1f, f)
                ln(c, 6f, 16.5f, 6f, 19.5f, p)
                ln(c, 18f, 16.5f, 18f, 19.5f, p)
            }
            LIST -> {
                c.drawCircle(5f, 7f, 1f, f)
                c.drawCircle(5f, 12f, 1f, f)
                c.drawCircle(5f, 17f, 1f, f)
                ln(c, 9f, 7f, 20f, 7f, p)
                ln(c, 9f, 12f, 20f, 12f, p)
                ln(c, 9f, 17f, 20f, 17f, p)
            }
            LAYERS -> {
                poly(c, p, true, 12f, 3.5f, 20.5f, 8f, 12f, 12.5f, 3.5f, 8f)
                poly(c, p, false, 3.5f, 12f, 12f, 16.5f, 20.5f, 12f)
                poly(c, p, false, 3.5f, 16f, 12f, 20.5f, 20.5f, 16f)
            }
            HEART -> {
                path.reset()
                path.moveTo(12f, 20f)
                path.cubicTo(5f, 15.2f, 2.8f, 11f, 2.8f, 8.2f)
                path.cubicTo(2.8f, 5.6f, 4.8f, 4f, 7.2f, 4f)
                path.cubicTo(9.2f, 4f, 11f, 5.2f, 12f, 7f)
                path.cubicTo(13f, 5.2f, 14.8f, 4f, 16.8f, 4f)
                path.cubicTo(19.2f, 4f, 21.2f, 5.6f, 21.2f, 8.2f)
                path.cubicTo(21.2f, 11f, 19f, 15.2f, 12f, 20f)
                path.close()
                c.drawPath(path, p)
                ln(c, 12f, 9f, 12f, 14f, p)
                ln(c, 9.5f, 11.5f, 14.5f, 11.5f, p)
            }
            TRASH -> {
                rr(c, 6f, 7.5f, 18f, 21f, 2f, p)
                ln(c, 4f, 7f, 20f, 7f, p)
                poly(c, p, false, 9.5f, 7f, 9.5f, 4f, 14.5f, 4f, 14.5f, 7f)
                ln(c, 10f, 11f, 10f, 17f, p)
                ln(c, 14f, 11f, 14f, 17f, p)
            }
            FOLDER -> {
                poly(c, p, true, 3f, 6.5f, 9f, 6.5f, 11f, 9f, 21f, 9f, 21f, 19.5f, 3f, 19.5f)
            }
            PLAY -> {
                poly(c, p, true, 8f, 5f, 19f, 12f, 8f, 19f)
            }
            BOLT -> {
                poly(c, p, true, 13.5f, 2.5f, 5.5f, 13.5f, 11.5f, 13.5f, 10.5f, 21.5f, 18.5f, 10.5f, 12.5f, 10.5f)
            }
            ARROW_DOWN -> {
                ln(c, 12f, 4f, 12f, 19f, p)
                poly(c, p, false, 6f, 13f, 12f, 19f, 18f, 13f)
            }
            ARROW_UP -> {
                ln(c, 12f, 20f, 12f, 5f, p)
                poly(c, p, false, 6f, 11f, 12f, 5f, 18f, 11f)
            }
        }
    }
}

class IconView(ctx: Context) : View(ctx) {
    var type: Int = Ic.INFO
        set(v) {
            field = v
            invalidate()
        }
    var glow: Boolean = true
    private var colA: Int = ContextCompat.getColor(ctx, R.color.accent)
    private var colB: Int = ContextCompat.getColor(ctx, R.color.accent2)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeWidth = 1.7f
        glowP.style = Paint.Style.STROKE
        glowP.strokeCap = Paint.Cap.ROUND
        glowP.strokeJoin = Paint.Join.ROUND
        glowP.strokeWidth = 4.2f
        glowP.alpha = 55
        fill.style = Paint.Style.FILL
        applyShader()
    }

    private fun applyShader() {
        val sh = LinearGradient(2f, 2f, 22f, 22f, colA, colB, Shader.TileMode.CLAMP)
        stroke.shader = sh
        glowP.shader = sh
        fill.shader = sh
    }

    fun setColors(a: Int, b: Int) {
        colA = a
        colB = b
        applyShader()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = Math.min(width, height) / 24f
        if (s <= 0f) return
        c.save()
        c.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        c.scale(s, s)
        if (glow) Ic.draw(c, type, glowP, fill)
        Ic.draw(c, type, stroke, fill)
        c.restore()
    }
}

// ====================================================================== MAHIREX logo (vector, drawn in code)
class LogoView(ctx: Context) : View(ctx) {
    private val frame = floatArrayOf(
        8.1f, 8.1f, 47.8f, 42.6f, 90.4f, 8.1f, 90.4f, 64f, 76.5f, 80.6f, 76.0f, 48.5f, 82.1f, 39.4f,
        82.1f, 28.7f, 47.8f, 59.6f, 15.9f, 29.0f, 15.9f, 40.4f, 24.3f, 47.8f, 25.0f, 80.6f, 8.1f, 64f
    )
    private val piece = floatArrayOf(48.8f, 73.1f, 74.0f, 48.2f, 74.0f, 64.7f, 48.8f, 91.5f)
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pa = Path()
    private val pb = Path()

    init {
        val sh = LinearGradient(
            8f, 8f, 92f, 92f,
            intArrayOf(0xFF52C0FF.toInt(), 0xFF7C7BFF.toInt(), 0xFFC04DFF.toInt()), null, Shader.TileMode.CLAMP
        )
        fillP.style = Paint.Style.FILL
        fillP.shader = sh
        glowP.style = Paint.Style.STROKE
        glowP.strokeWidth = 5f
        glowP.strokeJoin = Paint.Join.ROUND
        glowP.shader = sh
        glowP.alpha = 70
        edgeP.style = Paint.Style.STROKE
        edgeP.strokeWidth = 0.9f
        edgeP.color = 0x99FFFFFF.toInt()
        build(pa, frame)
        build(pb, piece)
    }

    private fun build(p: Path, v: FloatArray) {
        p.reset()
        p.moveTo(v[0], v[1])
        var i = 2
        while (i + 1 < v.size) {
            p.lineTo(v[i], v[i + 1])
            i += 2
        }
        p.close()
    }

    override fun onDraw(c: Canvas) {
        val s = Math.min(width, height) / 100f
        if (s <= 0f) return
        c.save()
        c.translate((width - 100f * s) / 2f, (height - 100f * s) / 2f)
        c.scale(s, s)
        c.drawPath(pa, glowP)
        c.drawPath(pb, glowP)
        c.drawPath(pa, fillP)
        c.drawPath(pb, fillP)
        c.drawPath(pa, edgeP)
        c.drawPath(pb, edgeP)
        c.restore()
    }
}

// ====================================================================== scanning ring (home hero)
class ScanRingView(ctx: Context) : View(ctx) {
    var loading: Boolean = true
        set(v) {
            field = v
            invalidate()
        }
    private var rot = 0f
    private var anim: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icS = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icF = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icG = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val cA = ContextCompat.getColor(ctx, R.color.accent3)
    private val cB = ContextCompat.getColor(ctx, R.color.accent)
    private val cC = ContextCompat.getColor(ctx, R.color.accent2)

    init {
        track.style = Paint.Style.STROKE
        track.color = ColorUtils.setAlphaComponent(cB, 40)
        arcP.style = Paint.Style.STROKE
        arcP.strokeCap = Paint.Cap.ROUND
        glow.style = Paint.Style.FILL
        icS.style = Paint.Style.STROKE
        icS.strokeCap = Paint.Cap.ROUND
        icS.strokeJoin = Paint.Join.ROUND
        icS.strokeWidth = 1.5f
        icG.style = Paint.Style.STROKE
        icG.strokeCap = Paint.Cap.ROUND
        icG.strokeJoin = Paint.Join.ROUND
        icG.strokeWidth = 3.6f
        icG.alpha = 60
        icF.style = Paint.Style.FILL
        val sh = LinearGradient(4f, 2f, 20f, 22f, cA, cC, Shader.TileMode.CLAMP)
        icS.shader = sh
        icG.shader = sh
        icF.shader = sh
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val a = ValueAnimator.ofFloat(0f, 360f)
        a.duration = 2600
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener {
            rot = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val cx = w / 2f
        val cy = h / 2f
        arcP.shader = SweepGradient(cx, cy, intArrayOf(cA, cB, cC, cA), null)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val sw = w * 0.045f
        val rad = Math.min(w, h) / 2f - sw * 1.6f
        track.strokeWidth = sw
        arcP.strokeWidth = sw
        glow.shader = RadialGradient(
            cx, cy, rad * 1.35f,
            intArrayOf(ColorUtils.setAlphaComponent(cA, 70), ColorUtils.setAlphaComponent(cC, 0)),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, rad * 1.35f, glow)
        c.drawCircle(cx, cy, rad, track)
        oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
        c.save()
        c.rotate(rot, cx, cy)
        if (loading) c.drawArc(oval, 0f, 110f, false, arcP) else c.drawArc(oval, 0f, 360f, false, arcP)
        c.restore()
        // phone icon in the middle
        val isz = rad * 1.05f
        c.save()
        c.translate(cx - isz / 2f, cy - isz / 2f)
        c.scale(isz / 24f, isz / 24f)
        Ic.draw(c, Ic.PHONE, icG, icF)
        Ic.draw(c, Ic.PHONE, icS, icF)
        c.restore()
    }
}

// ====================================================================== circular gauge with label
class GaugeView(ctx: Context) : View(ctx) {
    private var target = 0f
    private var shown = 0f
    private var centerText = ""
    private var label = ""
    private var colA: Int = ContextCompat.getColor(ctx, R.color.accent3)
    private var colB: Int = ContextCompat.getColor(ctx, R.color.accent)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val d = ctx.resources.displayMetrics.density
    private val sd = ctx.resources.displayMetrics.scaledDensity

    init {
        track.style = Paint.Style.STROKE
        track.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, R.color.text_muted), 55)
        arcP.style = Paint.Style.STROKE
        arcP.strokeCap = Paint.Cap.ROUND
        tp.color = ContextCompat.getColor(ctx, R.color.text_primary)
        tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.DEFAULT_BOLD
        lp.color = ContextCompat.getColor(ctx, R.color.text_muted)
        lp.textAlign = Paint.Align.CENTER
        lp.textSize = 12f * sd
    }

    fun setColors(a: Int, b: Int) {
        colA = a
        colB = b
        invalidate()
    }

    fun setData(percent: Float, center: String, lab: String) {
        target = percent.coerceIn(0f, 100f)
        centerText = center
        label = lab
        val a = ValueAnimator.ofFloat(shown, target)
        a.duration = 800
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            shown = it.animatedValue as Float
            invalidate()
        }
        a.start()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        var w = MeasureSpec.getSize(wSpec)
        if (w <= 0) w = (96 * d).toInt()
        setMeasuredDimension(w, w + (28 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val sw = w * 0.085f
        val rad = w / 2f - sw * 1.2f
        val cx = w / 2f
        val cy = w / 2f
        track.strokeWidth = sw
        arcP.strokeWidth = sw
        arcP.shader = LinearGradient(0f, 0f, w, w, colA, colB, Shader.TileMode.CLAMP)
        oval.set(cx - rad, cy - rad, cx + rad, cy + rad)
        c.drawCircle(cx, cy, rad, track)
        if (shown > 0.5f) c.drawArc(oval, -90f, 360f * shown / 100f, false, arcP)
        tp.textSize = w * 0.22f
        val fm = tp.fontMetrics
        c.drawText(centerText, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
        c.drawText(label, cx, w + 18f * d, lp)
    }
}

// ====================================================================== live sparkline
class SparkView(ctx: Context) : View(ctx) {
    private val data = ArrayList<Float>()
    var maxPoints = 60
    var fixedMin: Float? = null
    var fixedMax: Float? = null
    private var colA: Int = ContextCompat.getColor(ctx, R.color.accent3)
    private var colB: Int = ContextCompat.getColor(ctx, R.color.accent2)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val area = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lp = Path()
    private val ap = Path()
    private val d = ctx.resources.displayMetrics.density

    init {
        line.style = Paint.Style.STROKE
        line.strokeWidth = 2.2f * d
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        area.style = Paint.Style.FILL
        grid.style = Paint.Style.STROKE
        grid.strokeWidth = 1f
        grid.color = ContextCompat.getColor(ctx, R.color.divider)
    }

    fun setColors(a: Int, b: Int) {
        colA = a
        colB = b
    }

    fun push(v: Float) {
        data.add(v)
        while (data.size > maxPoints) data.removeAt(0)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        for (k in 1..3) c.drawLine(0f, h * k / 4f, w, h * k / 4f, grid)
        if (data.size < 2) return
        var mn = fixedMin ?: data.min()
        var mx = fixedMax ?: data.max()
        if (mx - mn < 1e-3f) {
            mx = mn + 1f
        }
        if (fixedMin == null) mn -= (mx - mn) * 0.1f
        if (fixedMax == null) mx += (mx - mn) * 0.1f
        val pad = 4f * d
        val step = (w - pad * 2f) / (maxPoints - 1).toFloat()
        lp.reset()
        ap.reset()
        val n = data.size
        for (i in 0 until n) {
            val x = w - pad - (n - 1 - i) * step
            val y = h - pad - (data[i] - mn) / (mx - mn) * (h - pad * 2f)
            if (i == 0) {
                lp.moveTo(x, y)
                ap.moveTo(x, h)
                ap.lineTo(x, y)
            } else {
                lp.lineTo(x, y)
                ap.lineTo(x, y)
            }
        }
        ap.lineTo(w - pad, h)
        ap.close()
        area.shader = LinearGradient(
            0f, 0f, 0f, h, ColorUtils.setAlphaComponent(colA, 90), ColorUtils.setAlphaComponent(colB, 0), Shader.TileMode.CLAMP
        )
        line.shader = LinearGradient(0f, 0f, w, 0f, colB, colA, Shader.TileMode.CLAMP)
        c.drawPath(ap, area)
        c.drawPath(lp, line)
    }
}
