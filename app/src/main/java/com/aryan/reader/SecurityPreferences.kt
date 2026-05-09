package com.aryan.reader

import android.content.SharedPreferences

class SecurityPreferences(
    private val prefs: SharedPreferences
) {

    companion object {
        private const val KEY_SCREEN_PROTECTION =
            "screen_protection"
    }

    var screenCaptureProtectionEnabled: Boolean
        get() = prefs.getBoolean(
            KEY_SCREEN_PROTECTION,
            false
        )
        set(value) {
            prefs.edit()
                .putBoolean(
                    KEY_SCREEN_PROTECTION,
                    value
                )
                .apply()
        }

    fun toggleScreenCaptureProtection() {
        screenCaptureProtectionEnabled =
            !screenCaptureProtectionEnabled
    }
}