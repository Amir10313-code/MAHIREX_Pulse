package com.mahirex.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AppCompatDelegate.setDefaultNightMode(Prefs.nightMode())
        Lang.applySaved()
    }
}
