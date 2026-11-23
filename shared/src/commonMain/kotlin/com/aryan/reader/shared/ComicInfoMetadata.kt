package com.aryan.reader.shared

/**
 * Metadata read from the `ComicInfo.xml` file a comic archive carries next to
 * its pages (the Anansi/ComicRack schema used by virtually every comic tool).
 *
 * [title] comes from `<Title>` only. When an archive has no `<Title>` this
 * stays null and every caller keeps the filename-derived title the library
 * already shows, so a title is never invented out of series metadata.
 */
data class SharedComicInfoMetadata(
    val title: String? = null,
    val author: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Double? = null,
    val description: String? = null,
)

/**
 * Whether an archive entry is the ComicInfo sidecar.
 *
 * It conventionally sits at the archive root, but some tools nest it beside the
 * pages and casing varies, so match the leaf name case-insensitively.
 */
fun isComicInfoArchivePath(path: String): Boolean =
    path.substringAfterLast('/').substringAfterLast('\\').equals(ComicInfoEntryName, ignoreCase = true)

/**
 * Extracts the fields the library models can hold. Regex-based on purpose, to
 * match the other shared metadata parsers and to tolerate the malformed XML
 * real-world ComicInfo files contain.
 */
fun parseSharedComicInfoXml(xml: String): SharedComicInfoMetadata {
    return SharedComicInfoMetadata(
        title = xml.comicInfoElement("Title"),
        // `Writer` is the ComicInfo field that maps to an author. Penciller is
        // the documented fallback for art-only books that leave Writer empty.
        author = xml.comicInfoElement("Writer") ?: xml.comicInfoElement("Penciller"),
        seriesName = xml.comicInfoElement("Series"),
        seriesIndex = xml.comicInfoElement("Number").comicInfoSeriesIndex(),
        description = xml.comicInfoElement("Summary"),
    )
}

private const val ComicInfoEntryName = "ComicInfo.xml"

private fun String.comicInfoElement(name: String): String? =
    Regex(
        """<(?:[\w.-]+:)?$name\b[^>]*>([\s\S]*?)</(?:[\w.-]+:)?$name\s*>""",
        RegexOption.IGNORE_CASE,
    ).find(this)?.groupValues?.getOrNull(1)?.sharedXmlText()

/**
 * `<Number>` is free text in practice: `3`, `#3`, `3.5`, `3 (2021)`, `Annual 2`.
 * Only a pure number (optionally `#`-prefixed) becomes a series index; guessing
 * at the rest (`3 (2021)` -> 3, `1-2` -> 1) would put books in the wrong place
 * in a series, which is worse than leaving the index empty.
 */
private fun String?.comicInfoSeriesIndex(): Double? {
    val normalized = this?.trim()?.removePrefix("#")?.trim() ?: return null
    if (!ComicInfoSeriesIndexRegex.matches(normalized)) return null
    return normalized.toDoubleOrNull()
}

private val ComicInfoSeriesIndexRegex = Regex("""\d+(?:\.\d+)?""")
