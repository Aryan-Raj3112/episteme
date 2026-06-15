package com.aryan.reader.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.FileType
import com.aryan.reader.R
import com.aryan.reader.SearchState
import com.aryan.reader.epubreader.SystemUiMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTopBarLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun topToolbarActionsArePinnedToTrailingSide() {
        lateinit var themeDescription: String

        composeTestRule.setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            themeDescription = context.getString(R.string.tooltip_theme_desc)

            MaterialTheme {
                Box(
                    modifier = Modifier
                        .width(420.dp)
                        .testTag("PdfTopBarHost")
                ) {
                    PdfTopBar(
                        showStandardBars = true,
                        systemUiMode = SystemUiMode.HIDDEN,
                        statusBarHeightDp = 0.dp,
                        searchState = remember { SearchState(scope) { emptyList() } },
                        focusRequester = remember { FocusRequester() },
                        onCloseSearch = {},
                        isLoadingDocument = false,
                        errorMessage = null,
                        currentPageForDisplay = 0,
                        currentPageLabel = "1 / 10",
                        totalPages = 10,
                        pagerStatePageCount = 10,
                        hiddenTools = emptySet(),
                        toolOrder = listOf(PdfReaderTool.THEME, PdfReaderTool.SEARCH),
                        bottomTools = emptySet(),
                        isScrollLocked = false,
                        isEditMode = false,
                        displayMode = DisplayMode.VERTICAL_SCROLL,
                        isRightToLeftPagination = false,
                        isKeepScreenOn = false,
                        isTtsSessionActive = false,
                        isSliderActive = false,
                        isBookmarked = false,
                        canDeletePage = false,
                        isReflowingThisBook = false,
                        hasReflowFile = false,
                        isPdfDocumentLoaded = true,
                        canPrintDocument = true,
                        isTabsEnabled = false,
                        openTabs = emptyList(),
                        activeTabBookId = null,
                        usePdfFileNameAsDisplayName = false,
                        effectiveFileType = FileType.PDF,
                        onNavigateBack = {},
                        onShowThemePanel = {},
                        onShowBrightnessControl = {},
                        onToggleScrollLock = {},
                        onShowDictionarySettings = {},
                        onShowPenPlayground = {},
                        onImportSvg = {},
                        onShowCustomizeTools = {},
                        onShowOcrLanguage = {},
                        onShowVisualOptions = {},
                        onShowScreenOrientation = {},
                        onShowSlider = {},
                        onShowToc = {},
                        onSearchClick = {},
                        onToggleHighlights = {},
                        onShowAiHub = {},
                        onToggleEditMode = {},
                        onToggleTts = {},
                        isTtsPlayingOrLoading = false,
                        showAllTextHighlights = false,
                        isHighlightingLoading = false,
                        tapToNavigateEnabled = false,
                        onToggleTapToNavigate = {},
                        onChangeDisplayMode = {},
                        onSetRightToLeftPagination = {},
                        onToggleKeepScreenOn = {},
                        onStartAutoScroll = {},
                        onShowTtsSettings = {},
                        onShowTtsReplacements = {},
                        onToggleBookmark = {},
                        onShowFileInfo = {},
                        onInsertPage = {},
                        onDeletePage = {},
                        onReflowAction = {},
                        onShare = {},
                        onSaveCopy = {},
                        onPrint = {},
                        onTabClick = {},
                        onTabClose = {},
                        onNewTabClick = {},
                        onGenerateDemoAnnotations = {},
                        includeDebugActions = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val themeButtonBounds = composeTestRule
            .onNodeWithContentDescription(themeDescription)
            .getUnclippedBoundsInRoot()
        val hostBounds = composeTestRule
            .onNodeWithTag("PdfTopBarHost")
            .getUnclippedBoundsInRoot()

        assertThat(themeButtonBounds.left.value)
            .isGreaterThan(hostBounds.left.value + hostBounds.width.value * 0.58f)
    }
}
