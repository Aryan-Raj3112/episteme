package com.aryan.reader.epubreader

import com.aryan.reader.shared.PageInfoMode

internal fun shouldShowEpubPageInfoBar(
    pageInfoMode: PageInfoMode,
    showReaderChrome: Boolean
): Boolean {
    return when (pageInfoMode) {
        PageInfoMode.DEFAULT -> true
        PageInfoMode.SYNC -> showReaderChrome
        PageInfoMode.HIDDEN -> false
    }
}

internal fun shouldReserveEpubPageInfoBarSpace(
    pageInfoMode: PageInfoMode,
    showReaderChrome: Boolean,
    isNativeVerticalMode: Boolean
): Boolean {
    if (isNativeVerticalMode) return false
    return shouldShowEpubPageInfoBar(
        pageInfoMode = pageInfoMode,
        showReaderChrome = showReaderChrome
    )
}
