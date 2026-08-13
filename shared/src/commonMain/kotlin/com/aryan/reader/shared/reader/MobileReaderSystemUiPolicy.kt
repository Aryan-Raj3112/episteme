package com.aryan.reader.shared.reader

import com.aryan.reader.shared.SystemUiMode

data class MobileReaderSystemBarsVisibility(
    val statusBarsVisible: Boolean,
    val navigationBarsVisible: Boolean,
)

fun mobileEpubSystemBarsVisibility(
    mode: SystemUiMode,
    readerChromeVisible: Boolean,
): MobileReaderSystemBarsVisibility = when (mode) {
    SystemUiMode.DEFAULT -> MobileReaderSystemBarsVisibility(
        statusBarsVisible = true,
        navigationBarsVisible = readerChromeVisible,
    )
    SystemUiMode.SYNC -> MobileReaderSystemBarsVisibility(readerChromeVisible, readerChromeVisible)
    SystemUiMode.HIDDEN -> MobileReaderSystemBarsVisibility(false, false)
}

fun mobilePdfSystemBarsVisibility(
    mode: SystemUiMode,
    standardReaderChromeVisible: Boolean,
): MobileReaderSystemBarsVisibility = when (mode) {
    SystemUiMode.DEFAULT -> MobileReaderSystemBarsVisibility(true, true)
    SystemUiMode.SYNC -> MobileReaderSystemBarsVisibility(
        standardReaderChromeVisible,
        standardReaderChromeVisible,
    )
    SystemUiMode.HIDDEN -> MobileReaderSystemBarsVisibility(false, false)
}

enum class MobilePdfDocumentPresentation {
    LOADING,
    ERROR,
    READY,
    EMPTY,
}

fun selectMobilePdfDocumentPresentation(
    loading: Boolean,
    errorPresent: Boolean,
    documentPresent: Boolean,
    totalPages: Int,
): MobilePdfDocumentPresentation = when {
    loading -> MobilePdfDocumentPresentation.LOADING
    errorPresent -> MobilePdfDocumentPresentation.ERROR
    documentPresent && totalPages > 0 -> MobilePdfDocumentPresentation.READY
    else -> MobilePdfDocumentPresentation.EMPTY
}
