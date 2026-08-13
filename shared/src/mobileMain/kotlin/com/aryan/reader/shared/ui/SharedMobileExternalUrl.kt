package com.aryan.reader.shared.ui

/**
 * Opens [url] in the platform's default handler (browser on iOS via
 * `UIApplication.openURL`). Returns `true` if the platform attempted to open
 * the URL, `false` if the URL was malformed or unsupported.
 */
internal expect fun openSharedMobileExternalUrl(url: String): Boolean
