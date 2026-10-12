package com.aryan.reader.shared.office

/**
 * Portable entry point for reading Microsoft Office documents through
 * [`office_oxide`](https://crates.io/crates/office_oxide), a Rust library linked
 * per platform.
 *
 * Backed natively on Android (JNI over `app/src/main/cpp/office_oxide_bridge_jni.cpp`)
 * and iOS (cinterop over the same C ABI). Every method is safe to call on any
 * platform: [isAvailable] reports whether the native bridge loaded, and the
 * rendering methods return null when it did not or when the document could not
 * be read. Callers must therefore always have a fallback path.
 *
 * This is deliberately the *whole* public surface. See
 * `docs/office-oxide-integration-plan.md` for what is intentionally not exposed.
 */
expect object SharedOfficeOxide {
    /** True when the native bridge loaded and can be called. */
    val isAvailable: Boolean

    /** True when [extension] names an Office format this bridge can read. */
    fun supports(extension: String): Boolean

    /**
     * Renders a document to an HTML fragment.
     *
     * @param bytes the raw document bytes
     * @param extension file extension, with or without a leading dot
     * @param embedImages when true, images are inlined as `data:` URIs so the
     *   fragment is self-contained
     * @return the HTML, or null when unavailable, unsupported or unreadable
     */
    fun renderHtml(bytes: ByteArray, extension: String, embedImages: Boolean): String?

    /**
     * Extracts plain text for search, TTS and library indexing.
     *
     * @return the text, or null when unavailable, unsupported or unreadable
     */
    fun renderPlainText(bytes: ByteArray, extension: String): String?
}
