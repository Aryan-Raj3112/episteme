package com.aryan.reader.opds

data class OpdsCatalog(
    val id: String,
    val title: String,
    val url: String
)

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextUrl: String?,
    val searchUrl: String? = null
)

data class OpdsEntry(
    val id: String,
    val title: String,
    val summary: String?,
    val author: String?,
    val coverUrl: String?,
    val downloadUrl: String?,
    val downloadMimeType: String?,
    val navigationUrl: String?
) {
    val isAcquisition: Boolean
        get() = downloadUrl != null

    val isNavigation: Boolean
        get() = navigationUrl != null && downloadUrl == null
}