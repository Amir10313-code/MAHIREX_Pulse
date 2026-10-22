package com.mahirex.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun size(b: Long): String {
        var v = b.toDouble()
        var i = 0
        val u = arrayOf("B", "KB", "MB", "GB", "TB")
        while (v >= 1024 && i < u.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${v.toLong()} B" else String.format(Locale.US, "%.2f %s", v, u[i])
    }

    /** Short size without decimals when round, e.g. "6 GB" or "119.5 GB" */
    fun sizeShort(b: Long): String {
        val gib = b / (1024.0 * 1024.0 * 1024.0)
        return if (gib >= 1.0) {
            if (Math.abs(gib - Math.round(gib)) < 0.05) String.format(Locale.US, "%d GB", Math.round(gib))
            else String.format(Locale.US, "%.1f GB", gib)
        } else {
            String.format(Locale.US, "%d MB", Math.round(b / (1024.0 * 1024.0)))
        }
    }

    fun date(ms: Long, persian: Boolean): String {
        val g = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
        if (!persian) return g.substring(0, 16)
        return try {
            val pc = android.icu.util.Calendar.getInstance(
                android.icu.util.ULocale("fa_IR@calendar=persian")
            )
            pc.timeInMillis = ms
            val y = pc.get(android.icu.util.Calendar.YEAR)
            val m = pc.get(android.icu.util.Calendar.MONTH) + 1
            val d = pc.get(android.icu.util.Calendar.DAY_OF_MONTH)
            String.format(Locale.US, "%04d/%02d/%02d", y, m, d) + "  " +
                SimpleDateFormat("HH:mm", Locale.US).format(Date(ms)) + "   (" + g.substring(0, 10) + ")"
        } catch (e: Throwable) {
            g
        }
    }
}
