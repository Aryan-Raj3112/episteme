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

data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String?,
    val author: String?,
    val coverUrl: String?,
    val downloadUrl: String?,
    val downloadMimeType: String?,
    val navigationUrl: String?,
    val publisher: String? = null,
    val published: String? = null,
    val language: String? = null,
    val series: String? = null,
    val seriesIndex: String? = null,
    val categories: List<String> = emptyList()
) {
    val isAcquisition: Boolean
        get() = downloadUrl != null

    val isNavigation: Boolean
        get() = navigationUrl != null && downloadUrl == null
}