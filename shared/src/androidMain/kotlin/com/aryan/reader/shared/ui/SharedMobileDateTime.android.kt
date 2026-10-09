package com.aryan.reader.shared.ui

import java.text.DateFormat
import java.util.Date
internal actual fun formatSharedMobileDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))

/**
 * Android benchmark parity (`EpubReaderPreferences.kt:96`): use
 * `android.text.format.DateFormat.getTimeFormat`, which honours the user's
 * system 12/24-hour setting, rather than the locale default that
 * `DateFormat.getTimeInstance` would pick. Falls back to the locale default
 * before an application context has been registered.
 */
internal actual fun formatSharedMobileClockTime(epochMillis: Long): String {
    val context = AndroidSharedMobileContext.applicationContext
    return if (context != null) {
        android.text.format.DateFormat.getTimeFormat(context).format(Date(epochMillis))
    } else {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
    }
}

internal actual fun formatSharedMobileBookInfoDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(epochMillis))
