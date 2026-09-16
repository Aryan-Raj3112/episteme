package com.aryan.reader.shared.reader

internal data class SharedMobiTocPoint(
    val title: String,
    val bytePosition: Int,
)

internal data class SharedMobiSection(
    val title: String,
    val html: String,
)

internal fun splitMobiHtml(
    html: String,
    toc: List<SharedMobiTocPoint>,
    fallbackTitle: String,
): List<SharedMobiSection> {
    val bytes = html.encodeToByteArray()
    val points = toc
        .filter { it.bytePosition in 0 until bytes.size }
        .distinctBy { it.bytePosition }
        .sortedBy { it.bytePosition }
    if (points.isNotEmpty()) {
        // TOC positions are raw file offsets that can land inside a tag
        // (e.g. `<div id="ch9" aid="2RHM2">` split at `9` leaks
        // `9" aid="2RHM2">` as visible text at the next chapter start).
        // Align each split forward to the next tag boundary before slicing.
        val aligned = points
            .map { it.copy(bytePosition = bytes.mobiTagBoundaryAfter(it.bytePosition)) }
            .filter { it.bytePosition in 0 until bytes.size }
            .distinctBy { it.bytePosition }
            .sortedBy { it.bytePosition }
        if (aligned.isEmpty()) return listOf(SharedMobiSection(fallbackTitle, html))
        return aligned.mapIndexedNotNull { index, point ->
            val end = aligned.getOrNull(index + 1)?.bytePosition ?: bytes.size
            if (end <= point.bytePosition) return@mapIndexedNotNull null
            val sectionHtml = bytes.copyOfRange(point.bytePosition, end)
                .decodeToString()
                .dropLeadingMobiTagTail()
                .dropTrailingMobiPartialTag()
            if (sectionHtml.isBlank()) return@mapIndexedNotNull null
            SharedMobiSection(
                title = point.title.ifBlank { "Chapter ${index + 1}" },
                html = sectionHtml,
            )
        }.takeIf { it.isNotEmpty() } ?: listOf(SharedMobiSection(fallbackTitle, html))
    }

    val pageBreak = Regex("""<mbp:pagebreak\b[^>]*?/?>""", RegexOption.IGNORE_CASE)
    val sections = pageBreak.split(html).filter { it.isNotBlank() }
    return if (sections.size > 1) {
        sections.mapIndexed { index, section ->
            SharedMobiSection("Chapter ${index + 1}", section)
        }
    } else {
        listOf(SharedMobiSection(fallbackTitle, html))
    }
}

internal fun rewriteMobiResourceReferences(
    html: String,
    imageDataUris: List<String>,
    cssDataUris: Map<Int, String>,
): String {
    var rewritten = Regex(
        """kindle:flow:(\d+)(?:\?[^"' >]*)?""",
        RegexOption.IGNORE_CASE,
    ).replace(html) { match ->
        cssDataUris[match.groupValues[1].toIntOrNull()] ?: match.value
    }
    rewritten = Regex(
        """kindle:embed:(\d+)(?:\?[^"' >]*)?""",
        RegexOption.IGNORE_CASE,
    ).replace(rewritten) { match ->
        imageDataUris.getOrNull(match.groupValues[1].toIntOrNull()?.minus(1) ?: -1) ?: match.value
    }
    return Regex(
        """<img\b([^>]*?)\srecindex=["']?(\d+)["']?([^>]*)>""",
        RegexOption.IGNORE_CASE,
    ).replace(rewritten) { match ->
        val source = imageDataUris.getOrNull(match.groupValues[2].toIntOrNull()?.minus(1) ?: -1)
            ?: return@replace match.value
        val attributes = match.groupValues[1] + match.groupValues[3]
        val withoutSource = attributes.replace(
            Regex("""\s+src\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""", RegexOption.IGNORE_CASE),
            "",
        )
        """<img$withoutSource src="$source">"""
    }
}

/**
 * Advances a raw byte offset forward past the end of any tag it lands inside,
 * so MOBI TOC splits never start a chapter mid-`<...>`. Quote-aware (ASCII
 * `"`, `'`, `<`, `>` are single-byte in UTF-8, so byte scanning stays correct
 * for tag boundaries even around multi-byte text).
 */
private fun ByteArray.mobiTagBoundaryAfter(offset: Int): Int {
    if (offset <= 0) return 0
    if (offset >= size) return size
    var inQuote: Byte = 0
    var inTag = false
    var cursor = 0
    while (cursor < offset) {
        val byte = this[cursor]
        if (inQuote != 0.toByte()) {
            if (byte == inQuote) inQuote = 0
        } else {
            when (byte) {
                '"'.code.toByte(), '\''.code.toByte() -> if (inTag) inQuote = byte
                '<'.code.toByte() -> inTag = true
                '>'.code.toByte() -> inTag = false
            }
        }
        cursor++
    }
    if (!inTag) return offset
    while (cursor < size) {
        val byte = this[cursor]
        if (inQuote != 0.toByte()) {
            if (byte == inQuote) inQuote = 0
        } else {
            when (byte) {
                '"'.code.toByte(), '\''.code.toByte() -> inQuote = byte
                '>'.code.toByte() -> return cursor + 1
            }
        }
        cursor++
    }
    return size
}

/**
 * Safety net for any residual tag tail (e.g. `9" aid="2RHM2">Chapter...`)
 * when a split still lands mid-tag. Only strips when the prefix before the
 * first `>` looks like attribute text (`=` plus a quote), so legitimate text
 * starting with `>` is preserved.
 */
private fun String.dropLeadingMobiTagTail(): String {
    val trimmed = trimStart()
    val close = trimmed.indexOf('>')
    if (close < 0) return trimmed
    val open = trimmed.indexOf('<')
    if (open >= 0 && open < close) return trimmed
    val prefix = trimmed.substring(0, close)
    if ('=' !in prefix) return trimmed
    if ('"' !in prefix && '\'' !in prefix) return trimmed
    return trimmed.substring(close + 1).trimStart()
}

/**
 * Drops a trailing unterminated tag (e.g. chapter ending with `<div id="ch`)
 * that would otherwise leak as visible text, since tag-stripping regexes
 * require a closing `>`. Conservative: only when the last `<` looks like a
 * tag open (letter, `/`, `!`, or `?` follows).
 */
private fun String.dropTrailingMobiPartialTag(): String {
    val open = lastIndexOf('<')
    if (open < 0) return this
    if (indexOf('>', open) >= 0) return this
    val next = getOrNull(open + 1) ?: return this
    if (!next.isLetter() && next != '/' && next != '!' && next != '?') return this
    return substring(0, open).trimEnd()
}
