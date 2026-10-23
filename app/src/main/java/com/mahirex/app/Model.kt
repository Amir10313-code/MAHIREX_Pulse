package com.mahirex.app

object Tone {
    const val NONE = 0
    const val OK = 1
    const val WARN = 2
    const val BAD = 3
    const val MUTED = 4
}

class InfoItem(
    val key: String,
    val value: String,
    val header: Boolean = false,
    val level: Int = 1,
    val tone: Int = Tone.NONE
)

class InfoSection(val id: String, val title: String) {
    val items = ArrayList<InfoItem>()
    var preview: String = ""

    fun add(key: String, value: Any?, tone: Int = Tone.NONE) {
        val v = value?.toString()?.trim()
        items.add(InfoItem(key, if (v.isNullOrEmpty()) "—" else v, false, 1, tone))
    }

    fun sub(title: String) {
        items.add(InfoItem(title, "", true, 1))
    }

    // second-level heading, shown indented under the previous sub()
    fun sub2(title: String) {
        items.add(InfoItem(title, "", true, 2))
    }
}

/** A full phone report: numeric metrics (for the graphics) + all text sections (for the details). */
class Report(
    val createdAt: Long,
    val lang: String,
    val appVersion: String,
    val metrics: Map<String, Any>,
    val sections: List<InfoSection>,
    val fromText: Boolean = false
) {
    fun str(key: String): String? = metrics[key]?.toString()
    fun num(key: String): Double? = (metrics[key] as? Number)?.toDouble()
    fun bool(key: String): Boolean? = metrics[key] as? Boolean
}

object Cache {
    @Volatile
    var report: Report? = null

    @Volatile
    var lang: String = ""

    @Volatile
    var other: Report? = null
}
