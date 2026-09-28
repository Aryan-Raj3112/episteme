package com.aryan.reader.shared.pdf

import platform.Foundation.NSUserDefaults

/**
 * iOS actual of the OCR selection gate. Mirrors the Android PdfPreferences
 * benchmark: the language and the explicit user-selection flag live in
 * NSUserDefaults under versioned keys so the first-run OCR dialog parity works
 * the same way `ocr_language_selected_key` does on Android.
 */
actual object SharedPdfOcrGate {
    private const val LanguageKey = "reader_shared_pdf_ocr_language_v1"
    private const val SelectedKey = "reader_shared_pdf_ocr_language_selected_v1"

    // Pre-gate iOS builds stored only the language under this key. Existing
    // users must not see the first-run dialog again, so it seeds both reads.
    private const val LegacyLanguageKey = "reader_ios_pdf_ocr_language_v1"

    actual fun loadLanguage(): SharedPdfOcrLanguage {
        val defaults = NSUserDefaults.standardUserDefaults
        val stored = defaults.stringForKey(LanguageKey) ?: defaults.stringForKey(LegacyLanguageKey)
        return SharedPdfOcrLanguage.fromId(stored)
    }

    actual fun hasUserSelectedLanguage(): Boolean {
        val defaults = NSUserDefaults.standardUserDefaults
        return defaults.boolForKey(SelectedKey) ||
            defaults.stringForKey(LegacyLanguageKey) != null
    }

    actual fun markUserSelectedLanguage() {
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = SelectedKey)
    }

    actual fun persistLanguage(language: SharedPdfOcrLanguage) {
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.setObject(language.id, forKey = LanguageKey)
        defaults.setBool(true, forKey = SelectedKey)
    }
}
