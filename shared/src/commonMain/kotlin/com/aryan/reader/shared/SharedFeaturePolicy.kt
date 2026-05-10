package com.aryan.reader.shared

data class SharedFeaturePolicy(
    val networkAccess: Boolean = true,
    val opdsCatalogs: Boolean = networkAccess,
    val aiAndCloud: Boolean = networkAccess,
    val externalLookup: Boolean = networkAccess,
    val projectLinks: Boolean = networkAccess,
    val googleFontsDownload: Boolean = networkAccess
) {
    companion object {
        val Standard = SharedFeaturePolicy()
        val OssOffline = SharedFeaturePolicy(
            networkAccess = false,
            opdsCatalogs = false,
            aiAndCloud = false,
            externalLookup = false,
            projectLinks = false,
            googleFontsDownload = false
        )
    }
}
