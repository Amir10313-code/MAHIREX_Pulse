package com.mahirex.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ReportIO {
    const val FORMAT = "mahirex-report"

    // ------------------------------------------------------------ JSON (graphical file)
    fun toJson(r: Report): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", 1)
        root.put("app", "MAHIREX")
        root.put("appVersion", r.appVersion)
        root.put("createdAt", r.createdAt)
        root.put("lang", r.lang)
        val mo = JSONObject()
        for ((k, v) in r.metrics) mo.put(k, v)
        root.put("metrics", mo)
        val arr = JSONArray()
        for (s in r.sections) {
            val so = JSONObject()
            so.put("id", s.id)
            so.put("title", s.title)
            so.put("preview", s.preview)
            val items = JSONArray()
            for (item in s.items) {
                val io = JSONObject()
                io.put("k", item.key)
                io.put("v", item.value)
                io.put("h", if (item.header) 1 else 0)
                io.put("l", item.level)
                io.put("t", item.tone)
                items.put(io)
            }
            so.put("items", items)
            arr.put(so)
        }
        root.put("sections", arr)
        return root.toString()
    }

    private fun fromJson(text: String): Report? {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT) return null
            val metrics = LinkedHashMap<String, Any>()
            val mo = root.optJSONObject("metrics")
            if (mo != null) {
                val keys = mo.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = mo.get(k)
                    metrics[k] = v
                }
            }
            val arr = root.optJSONArray("sections") ?: return null
            val sections = ArrayList<InfoSection>()
            for (i in 0 until arr.length()) {
                val so = arr.getJSONObject(i)
                val s = InfoSection(so.optString("id", "s$i"), so.optString("title", ""))
                s.preview = so.optString("preview", "")
                val items = so.optJSONArray("items")
                if (items != null) {
                    for (j in 0 until items.length()) {
                        val io = items.getJSONObject(j)
                        s.items.add(
                            InfoItem(
                                io.optString("k", ""), io.optString("v", ""),
                                io.optInt("h", 0) == 1, io.optInt("l", 1), io.optInt("t", 0)
                            )
                        )
                    }
                }
                sections.add(s)
            }
            if (sections.isEmpty()) return null
            return Report(
                root.optLong("createdAt", 0L), root.optString("lang", ""),
                root.optString("appVersion", ""), metrics, sections, false
            )
        } catch (e: Exception) {
            return null
        }
    }

    // ------------------------------------------------------------ plain text
    fun toText(ctx: Context, r: Report): String {
        val line = "=".repeat(60)
        val thin = "-".repeat(60)
        val persian = r.lang == "fa"
        val sb = StringBuilder()
        sb.append(line).append("\n")
        sb.append(ctx.getString(R.string.report_txt_title)).append("\n")
        sb.append(ctx.getString(R.string.report_txt_by)).append("\n")
        sb.append(ctx.getString(R.string.report_txt_date)).append(": ").append(Fmt.date(r.createdAt, persian)).append("\n")
        sb.append(line).append("\n")
        for (sec in r.sections) {
            sb.append("\n[ ").append(sec.title).append(" ]\n")
            sb.append(thin).append("\n")
            var firstSub = true
            for (item in sec.items) {
                if (item.header) {
                    if (!firstSub) sb.append("\n")
                    firstSub = false
                    sb.append(if (item.level >= 2) "   > " else ">> ").append(item.key).append("\n")
                } else {
                    sb.append(item.key).append(": ").append(item.value).append("\n")
                }
            }
        }
        sb.append("\n").append(line).append("\n")
        sb.append(ctx.getString(R.string.report_txt_end)).append("\n")
        return sb.toString()
    }

    /** Reads the TXT format written by [toText]: sections only, no metrics. */
    private fun fromText(text: String): Report? {
        val sections = ArrayList<InfoSection>()
        var cur: InfoSection? = null
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.startsWith("[ ") && line.endsWith(" ]")) {
                cur = InfoSection("txt" + sections.size, line.substring(2, line.length - 2))
                sections.add(cur)
                continue
            }
            val c = cur ?: continue
            if (line.isBlank() || line.startsWith("---") || line.startsWith("===")) continue
            if (line.startsWith(">> ")) {
                c.sub(line.substring(3))
            } else if (line.trimStart().startsWith("> ")) {
                c.sub2(line.trimStart().substring(2))
            } else {
                val idx = line.indexOf(": ")
                if (idx > 0) c.add(line.substring(0, idx), line.substring(idx + 2))
            }
        }
        if (sections.isEmpty()) return null
        return Report(0L, "", "", LinkedHashMap<String, Any>(), sections, true)
    }

    fun parse(raw: String): Report? {
        val text = raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return if (text.startsWith("{")) fromJson(text) else fromText(text)
    }
}
