// OpdsModels.kt
package com.aryan.reader.opds

data class OpdsCatalog(
    val id: String,
    val title: String,
    val url: String,
    val isDefault: Boolean = false,
    val username: String? = null,
    val password: String? = null
)

data class OpdsFacet(
    val title: String,
    val group: String,
    val url: String,
    val isActive: Boolean
)

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String?,
    val searchUrl: String? = null,
    val facets: List<OpdsFacet> = emptyList()
)

data class OpdsAuthor(
    val name: String,
    val url: String?
)

data class OpdsAcquisition(
    val url: String,
    val mimeType: String
) {
    val formatName: String
        get() = when {
            mimeType.contains("epub") -> "EPUB"
            mimeType.contains("pdf") -> "PDF"
            mimeType.contains("mobi") || mimeType.contains("x-mobipocket-ebook") -> "MOBI"
            mimeType.contains("fictionbook") || mimeType.contains("fb2") -> "FB2"
            mimeType.contains("cbz") || mimeType.contains("comicbook") -> "CBZ"
            mimeType.contains("cbr") || mimeType.contains("rar") -> "CBR"
            mimeType.contains("txt") || mimeType.contains("text/plain") -> "TXT"
            else -> mimeType.substringAfterLast("/").uppercase()
        }

    val priority: Int
        get() = when (formatName) {
            "EPUB" -> 5
            "PDF" -> 4
            "MOBI" -> 3
            "FB2" -> 2
            "CBZ" -> 1
            "TXT" -> 0
            else -> -1
        }
}

data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String?,
    val authors: List<OpdsAuthor> = emptyList(),
    val coverUrl: String?,
    val acquisitions: List<OpdsAcquisition> = emptyList(),
    val navigationUrl: String?,
    val publisher: String? = null,
    val published: String? = null,
    val language: String? = null,
    val series: String? = null,
    val seriesIndex: String? = null,
    val categories: List<String> = emptyList()
) {
    val author: String?
        get() = authors.firstOrNull()?.name

    val bestAcquisition: OpdsAcquisition?
        get() = acquisitions.maxByOrNull { it.priority }

    val isAcquisition: Boolean
        get() = acquisitions.isNotEmpty()

    val isNavigation: Boolean
        get() = navigationUrl != null && acquisitions.isEmpty()
}