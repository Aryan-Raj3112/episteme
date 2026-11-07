package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
 * What paints the strip behind a visible system bar. Reader bars are transparent and
 * edge-to-edge, so the icons have to contrast with the surface actually drawn there.
 */
enum class MobileReaderSystemBarBackdrop {
    /** The reader toolbar surface (`MaterialTheme.colorScheme.surface`). */
    CHROME,

    /** The reader page/render background. */
    READER_CONTENT,

    /** The reader paints no chrome there; leave the platform's existing appearance alone. */
    PLATFORM_DEFAULT,
}

data class MobileReaderSystemBarBackdrops(
    val statusBar: MobileReaderSystemBarBackdrop,
    val navigationBar: MobileReaderSystemBarBackdrop,
)

/**
 * Resolves the icon appearance for each system bar. A `null` flag means the reader has no
 * opinion and the host's previous/default appearance must stay in place.
 */
data class MobileReaderSystemBarIconFlags(
    val lightStatusBarAppearance: Boolean?,
    val lightNavigationBarAppearance: Boolean?,
)

/**
 * The EPUB top bar's Surface paints the status-bar strip whenever chrome is up, and the
 * reader always paints the navigation-bar scrims with that same surface color; with chrome
 * hidden the status strip shows the page.
 */
fun mobileEpubSystemBarBackdrops(
    readerChromeVisible: Boolean,
): MobileReaderSystemBarBackdrops = MobileReaderSystemBarBackdrops(
    statusBar = if (readerChromeVisible) {
        MobileReaderSystemBarBackdrop.CHROME
    } else {
        MobileReaderSystemBarBackdrop.READER_CONTENT
    },
    navigationBar = MobileReaderSystemBarBackdrop.CHROME,
)

/**
 * The PDF top and bottom bars each paint their own system-bar strip. A bar that is not
 * rendered leaves the default appearance untouched, because the strip then shows the
 * document and there is no chrome color to match.
 */
fun mobilePdfSystemBarBackdrops(
    topBarVisible: Boolean,
    bottomBarVisible: Boolean,
): MobileReaderSystemBarBackdrops = MobileReaderSystemBarBackdrops(
    statusBar = if (topBarVisible) {
        MobileReaderSystemBarBackdrop.CHROME
    } else {
        MobileReaderSystemBarBackdrop.PLATFORM_DEFAULT
    },
    navigationBar = if (bottomBarVisible) {
        MobileReaderSystemBarBackdrop.CHROME
    } else {
        MobileReaderSystemBarBackdrop.PLATFORM_DEFAULT
    },
)

/**
 * Computes the `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` values:
 * "light appearance" means dark icons, so it is the inverse of the backdrop's darkness.
 * This is what keeps the system icons readable when the app theme and the reader theme
 * disagree (for example a dark app bar over a light reader page).
 */
fun mobileReaderSystemBarIconFlags(
    backdrops: MobileReaderSystemBarBackdrops,
    chromeBackground: Color,
    contentBackground: Color,
): MobileReaderSystemBarIconFlags {
    fun appearance(backdrop: MobileReaderSystemBarBackdrop): Boolean? = when (backdrop) {
        MobileReaderSystemBarBackdrop.CHROME -> !chromeBackground.isDarkReaderChrome()
        MobileReaderSystemBarBackdrop.READER_CONTENT -> !contentBackground.isDarkReaderChrome()
        MobileReaderSystemBarBackdrop.PLATFORM_DEFAULT -> null
    }
    return MobileReaderSystemBarIconFlags(
        lightStatusBarAppearance = appearance(backdrops.statusBar),
        lightNavigationBarAppearance = appearance(backdrops.navigationBar),
    )
}

private fun Color.isDarkReaderChrome(): Boolean = luminance() < 0.5f

/**
 * Whether vertical PDF content must start below the status bar.
 *
 * Only Always Show reserves space: its status bar never hides, so the first
 * page is anchored below it. In Sync with Menus the content stays edge-to-edge
 * under the status bar, so showing the menu with the status bar does not push
 * the page down. Split panes already sit below the workspace toolbar (which
 * pads the status bar), so they must not pad again. Pagination centers pages
 * and is unchanged.
 */
fun shouldPadPdfVerticalContentBelowStatusBar(
    mode: SystemUiMode,
    standardReaderChromeVisible: Boolean,
    isVerticalMode: Boolean,
    isSplitPane: Boolean,
): Boolean {
    if (!isVerticalMode || isSplitPane) return false
    return mode == SystemUiMode.DEFAULT
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
