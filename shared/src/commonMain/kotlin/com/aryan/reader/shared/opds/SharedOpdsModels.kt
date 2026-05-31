package com.aryan.reader.shared.opds

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
            mimeType.contains("epub", ignoreCase = true) -> "EPUB"
            mimeType.contains("pdf", ignoreCase = true) -> "PDF"
            mimeType.contains("presentationml.presentation", ignoreCase = true) ||
                mimeType.contains("pptx", ignoreCase = true) -> "PPTX"
            mimeType.contains("markdown", ignoreCase = true) ||
                mimeType.contains("text/x-markdown", ignoreCase = true) -> "MD"
            mimeType.contains("html", ignoreCase = true) ||
                mimeType.contains("xhtml", ignoreCase = true) -> "HTML"
            mimeType.contains("mobi", ignoreCase = true) ||
                mimeType.contains("x-mobipocket-ebook", ignoreCase = true) -> "MOBI"
            mimeType.contains("fictionbook", ignoreCase = true) ||
                mimeType.contains("fb2", ignoreCase = true) -> "FB2"
            mimeType.contains("cbt", ignoreCase = true) ||
                mimeType.contains("comicbook+tar", ignoreCase = true) ||
                mimeType.contains("x-tar", ignoreCase = true) ||
                mimeType.equals("application/tar", ignoreCase = true) -> "CBT"
            mimeType.contains("cbr", ignoreCase = true) ||
                mimeType.contains("comicbook-rar", ignoreCase = true) ||
                mimeType.contains("rar", ignoreCase = true) -> "CBR"
            mimeType.contains("cb7", ignoreCase = true) ||
                mimeType.contains("7z", ignoreCase = true) -> "CB7"
            mimeType.contains("cbz", ignoreCase = true) ||
                mimeType.contains("comicbook+zip", ignoreCase = true) ||
                mimeType.contains("comicbook", ignoreCase = true) -> "CBZ"
            mimeType.contains("txt", ignoreCase = true) ||
                mimeType.contains("text/plain", ignoreCase = true) -> "TXT"
            else -> mimeType.substringAfterLast("/").uppercase()
        }

    val priority: Int
        get() = when (formatName) {
            "EPUB" -> 5
            "PDF" -> 4
            "PPTX" -> 4
            "MOBI" -> 3
            "FB2", "MD", "HTML" -> 2
            "CBZ", "CBR", "CB7", "CBT" -> 1
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
    val categories: List<String> = emptyList(),
    val pseCount: Int? = null,
    val pseUrlTemplate: String? = null
) {
    val author: String?
        get() = authors.firstOrNull()?.name

    val bestAcquisition: OpdsAcquisition?
        get() = acquisitions.maxByOrNull { it.priority }

    val isAcquisition: Boolean
        get() = acquisitions.isNotEmpty()

    val isNavigation: Boolean
        get() = navigationUrl != null && acquisitions.isEmpty()

    val isStreamable: Boolean
        get() = pseUrlTemplate != null && pseCount != null && pseCount > 0
}

data class SharedOpdsDownloadState(
    val isDownloading: Boolean,
    val progress: Float? = null
)

data class SharedOpdsScreenState(
    val catalogs: List<OpdsCatalog> = emptyList(),
    val currentCatalog: OpdsCatalog? = null,
    val currentFeed: OpdsFeed? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isViewingCatalog: Boolean = false,
    val searchUrlTemplate: String? = null,
    val downloadingState: Map<String, SharedOpdsDownloadState> = emptyMap()
)

data class OpdsStreamReference(
    val id: String,
    val count: Int,
    val urlTemplate: String,
    val catalogId: String? = null
)

interface SharedOpdsRepository {
    fun loadCatalogs(): List<OpdsCatalog>
    fun saveCatalogs(catalogs: List<OpdsCatalog>)
    suspend fun fetchFeed(url: String, username: String? = null, password: String? = null): Result<OpdsFeed>
    suspend fun getSearchTemplate(openSearchUrl: String, username: String? = null, password: String? = null): String?
}
