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
