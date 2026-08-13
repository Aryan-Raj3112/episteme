package com.aryan.reader.shared

data class SharedDocumentMetadata(
    val title: String? = null,
    val author: String? = null,
)

fun sharedDocumentMetadataArchivePath(type: FileType): String? = when (type) {
    FileType.DOCX, FileType.PPTX -> "docProps/core.xml"
    FileType.ODT -> "meta.xml"
    else -> null
}

/**
 * Extracts the Dublin Core fields used by Android's DOCX/ODT/FODT/PPTX metadata worker.
 * Matching by local XML name keeps namespace-prefix differences platform-neutral.
 */
fun parseSharedDocumentXmlMetadata(xml: String): SharedDocumentMetadata {
    var title: String? = null
    var author: String? = null
    SharedDocumentMetadataElementRegex.findAll(xml).forEach { match ->
        val localName = match.groupValues[1].lowercase()
        val value = match.groupValues[2].sharedDocumentPlainText()
        when {
            title == null && localName == "title" -> title = value
            author == null && localName in setOf("creator", "initial-creator") -> author = value
        }
    }
    return SharedDocumentMetadata(title = title, author = author)
}

/** Extracts FB2 title-info metadata with Android-compatible author ordering. */
fun parseSharedFb2Metadata(xml: String): SharedDocumentMetadata {
    val bodyStart = Regex("<(?:[\\w.-]+:)?body\\b", RegexOption.IGNORE_CASE).find(xml)?.range?.first
    val header = bodyStart?.let { xml.substring(0, it) } ?: xml
    val title = SharedFb2TitleRegex.find(header)?.groupValues?.getOrNull(1)?.sharedDocumentPlainText()
    val authors = SharedFb2AuthorRegex.findAll(header).mapNotNull { authorMatch ->
        SharedFb2AuthorPartRegex.findAll(authorMatch.groupValues[1])
            .mapNotNull { it.groupValues[1].sharedDocumentPlainText() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf(String::isNotBlank)
    }.distinct().toList()
    return SharedDocumentMetadata(title = title, author = authors.joinToString(", ").takeIf(String::isNotBlank))
}

private fun String.sharedDocumentPlainText(): String? =
    replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty() }
        .replace(Regex("&#x([0-9a-f]+);", RegexOption.IGNORE_CASE)) {
            it.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
        }
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf(String::isNotBlank)

private val SharedDocumentMetadataElementRegex = Regex(
    """<(?:[\w.-]+:)?(title|creator|initial-creator)\b[^>]*>([\s\S]*?)</(?:[\w.-]+:)?\1\s*>""",
    RegexOption.IGNORE_CASE,
)
private val SharedFb2TitleRegex = Regex(
    """<(?:[\w.-]+:)?book-title\b[^>]*>([\s\S]*?)</(?:[\w.-]+:)?book-title\s*>""",
    RegexOption.IGNORE_CASE,
)
private val SharedFb2AuthorRegex = Regex(
    """<(?:[\w.-]+:)?author\b[^>]*>([\s\S]*?)</(?:[\w.-]+:)?author\s*>""",
    RegexOption.IGNORE_CASE,
)
private val SharedFb2AuthorPartRegex = Regex(
    """<(?:[\w.-]+:)?(?:first-name|middle-name|last-name|nickname)\b[^>]*>([\s\S]*?)</(?:[\w.-]+:)?(?:first-name|middle-name|last-name|nickname)\s*>""",
    RegexOption.IGNORE_CASE,
)
