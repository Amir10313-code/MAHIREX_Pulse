package com.mahirex.app

import android.app.Activity
import android.os.Bundle

/**
 * Invisible "shortcut" behind the widget button: it starts the speed test service and closes at once,
 * so the app is never shown. Starting the service from here is always allowed because the tap just happened.
 */
class WidgetTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (!SpeedTestService.start(this)) {
                HomeWidgets.testEnd(this, HomeWidgets.ERR_START)
                HomeWidgets.updateAll(this)
            }
        } catch (e: Throwable) {
            // never show a crash for a widget tap
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
