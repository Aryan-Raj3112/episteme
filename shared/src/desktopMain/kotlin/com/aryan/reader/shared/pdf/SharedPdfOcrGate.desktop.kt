package com.aryan.reader.shared.pdf

/**
 * Desktop actual: there is no OCR engine on desktop, so every script reports
 * unsupported and the gate is an in-memory no-op. Shared UI hides OCR actions
 * behind [isSupportedOnPlatform], so this only affects code paths that skip
 * that check.
 *
 * (Added to repair the desktop target: the OCR expects landed with androidMain
 * + iosMain actuals only, which left `:shared:compileKotlinDesktop` red.)
 */
actual val SharedPdfOcrLanguage.isSupportedOnPlatform: Boolean
    get() = false

actual object SharedPdfOcrGate {
    private var language: SharedPdfOcrLanguage = SharedPdfOcrLanguage.LATIN
    private var userSelected: Boolean = false

    actual fun loadLanguage(): SharedPdfOcrLanguage = language

    actual fun hasUserSelectedLanguage(): Boolean = userSelected

    actual fun markUserSelectedLanguage() {
        userSelected = true
    }

    actual fun persistLanguage(language: SharedPdfOcrLanguage) {
        this.language = language
        userSelected = true
    }
}
