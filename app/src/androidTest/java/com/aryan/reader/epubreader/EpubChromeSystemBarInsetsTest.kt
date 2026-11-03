package com.aryan.reader.epubreader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.RenderMode
import com.aryan.reader.shared.ReaderSearchState as SearchState
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.ui.SharedMobileEpubBottomBar
import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import com.aryan.reader.shared.ui.SharedMobileEpubTopBar
import com.aryan.reader.shared.ui.SharedReaderEpubBottomBarHeight
import com.aryan.reader.shared.ui.SharedReaderEpubTopBarHeight
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for the EPUB chrome's system-bar strips.
 *
 * The top bar used to receive the status-bar inset as padding *around* its Surface, so the
 * 55.dp surface started below the inset and the strip above it showed the reader background (the
 * PDF reader's bar painted it). The same held for Android's bottom bar: the inset lived outside
 * the Surface and the navigation-bar scrim had to hide the seam. These tests pin the fix: the bar's
 * own bounds grow by the inset and reach the screen edge, which only happens when the inset is
 * painted by the Surface.
 */
@RunWith(AndroidJUnit4::class)
class EpubChromeSystemBarInsetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val inset = 24.dp

    @Test
    fun topBarSurfacePaintsTheStatusBarStripItself() {
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        SharedMobileEpubTopBar(
                            title = "Book",
                            isBookmarked = false,
                            topTools = emptyList(),
                            showMore = false,
                            onShowMoreChange = {},
                            onBack = {},
                            onTheme = {},
                            onFormat = {},
                            onSearch = {},
                            onBookmark = {},
                            onVisualOptions = {},
                            onBrightness = {},
                            onOpenToc = {},
                            onOpenSlider = {},
                            onFileInfo = {},
                            onCustomizeTools = {},
                            onScreenOrientation = {},
                            onTtsSettings = {},
                            onTtsReplacements = {},
                            onBookReplacements = {},
                            onOpenDictionarySettings = {},
                            readingMode = ReaderReadingMode.VERTICAL,
                            rightToLeftPagination = false,
                            useNativeVerticalRenderer = false,
                            tapToNavigateEnabled = false,
                            pageTurnAnimationEnabled = false,
                            onReadingModeChange = {},
                            onUseNativeVerticalRendererChange = {},
                            onRightToLeftPaginationChange = {},
                            onTapToNavigateChange = {},
                            onPageTurnAnimationChange = {},
                            toolbarPreferences = ReaderToolbarPreferences(),
                            localTtsState = SharedMobileEpubLocalTtsState.IDLE,
                            onLocalTtsToggle = {},
                            onLocalTtsStop = {},
                            keepScreenOn = false,
                            onKeepScreenOnChange = {},
                            autoScroll = false,
                            onAutoScrollChange = {},
                            topInset = inset,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        val bounds = composeTestRule
            .onNodeWithTag("EpubTopBar")
            .getUnclippedBoundsInRoot()
        assertThat(bounds.top.value).isWithin(0.5f).of(0f)
        assertThat((bounds.bottom - bounds.top).value)
            .isWithin(0.5f)
            .of((SharedReaderEpubTopBarHeight + inset).value)
    }

    @Test
    fun androidTopBarAdapterLiftsOutOfTheInsetChromeLayer() {
        val hostInset = 20.dp
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize()) {
                        // Mimics EpubReaderHost: the chrome layer is inset by the status bar so its
                        // controls clear the system bar, and the bar is expected to lift itself out
                        // of that inset so its surface still covers the strip.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(top = hostInset)
                        ) {
                            EpubReaderTopBar(
                                isVisible = true,
                                searchState = remember {
                                    SearchState(scope) { emptyList() }
                                },
                                bookTitle = "Book",
                                currentRenderMode = RenderMode.PAGINATED,
                                isBookmarked = false,
                                isTtsActive = false,
                                isSliderActive = false,
                                tapToNavigateEnabled = false,
                                volumeScrollEnabled = false,
                                isPageTurnAnimationEnabled = false,
                                isRightToLeftPagination = false,
                                useNativeVerticalRenderer = false,
                                onNavigateBack = {},
                                isKeepScreenOn = false,
                                onToggleKeepScreenOn = {},
                                onCloseSearch = {},
                                onChangeRenderMode = {},
                                onUseNativeVerticalRendererChange = {},
                                onToggleBookmark = {},
                                onToggleTapToNavigate = {},
                                onToggleVolumeScroll = {},
                                onTogglePageTurnAnimation = {},
                                onSetRightToLeftPagination = {},
                                onStartAutoScroll = {},
                                onOpenTtsSettings = {},
                                onOpenTtsReplacements = {},
                                onOpenBookReplacements = {},
                                onOpenDictionarySettings = {},
                                onOpenThemeSettings = {},
                                onOpenBrightness = {},
                                onOpenVisualOptions = {},
                                onOpenScreenOrientation = {},
                                onOpenSlider = {},
                                onOpenDrawer = {},
                                onToggleFormat = {},
                                onToggleSearch = {},
                                onOpenAiHub = {},
                                onToggleTts = {},
                                onOpenFileInfo = {},
                                searchFocusRequester = remember { FocusRequester() },
                                hiddenTools = emptySet(),
                                toolOrder = emptyList(),
                                bottomTools = emptySet(),
                                onCustomizeTools = {},
                                topInset = inset,
                                hostTopInset = hostInset,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        val bounds = composeTestRule
            .onNodeWithTag("EpubTopBar")
            .getUnclippedBoundsInRoot()
        assertThat(bounds.top.value).isWithin(0.5f).of(0f)
        assertThat((bounds.bottom - bounds.top).value)
            .isWithin(0.5f)
            .of((SharedReaderEpubTopBarHeight + inset).value)
    }

    @Test
    fun bottomBarSurfacePaintsTheNavigationBarStripItself() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag(HOST_TAG)) {
                    SharedMobileEpubBottomBar(
                        tools = listOf(ReaderTool.TOC, ReaderTool.SEARCH),
                        isBookmarked = false,
                        onToc = {},
                        onFormat = {},
                        onSearch = {},
                        onTheme = {},
                        onBookmark = {},
                        onVisualOptions = {},
                        onOpenSlider = {},
                        onDictionary = {},
                        localTtsState = SharedMobileEpubLocalTtsState.IDLE,
                        onLocalTtsToggle = {},
                        bottomInset = inset,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val hostBounds = composeTestRule
            .onNodeWithTag(HOST_TAG)
            .getUnclippedBoundsInRoot()
        val bounds = composeTestRule
            .onNodeWithTag("EpubBottomBar")
            .getUnclippedBoundsInRoot()
        assertThat(bounds.bottom.value).isWithin(0.5f).of(hostBounds.bottom.value)
        assertThat((bounds.bottom - bounds.top).value)
            .isWithin(0.5f)
            .of((SharedReaderEpubBottomBarHeight + inset).value)
    }

    private companion object {
        const val HOST_TAG = "EpubChromeHost"
    }
}
