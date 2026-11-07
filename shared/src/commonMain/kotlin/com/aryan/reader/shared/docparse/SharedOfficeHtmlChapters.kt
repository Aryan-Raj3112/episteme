package com.aryan.reader.shared.docparse

import com.aryan.reader.shared.reader.decodeEpubEntities

/**
 * Chapters an office_oxide HTML fragment on `h1..h6`, mirroring how
 * [SharedDocxDocumentParser] starts a new section per heading: each heading
 * opens a chapter holding the heading plus the body that follows it, and
 * content before the first heading becomes an untitled leading chapter.
 *
 * The fragment is split as raw text, never re-serialized, so engine output
 * passes through byte-for-byte (data-URI images, `<hr />` breaks, inline
 * spans). A fragment without headings yields one untitled chapter; an
 * unclosed heading simply stays body text rather than failing the open.
 */
internal object SharedOfficeHtmlChapters {

    data class Chapter(
        val title: String?,
        val html: String,
    )

    fun split(fragment: String): List<Chapter> {
        val matches = HEADING_REGEX.findAll(fragment).toList()
        if (matches.isEmpty()) {
            return listOf(Chapter(title = null, html = fragment))
        }
        val chapters = mutableListOf<Chapter>()
        val leading = fragment.substring(0, matches.first().range.first)
        if (leading.isNotBlank()) {
            chapters += Chapter(title = null, html = leading)
        }
        matches.forEachIndexed { index, match ->
            val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: fragment.length
            chapters += Chapter(
                title = headingTitle(match.groupValues[2]),
                html = fragment.substring(match.range.first, bodyEnd),
            )
        }
        return chapters.ifEmpty { listOf(Chapter(title = null, html = fragment)) }
    }

    private fun headingTitle(innerHtml: String): String? =
        TAG_STRIP_REGEX.replace(innerHtml, " ")
            .decodeEpubEntities()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .takeIf(String::isNotBlank)

    private val HEADING_REGEX =
        // `(?is)` inline flags, not RegexOption: DOT_MATCHES_ALL is absent from
        // the common stdlib and breaks compileIosMainKotlinMetadata.
        Regex("""(?is)<h([1-6])\b[^>]*>(.*?)</h\1\s*>""")
    private val TAG_STRIP_REGEX = Regex("""<[^>]+>""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
}
