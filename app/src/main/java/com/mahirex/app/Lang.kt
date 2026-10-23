package com.mahirex.app

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object Lang {
    class Item(val tag: String, val name: String)

    val items: List<Item> = listOf(
        Item("fa", "فارسی"),
        Item("en", "English"),
        Item("ar", "العربية"),
        Item("tr", "Türkçe"),
        Item("ru", "Русский"),
        Item("es", "Español"),
        Item("fr", "Français"),
        Item("de", "Deutsch"),
        Item("pt", "Português"),
        Item("zh", "中文"),
        Item("hi", "हिन्दी"),
        Item("id", "Bahasa Indonesia"),
        Item("ur", "اردو"),
        Item("ja", "日本語")
    )

    /** Called once at startup. Android 13+ keeps per-app languages itself. */
    fun applySaved() {
        if (Build.VERSION.SDK_INT < 33) {
            val tag = Prefs.lang()
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            )
        }
    }

    fun set(tag: String) {
        Prefs.setLang(tag)
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /** "" = following the device language, otherwise the chosen language tag */
    fun current(): String {
        val l = AppCompatDelegate.getApplicationLocales()
        if (l.isEmpty()) return ""
        val lang = l.get(0)?.language ?: return ""
        return when (lang) {
            "in" -> "id"
            else -> lang
        }
    }
}
