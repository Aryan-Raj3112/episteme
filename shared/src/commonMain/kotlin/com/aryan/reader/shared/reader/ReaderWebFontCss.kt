package com.aryan.reader.shared.reader

/**
 * Everything the WebView needs to render the reader's chosen typeface.
 *
 * Android's ChapterWebView injects an `@font-face` for the selected reader
 * font and then forces that family onto every text element with
 * `!important`; the publication's own font rules never win. The shared
 * WebView renderer only had a `body` rule plus a `file://` font URL, which
 * WKWebView cannot read from a `loadHTMLString` document, so iOS silently
 * fell back to the system font in vertical (WebView) reading mode while the
 * native paginated reader showed the real typeface.
 *
 * [faceCss] is the `@font-face` block (empty when no file-backed face is
 * available), [familyCss] is the value for `--reader-family`, and
 * [overrideCss] is the `!important` rule that keeps publication CSS from
 * replacing the user's choice.
 */
internal data class ReaderWebFontCss(
    val faceCss: String,
    val familyCss: String,
    val overrideCss: String,
) {
    /** CSS installed in the document and refreshed by the appearance script. */
    val documentCss: String
        get() = listOf(faceCss, overrideCss).filter { it.isNotBlank() }.joinToString("\n")
}

/**
 * Platform URL (or data URI) for a file-backed reader face, already wrapped as
 * a CSS `url(...)` value. Returns null when this platform cannot supply the
 * file, in which case the generic CSS family fallback is used.
 *
 * iOS returns a `data:` URI because its WKWebView document is loaded from an
 * HTML string with no file-read access; Android/desktop keep the file URL the
 * existing Android WebView injects.
 */
internal expect fun readerWebFontFaceSource(
    fontFamily: String,
    customFontPath: String?,
): String?

/** Compose-resources path of a bundled reader typeface, or null for system families. */
internal fun readerBundledFontResourcePath(fontFamilyName: String): String? = when (fontFamilyName) {
    "Merriweather" -> "files/fonts/merriweather.ttf"
    "Lora" -> "files/fonts/lora.ttf"
    "Lato" -> "files/fonts/lato.ttf"
    "Lexend" -> "files/fonts/lexend.ttf"
    "Roboto Mono" -> "files/fonts/roboto_mono.ttf"
    else -> null
}

internal fun ReaderSettings.readerWebFontCss(
    source: String? = readerWebFontFaceSource(fontFamily, customFontPath),
): ReaderWebFontCss {
    val customPath = customFontPath?.takeIf { it.isNotBlank() }
    val bundledResource = readerBundledFontResourcePath(fontFamily)
    val faceFamily = when {
        customPath != null -> "ReaderCustomFont"
        source != null && bundledResource != null -> "ReaderBundledFont"
        else -> null
    }
    val faceCss = if (faceFamily != null && source != null) {
        "@font-face { font-family: '$faceFamily'; src: $source; font-display: swap; }"
    } else {
        ""
    }
    val familyCss = when {
        // A custom file that the platform cannot load keeps Android's
        // fallback stack instead of silently switching the whole book to the
        // system sans font.
        customPath != null -> "'ReaderCustomFont', Georgia, 'Times New Roman', serif"
        faceFamily == "ReaderBundledFont" -> "'ReaderBundledFont', ${readerBundledFontFallback(fontFamily)}"
        else -> readerSystemFontFamilyCss(fontFamily)
    }
    val forceFamily = customPath != null || !isDefaultReaderFontFamily(fontFamily)
    return ReaderWebFontCss(
        faceCss = faceCss,
        familyCss = familyCss,
        overrideCss = if (forceFamily) {
            "$ReaderWebFontForceSelector { font-family: var(--reader-family) !important; }"
        } else {
            ""
        },
    )
}

/**
 * Android benchmark (`epub_reader.js` updateReaderStyles): the reader family is
 * applied to these elements with `!important` so publication CSS cannot keep a
 * different typeface inside the same book.
 */
private const val ReaderWebFontForceSelector =
    "body, p, span, div, li, a, h1, h2, h3, h4, h5, h6, blockquote, td, th"

internal fun isDefaultReaderFontFamily(fontFamily: String): Boolean =
    fontFamily.isBlank() || fontFamily == "Default" || fontFamily == "Original"

internal fun readerBundledFontFallback(fontFamily: String): String = when (fontFamily) {
    "Merriweather", "Lora" -> "Georgia, 'Times New Roman', serif"
    "Lato", "Lexend" -> "Inter, Segoe UI, Arial, sans-serif"
    "Roboto Mono" -> "'Roboto Mono', Consolas, monospace"
    else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
}
