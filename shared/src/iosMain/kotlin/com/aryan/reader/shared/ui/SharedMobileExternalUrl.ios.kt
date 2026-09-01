package com.aryan.reader.shared.ui

import com.aryan.reader.shared.pdf.pdfLinkLog
import com.aryan.reader.shared.normalizeReaderHref
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

internal actual fun openSharedMobileExternalUrl(url: String): Boolean {
    val raw = url.trim()
    val normalized = normalizeReaderHref(raw)
    val target = NSURL.URLWithString(normalized)
    if (target == null) {
        pdfLinkLog { "external-open rejected raw=$raw normalized=$normalized reason=invalid-url" }
        return false
    }
    val application = UIApplication.sharedApplication
    val canOpen = application.canOpenURL(target)
    pdfLinkLog { "external-open-request raw=$raw normalized=$normalized canOpen=$canOpen" }
    if (!canOpen) return false
    application.openURL(
        url = target,
        options = emptyMap<Any?, Any>(),
        completionHandler = { opened ->
            pdfLinkLog { "external-open-completion normalized=$normalized opened=$opened" }
        }
    )
    return true
}
