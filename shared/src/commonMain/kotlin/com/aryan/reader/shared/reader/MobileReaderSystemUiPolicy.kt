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
 * Whether vertical PDF content must start below the status bar.
 *
 * In vertical mode the first page is anchored to the viewport top, so when the
 * status bar is visible the page would otherwise draw underneath it. Split
 * panes already sit below the workspace toolbar (which pads the status bar),
 * so they must not pad again. Pagination centers pages and is unchanged.
 */
fun shouldPadPdfVerticalContentBelowStatusBar(
    mode: SystemUiMode,
    standardReaderChromeVisible: Boolean,
    isVerticalMode: Boolean,
    isSplitPane: Boolean,
): Boolean {
    if (!isVerticalMode || isSplitPane) return false
    return mobilePdfSystemBarsVisibility(mode, standardReaderChromeVisible).statusBarsVisible
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
