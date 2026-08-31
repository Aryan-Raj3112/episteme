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

/**
 * Per-bar chrome visibility. The reader used to hide its top and bottom bars
 * together; these flags let each bar be hidden independently (Visual Options
 * "Show top toolbar" / "Show bottom toolbar") while tap-to-toggle still
 * reveals whatever bars are enabled.
 */
data class MobileReaderBarVisibility(
    val topBarVisible: Boolean,
    val bottomBarVisible: Boolean,
)

/**
 * Resolves which reader bars are rendered. A bar is visible when the reader
 * chrome is toggled on, edit mode is not active, and the bar itself has not
 * been hidden by the user.
 */
fun selectMobileReaderBarVisibility(
    readerChromeVisible: Boolean,
    isEditMode: Boolean,
    isTopBarEnabled: Boolean,
    isBottomBarEnabled: Boolean,
): MobileReaderBarVisibility {
    val chromeVisible = readerChromeVisible && !isEditMode
    return MobileReaderBarVisibility(
        topBarVisible = chromeVisible && isTopBarEnabled,
        bottomBarVisible = chromeVisible && isBottomBarEnabled,
    )
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
