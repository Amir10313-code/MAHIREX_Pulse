package com.mahirex.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * TextView that always reserves the full height of its wrapped text. Mixed Persian / Latin text
 * could otherwise lose its last line when a weighted row measured it at a different width.
 */
class MTextView(ctx: Context) : TextView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val l = layout ?: return
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        if (hMode == MeasureSpec.EXACTLY) return
        val lines = if (maxLines in 1 until l.lineCount) maxLines else l.lineCount
        var need = l.getLineTop(lines) + compoundPaddingTop + compoundPaddingBottom
        if (hMode == MeasureSpec.AT_MOST) need = Math.min(need, MeasureSpec.getSize(heightMeasureSpec))
        if (need > measuredHeight) setMeasuredDimension(measuredWidth, need)
    }
}

/** Small helper that builds the neon "glass" look used everywhere. */
class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density

    /**
     * Direction of the language the app is really showing. It is taken from the resource that was
     * actually picked (lang_code), not from the first device locale: a phone whose first locale is
     * English but whose second is Persian shows Persian text, so the layout has to be RTL too.
     */
    val rtl: Boolean = ctx.getString(R.string.lang_code).let { it == "fa" || it == "ar" || it == "ur" }

    val dir: Int get() = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    val night: Boolean
        get() = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    fun dpf(v: Float): Float = v * density
    fun col(id: Int): Int = ContextCompat.getColor(ctx, id)
    fun alpha(color: Int, a: Int): Int = ColorUtils.setAlphaComponent(color, a)

    val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun tv(text: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = MTextView(ctx)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        t.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
        if (rtl) t.setLineSpacing(0f, 1.12f)
        return t
    }

    fun centered(t: TextView): TextView {
        t.gravity = Gravity.CENTER
        t.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        return t
    }

    fun glass(radiusDp: Int = 22, selected: Boolean = false): Drawable {
        val a = if (selected) alpha(col(R.color.accent3), 230) else alpha(col(R.color.accent3), 110)
        val b = if (selected) alpha(col(R.color.accent2), 230) else alpha(col(R.color.accent2), 80)
        return GlassDrawable(dpf(radiusDp.toFloat()), col(R.color.card), a, b, dpf(if (selected) 1.6f else 1f))
    }

    fun solidGlass(radiusDp: Int = 22): Drawable =
        GlassDrawable(
            dpf(radiusDp.toFloat()), col(R.color.card_solid),
            alpha(col(R.color.accent3), 140), alpha(col(R.color.accent2), 110), dpf(1f)
        )

    /**
     * Touch feedback: the whole pressed item gets a soft accent tint and a glowing outline
     * (instead of a round ripple spreading from the finger), and fades back out smoothly.
     */
    fun pressable(content: Drawable?, radiusDp: Int = 16): Drawable {
        val on = GradientDrawable()
        on.cornerRadius = dpf(radiusDp.toFloat())
        on.setColor(alpha(col(R.color.accent), 62))
        on.setStroke(dp(1), alpha(col(R.color.accent), 200))
        val overlay = StateListDrawable()
        overlay.addState(intArrayOf(android.R.attr.state_pressed), on)
        overlay.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        overlay.setEnterFadeDuration(60)
        overlay.setExitFadeDuration(340)
        return LayerDrawable(arrayOf(content ?: ColorDrawable(Color.TRANSPARENT), overlay))
    }

    fun ripple(content: Drawable?): Drawable = pressable(content, 16)

    /** background + press feedback (tint and a small "push in" scale) for a clickable view */
    fun feel(v: View, content: Drawable?, radiusDp: Int = 16) {
        v.background = pressable(content, radiusDp)
        v.isClickable = true
        v.isFocusable = true
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
            }
            false
        }
    }

    /** click that waits a moment so the pressed highlight is seen before the screen changes */
    fun onTap(v: View, delayMs: Long = 130L, action: () -> Unit) {
        var busy = false
        v.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true
            v.postDelayed({
                busy = false
                action()
            }, delayMs)
        }
    }

    /** vertical glass card */
    fun card(padDp: Int = 16, radiusDp: Int = 22): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
        l.background = glass(radiusDp)
        return l
    }

    fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0, start: Int = 0, end: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        p.marginStart = dp(start)
        p.marginEnd = dp(end)
        return p
    }

    fun weight(h: Int = WRAP, w: Float = 1f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, h, w)

    fun divider(): View {
        val v = View(ctx)
        v.setBackgroundColor(col(R.color.divider))
        return v
    }

    fun icon(type: Int, sizeDp: Int, a: Int = col(R.color.accent), b: Int = col(R.color.accent2)): IconView {
        val i = IconView(ctx)
        i.type = type
        i.setColors(a, b)
        i.minimumWidth = dp(sizeDp)
        i.minimumHeight = dp(sizeDp)
        return i
    }

    /** rounded tinted tile with a neon icon (used in the category list) */
    fun iconTile(type: Int, color: Int, tileDp: Int = 44, iconDp: Int = 24): View {
        val box = LinearLayout(ctx)
        box.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.cornerRadius = dpf(14f)
        g.setColor(alpha(color, 38))
        g.setStroke(dp(1), alpha(color, 120))
        box.background = g
        val ic = IconView(ctx)
        ic.type = type
        ic.setColors(color, color)
        box.addView(ic, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
        box.layoutParams = LinearLayout.LayoutParams(dp(tileDp), dp(tileDp))
        return box
    }

    /** pill button with icon + label */
    fun pillButton(label: String, iconType: Int, primary: Boolean, onClick: () -> Unit): LinearLayout {
        val b = LinearLayout(ctx)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), 0, dp(14), 0)
        val bg: Drawable = if (primary) {
            val g = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(col(R.color.accent), col(R.color.accent2))
            )
            g.cornerRadius = dpf(26f)
            g
        } else {
            glass(26)
        }
        feel(b, bg, 26)
        val tc = if (primary) 0xFFFFFFFF.toInt() else col(R.color.text_primary)
        val ic = IconView(ctx)
        ic.type = iconType
        ic.glow = false
        ic.setColors(tc, tc)
        b.addView(ic, LinearLayout.LayoutParams(dp(20), dp(20)))
        val t = tv(label, 13.5f, tc, true)
        t.maxLines = 1
        t.setPadding(dp(8), 0, 0, 0)
        b.addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
        b.setOnClickListener { onClick() }
        return b
    }

    fun pill(label: String, color: Int, sp: Float): TextView {
        val t = MTextView(ctx)
        t.text = label
        t.textSize = sp
        t.setTextColor(color)
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_FIRST_STRONG
        t.gravity = Gravity.CENTER
        t.setPadding(dp(14), dp(4), dp(14), dp(4))
        val g = GradientDrawable()
        g.setColor(alpha(color, 40))
        g.cornerRadius = dpf(30f)
        g.setStroke(dp(1), color)
        t.background = g
        return t
    }

    fun toneColor(tone: Int): Int? = when (tone) {
        Tone.OK -> col(R.color.ok)
        Tone.WARN -> col(R.color.warn)
        Tone.BAD -> col(R.color.bad)
        Tone.MUTED -> col(R.color.text_muted)
        else -> null
    }

    fun space(hDp: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(hDp))
        return v
    }

    /** "MAHIREX" in the neon gradient + small "PULSE" tag */
    fun wordmark(sp: Float): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        box.layoutDirection = View.LAYOUT_DIRECTION_LTR   // brand always reads MAHIREX PULSE, in every language
        val m = GradText(ctx)
        m.text = "MAHIREX"
        m.textDirection = View.TEXT_DIRECTION_LTR
        m.textSize = sp
        m.setTypeface(m.typeface, Typeface.BOLD)
        m.letterSpacing = 0.18f
        box.addView(m, LinearLayout.LayoutParams(WRAP, WRAP))
        val p = GradText(ctx)
        p.text = ctx.getString(R.string.brand_sub).uppercase()
        p.textDirection = View.TEXT_DIRECTION_LTR
        p.textSize = sp * 0.52f
        p.setTypeface(p.typeface, Typeface.BOLD)
        p.letterSpacing = 0.2f
        p.colorA = 0xFF2FD6FF.toInt()
        p.colorB = 0xFF3FA9FF.toInt()
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.marginStart = dp(6)
        box.addView(p, lp)
        return box
    }

    fun sectionTitle(text: String): TextView {
        val t = tv(text, 13f, col(R.color.text_muted), true)
        t.letterSpacing = 0.08f
        return t
    }
}
