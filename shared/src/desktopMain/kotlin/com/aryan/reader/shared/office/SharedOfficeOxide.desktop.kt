package com.aryan.reader.shared.office

/**
 * Desktop actual. Not wired yet.
 *
 * The Rust bridge exposes a plain C ABI, which the JVM cannot call directly, so
 * desktop needs either a JNA binding or a small JNI shim like Android's. Neither
 * is in place, and the `.dylib` is not packaged into `desktopApp` yet.
 *
 * Reporting unavailable (rather than throwing) keeps `FileCapabilities` honest:
 * the DOCX-to-office_oxide path simply stays off on desktop until Phase 3 of
 * `docs/office-oxide-integration-plan.md` lands. Android and iOS are the
 * priority platforms and are fully wired.
 */
actual object SharedOfficeOxide {
    actual val isAvailable: Boolean = false

    actual fun supports(extension: String): Boolean = false

    actual fun renderHtml(bytes: ByteArray, extension: String, embedImages: Boolean): String? = null

    actual fun renderPlainText(bytes: ByteArray, extension: String): String? = null
}
