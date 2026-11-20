package com.aryan.reader.shared

/**
 * HTML → the exact text the search scanners walk.
 *
 * The Android searcher walks a parsed DOM and the WebView flattens its own
 * nodes; the cross-platform shells only have chapter markup as a string. All
 * three must agree on the text, or hit ordinals drift: inline markup must not
 * split words (`text<sup>1</sup>` stays one run, exactly as a DOM walk reads
 * it), every block boundary separates words, whitespace runs collapse to one
 * space, and non-rendered tags contribute nothing.
 *
 * Deliberately not a general HTML parser: it only needs to produce the same
 * text, so it tracks text, tag boundaries and the four skipped elements.
 */
fun readerSearchHtmlToText(html: String): String {
    if (html.isEmpty()) return ""

    val out = StringBuilder(html.length)
    var lastWasSpace = true

    fun appendSpace() {
        if (out.isEmpty() || lastWasSpace) {
            lastWasSpace = true
            return
        }
        out.append(' ')
        lastWasSpace = true
    }

    fun appendText(text: CharSequence) {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char.isWhitespace()) {
                appendSpace()
                index++
                continue
            }
            if (char == '&') {
                val entity = decodeReaderSearchEntity(text, index)
                if (entity != null) {
                    if (entity.first.isWhitespace()) {
                        appendSpace()
                    } else {
                        out.append(entity.first)
                        lastWasSpace = false
                    }
                    index = entity.second
                    continue
                }
            }
            out.append(char)
            lastWasSpace = false
            index++
        }
    }

    var index = 0
    while (index < html.length) {
        val char = html[index]
        if (char != '<') {
            val nextTag = html.indexOf('<', index).let { if (it < 0) html.length else it }
            appendText(html.substring(index, nextTag))
            index = nextTag
            continue
        }

        // Markup: comments, declarations and doctypes are not text.
        if (html.startsWith("<!--", index)) {
            val end = html.indexOf("-->", index + 4)
            index = if (end < 0) html.length else end + 3
            continue
        }
        if (html.startsWith("<!", index) || html.startsWith("<?", index)) {
            val end = html.indexOf('>', index + 2)
            index = if (end < 0) html.length else end + 1
            continue
        }

        val tagEnd = html.indexOf('>', index + 1)
        if (tagEnd < 0) {
            // A stray '<' is text, not markup.
            appendText("<")
            index++
            continue
        }

        val tagBody = html.substring(index + 1, tagEnd)
        val isClosing = tagBody.startsWith("/")
        val name = tagBody
            .removePrefix("/")
            .trimStart()
            .takeWhile { it.isLetterOrDigit() || it == ':' || it == '-' || it == '_' }
            .substringAfter(':')
            .lowercase()
        index = tagEnd + 1

        if (!isClosing && name.isNotEmpty() && name in READER_SEARCH_SKIPPED_TAGS) {
            index = skipReaderSearchTagContent(html, name, index)
            appendSpace()
            continue
        }
        if (name in READER_SEARCH_BLOCK_BOUNDARY_TAGS) {
            appendSpace()
        }
    }

    return out.toString().trim()
}

/** Returns the decoded char and the index after its entity, or null when it is not one. */
private fun decodeReaderSearchEntity(text: CharSequence, startIndex: Int): Pair<Char, Int>? {
    val end = text.indexOf(';', startIndex + 1)
    if (end < 0 || end - startIndex > 10) return null
    val body = text.substring(startIndex + 1, end)
    val decoded = when {
        body.equals("amp", ignoreCase = true) -> '&'
        body.equals("lt", ignoreCase = true) -> '<'
        body.equals("gt", ignoreCase = true) -> '>'
        body.equals("quot", ignoreCase = true) -> '"'
        body.equals("apos", ignoreCase = true) -> '\''
        body.equals("nbsp", ignoreCase = true) -> ' '
        body.startsWith("#x", ignoreCase = true) ->
            body.substring(2).toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }?.let(::codePointChar)
        body.startsWith("#") ->
            body.substring(1).toIntOrNull()?.takeIf { it in 1..0x10FFFF }?.let(::codePointChar)
        else -> null
    } ?: return null
    return decoded to (end + 1)
}

/** BMP-only decode; astral entities keep their replacement char, which never matches prose. */
private fun codePointChar(value: Int): Char = if (value <= 0xFFFF) value.toChar() else '\uFFFD'

/** Skips the content of a non-rendered element (script/style/noscript/template). */
private fun skipReaderSearchTagContent(html: String, tagName: String, fromIndex: Int): Int {
    var depth = 1
    var index = fromIndex
    while (index < html.length) {
        val open = html.indexOf('<', index)
        if (open < 0) return html.length
        val close = html.indexOf('>', open + 1)
        if (close < 0) return html.length
        val body = html.substring(open + 1, close)
        val isClosing = body.startsWith("/")
        val name = body
            .removePrefix("/")
            .trimStart()
            .takeWhile { it.isLetterOrDigit() || it == ':' || it == '-' || it == '_' }
            .substringAfter(':')
            .lowercase()
        if (name == tagName) {
            depth += if (isClosing) -1 else 1
            if (depth <= 0) return close + 1
        }
        index = close + 1
    }
    return html.length
}
