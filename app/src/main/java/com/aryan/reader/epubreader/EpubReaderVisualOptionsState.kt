package com.aryan.reader.epubreader

import com.aryan.reader.shared.PageInfoMode
import com.aryan.reader.shared.shouldShowEpubPageInfoBar as shouldShowSharedEpubPageInfoBar
import com.aryan.reader.shared.shouldReserveEpubPageInfoBarSpace as shouldReserveSharedEpubPageInfoBarSpace

internal fun shouldShowEpubPageInfoBar(
    pageInfoMode: PageInfoMode,
    showReaderChrome: Boolean
): Boolean = shouldShowSharedEpubPageInfoBar(pageInfoMode, showReaderChrome)

internal fun shouldReserveEpubPageInfoBarSpace(
    pageInfoMode: PageInfoMode,
    showReaderChrome: Boolean,
    isNativeVerticalMode: Boolean
): Boolean = shouldReserveSharedEpubPageInfoBarSpace(pageInfoMode, showReaderChrome, isNativeVerticalMode)
