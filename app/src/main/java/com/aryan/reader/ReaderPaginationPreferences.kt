package com.aryan.reader

import android.content.Context
import androidx.core.content.edit

private const val READER_PAGINATION_PREFS_NAME = "reader_prefs"
private const val PDF_RIGHT_TO_LEFT_PAGINATION_KEY = "pdf_right_to_left_pagination_enabled"
private const val EPUB_RIGHT_TO_LEFT_PAGINATION_KEY = "epub_right_to_left_pagination_enabled"

fun savePdfRightToLeftPagination(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(READER_PAGINATION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PDF_RIGHT_TO_LEFT_PAGINATION_KEY, enabled) }
}

fun loadPdfRightToLeftPagination(context: Context): Boolean {
    val prefs = context.getSharedPreferences(READER_PAGINATION_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PDF_RIGHT_TO_LEFT_PAGINATION_KEY, false)
}

fun saveEpubRightToLeftPagination(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(READER_PAGINATION_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putBoolean(EPUB_RIGHT_TO_LEFT_PAGINATION_KEY, enabled) }
}

fun loadEpubRightToLeftPagination(context: Context): Boolean {
    val prefs = context.getSharedPreferences(READER_PAGINATION_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(EPUB_RIGHT_TO_LEFT_PAGINATION_KEY, false)
}
