package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.ui.sharedAndroidMobileApplicationContext

/**
 * Android actual of the OCR selection gate. Mirrors PdfPreferences
 * ("epub_reader_settings" + "ocr_language_key"/"ocr_language_selected_key") so
 * the shared reader screen and PdfViewerScreen stay in lockstep.
 */
actual object SharedPdfOcrGate {
    private const val PrefsName = "epub_reader_settings"
    private const val LanguageKey = "ocr_language_key"
    private const val SelectedKey = "ocr_language_selected_key"

    private val prefs get() = sharedAndroidMobileApplicationContext()
        ?.getSharedPreferences(PrefsName, android.content.Context.MODE_PRIVATE)

    actual fun loadLanguage(): SharedPdfOcrLanguage {
        val name = prefs?.getString(LanguageKey, SharedPdfOcrLanguage.LATIN.name)
        return try {
            SharedPdfOcrLanguage.valueOf(name ?: SharedPdfOcrLanguage.LATIN.name)
        } catch (_: Exception) {
            SharedPdfOcrLanguage.LATIN
        }
    }

    actual fun hasUserSelectedLanguage(): Boolean =
        prefs?.getBoolean(SelectedKey, false) ?: false

    actual fun markUserSelectedLanguage() {
        prefs?.edit()?.putBoolean(SelectedKey, true)?.apply()
    }

    actual fun persistLanguage(language: SharedPdfOcrLanguage) {
        prefs?.edit()
            ?.putString(LanguageKey, language.name)
            ?.putBoolean(SelectedKey, true)
            ?.apply()
    }
}
