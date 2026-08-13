package com.aryan.reader

import android.content.Context
import com.aryan.reader.shared.sanitizeCustomSleepTimerMinutes

private const val LISTEN_SLEEP_TIMER_PREFS = "listen_sleep_timer_preferences"
private const val CUSTOM_SLEEP_TIMERS = "custom_sleep_timers"

internal fun loadCustomSleepTimerMinutes(context: Context): List<Int> {
    val stored = context.getSharedPreferences(LISTEN_SLEEP_TIMER_PREFS, Context.MODE_PRIVATE)
        .getString(CUSTOM_SLEEP_TIMERS, null)
        .orEmpty()
        .split(',')
        .mapNotNull(String::toIntOrNull)
    return sanitizeCustomSleepTimerMinutes(stored)
}

internal fun saveCustomSleepTimerMinutes(context: Context, values: List<Int>) {
    val encoded = sanitizeCustomSleepTimerMinutes(values).joinToString(",")
    context.getSharedPreferences(LISTEN_SLEEP_TIMER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(CUSTOM_SLEEP_TIMERS, encoded)
        .apply()
}
