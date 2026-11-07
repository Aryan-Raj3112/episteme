package com.aryan.reader.epub

import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

/**
 * Shared primitives for the HTML import pipeline ([SingleFileImporter]): chapter
 * splitting that never breaks a tag, and a sanitizer that preserves paragraph
 * alignment while forbidding all other inline CSS.
 *
 * Split out of `SingleFileImporter` so that file stays under its architecture
 * line budget; everything here is stateless.
 */

/** Common log tag for DOCX image diagnostics (bridge, parity gate, chapter images). */
internal const val DOCX_IMAGE_DEBUG_TAG = "DocxImageDebug"

/** Block elements whose alignment the reader preserves (see [retainTextAlignAttributes]). */
private val blockTagsWithAlign = listOf(
    "p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "li", "td", "th", "section", "article",
)

/**
 * The sanitizer for HTML imports. `align` is allowlisted only on block elements:
 * `style` stays forbidden, so document fonts and colours can never leak into the
 * reader's own typography — only the alignment survives.
 */
internal fun buildHtmlImportSafelist(): Safelist {
    val safelist = Safelist.relaxed()
        .addTags("article", "aside", "details", "div", "figcaption", "figure", "footer", "header", "main", "section", "summary")
        .addAttributes(":all", "class", "dir", "id", "lang", "title")
        .addAttributes("a", "name", "target")
        .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
        .addProtocols("img", "src", "http", "https", "data", "file", "content")
    blockTagsWithAlign.forEach { tag -> safelist.addAttributes(tag, "align") }
    return safelist
}

private val retainedTextAlignValues = setOf("left", "right", "center", "justify")

/**
 * Carries `text-align` from inline `style` into the `align` attribute before
 * sanitization strips all styles. Neither DOCX engine emits alignment today
 * (office_oxide parses `w:jc` into its IR but its HTML renderer drops it;
 * mammoth never emits it), but Google Docs and Word center titles and headings
 * routinely — without this the fix, when it lands upstream, would die in our
 * sanitizer a second time. Values outside the four literals are dropped, so no
 * CSS can smuggle through.
 */
internal fun retainTextAlignAttributes(root: Element) {
    for (element in root.select(blockTagsWithAlign.joinToString(", "))) {
        val align = element.attr("style").extractTextAlignValue() ?: continue
        element.attr("align", align)
    }
}

internal fun String.extractTextAlignValue(): String? {
    // CSS property names are ASCII case-insensitive; values are lowercased to
    // match the allowlist.
    val value = Regex("text-align\\s*:\\s*([A-Za-z]+)", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.getOrNull(1)
        ?: return null
    return value.lowercase().takeIf { it in retainedTextAlignValues }
}

/**
 * Chooses where the current chapter chunk ends. Small tags are kept whole by
 * breaking before them; giant tags (multi-megabyte `data:` images) and tags
 * continued from an earlier chunk/line extend to their closing bracket so the
 * tag stays whole even if the chapter overflows its budget. Every return
 * exceeds `start`, so the caller always makes progress.
 */
internal fun findHtmlChunkEnd(
    source: String,
    start: Int,
    requestedEnd: Int,
    absoluteEnd: Int,
    startsInTag: Boolean,
): Int {
    if (requestedEnd >= absoluteEnd) return absoluteEnd

    if (!startsInTag) {
        val tagStart = source.lastIndexOf('<', requestedEnd - 1)
        val tagEnd = source.lastIndexOf('>', requestedEnd - 1)
        if (tagStart > tagEnd && tagStart >= start) {
            // Inside a tag that starts in this chunk: break before it when
            // small, so chapters stay balanced…
            if (tagStart > start && requestedEnd - tagStart <= 4096) {
                return tagStart
            }
            // …but a giant tag (or one starting exactly here) extends to
            // its end instead of splitting.
            val close = source.indexOf('>', requestedEnd)
            return if (close >= 0) minOf(close + 1, absoluteEnd) else absoluteEnd
        }
        return requestedEnd
    }

    // Continuing a tag from an earlier chunk/line: extend to its closing
    // bracket so the tag stays whole even if the chapter overflows.
    val close = source.indexOf('>', requestedEnd)
    return if (close >= 0) minOf(close + 1, absoluteEnd) else absoluteEnd
}

/**
 * Returns true when [end] sits inside a `<…>` tag, scanning [start]..[end]
 * from [initiallyInTag]. Quote-aware, so `>` inside attribute values does
 * not end the tag early. Used only for split decisions; a wrong answer
 * merely reproduces the old split behaviour, never corrupts content.
 */
internal fun scanHtmlTagState(segment: String, start: Int, end: Int, initiallyInTag: Boolean): Boolean {
    var inTag = initiallyInTag
    var quote: Char? = null
    var index = start
    while (index < end) {
        val char = segment[index]
        if (quote != null) {
            if (char == quote) {
                quote = null
            }
        } else if (inTag) {
            if (char == '"' || char == '\'') {
                quote = char
            } else if (char == '>') {
                inTag = false
            }
        } else if (char == '<') {
            inTag = true
        }
        index++
    }
    return inTag
}
