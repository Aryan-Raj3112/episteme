package com.aryan.reader

import android.content.Context
import androidx.core.content.edit

private const val READER_PAGINATION_PREFS_NAME = "reader_prefs"
private const val READER_RIGHT_TO_LEFT_PAGINATION_KEY = "reader_right_to_left_pagination_enabled"

fun saveReaderRightToLeftPagination(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(READER_PAGINATION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(READER_RIGHT_TO_LEFT_PAGINATION_KEY, enabled) }
}

fun loadReaderRightToLeftPagination(context: Context): Boolean {
    val prefs = context.getSharedPreferences(READER_PAGINATION_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(READER_RIGHT_TO_LEFT_PAGINATION_KEY, false)
}
