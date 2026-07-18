package com.aryan.reader.shared.ui

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

internal actual fun openSharedMobileExternalUrl(url: String): Boolean {
    val normalized = if (url.trim().startsWith("//")) "https:${url.trim()}" else url.trim()
    val target = NSURL.URLWithString(normalized) ?: return false
    return UIApplication.sharedApplication.openURL(target)
}
