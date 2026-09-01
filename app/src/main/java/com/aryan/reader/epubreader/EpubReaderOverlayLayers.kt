// EpubReaderOverlayLayers.kt
//
// Overlay layers extracted from EpubReaderHost's scaffold content closure.
// The original monolithic closure compiled into a single ART-hostile method
// (tens of thousands of bytecode units with hundreds of registers) that some
// verifiers reject at class-load time. Keeping each layer in its own file and
// composable bounds generated method sizes.
package com.aryan.reader.epubreader

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.copyPlainTextToClipboard
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.paginatedreader.Locator
import com.aryan.reader.paginatedreader.nativeVerticalProgressForCompatPage
import com.aryan.reader.ReaderSliderChromeColors
import com.aryan.reader.shared.PageInfoPosition
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.ReaderMotionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun EpubReaderBusyScrim(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .clickable(enabled = true) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
internal fun EpubReaderTocNavigationOverlay(
    isVisible: Boolean,
    motionPolicy: ReaderMotionPolicy
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = if (motionPolicy.reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn()
        },
        exit = if (motionPolicy.reduceMotion) {
            ExitTransition.None
        } else {
            fadeOut()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                .clickable(enabled = true) { },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.navigating_to_chapter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
internal fun EpubHighlightNoteEditorSheet(
    navigation: EpubReaderNavigationState,
    userHighlights: MutableList<UserHighlight>,
    effectiveBg: Color,
    effectiveText: Color,
    activeHighlightPalette: List<Int>,
    currentRenderMode: RenderMode,
    currentChapterIndex: Int,
    webViewRefForTts: WebView?,
    onOpenPaletteManager: () -> Unit,
    onColorChange: (UserHighlight, Int) -> Unit,
    onStyleChange: (UserHighlight, HighlightStyle) -> Unit,
    onDictionaryLookup: (String) -> Unit,
    onTranslateLookup: (String) -> Unit,
    onSearchLookup: (String) -> Unit
) {
    val context = LocalContext.current
    val highlightToNoteCfi = navigation.highlightToNoteCfi ?: return
    val targetHighlight = userHighlights.find {
        it.cfi == highlightToNoteCfi || it.cfi.contains(highlightToNoteCfi)
    } ?: return
    AnnotationBottomSheet(
        highlight = targetHighlight,
        effectiveBg = effectiveBg,
        effectiveText = effectiveText,
        activeHighlightPalette = activeHighlightPalette,
        onColorChange = { newColor -> onColorChange(targetHighlight, newColor) },
        onStyleChange = { newStyle -> onStyleChange(targetHighlight, newStyle) },
        onOpenPaletteManager = onOpenPaletteManager,
        onDismiss = { navigation.highlightToNoteCfi = null },
        onSave = { noteText ->
            val index = userHighlights.indexOfFirst { it.cfi == targetHighlight.cfi }
            if (index != -1) {
                userHighlights[index] = targetHighlight.copy(note = noteText.takeIf { it.isNotBlank() })
            }
            navigation.highlightToNoteCfi = null
        },
        onDelete = {
            userHighlights.remove(targetHighlight)

            if (currentRenderMode == RenderMode.VERTICAL_SCROLL && targetHighlight.chapterIndex == currentChapterIndex) {
                val cssClass = targetHighlight.color.cssClass
                val jsCommand = "javascript:window.HighlightBridgeHelper.removeHighlightByCfi('${escapeJsString(
                    targetHighlight.cfi)}', '$cssClass');"
                webViewRefForTts?.evaluateJavascript(jsCommand, null)
            }
            navigation.highlightToNoteCfi = null
        },
        onCopy = {
            val copied = copyPlainTextToClipboard(
                context = context,
                label = context.getString(R.string.clip_label_copied_text),
                text = targetHighlight.text
            )
            if (!copied) {
                Toast.makeText(context, context.getString(R.string.error_copy_to_clipboard), Toast.LENGTH_SHORT).show()
            }
            navigation.highlightToNoteCfi = null
        },
        onDictionary = {
            onDictionaryLookup(targetHighlight.text)
            navigation.highlightToNoteCfi = null
        },
        onTranslate = {
            onTranslateLookup(targetHighlight.text)
            navigation.highlightToNoteCfi = null
        },
        onSearch = {
            onSearchLookup(targetHighlight.text)
            navigation.highlightToNoteCfi = null
        }
    )
}

@Composable
internal fun EpubFootnoteNavigationSheet(
    navigation: EpubReaderNavigationState,
    epubBook: EpubBook,
    chapters: List<EpubChapter>,
    paginator: IPaginator?,
    scope: CoroutineScope,
    currentChapterIndex: Int,
    currentRenderMode: RenderMode,
    isNativeVerticalMode: Boolean,
    paginatedPagerState: PagerState,
    effectiveBg: Color,
    effectiveText: Color,
    currentJumpLocator: () -> SharedReaderLocator?,
    onDestinationResolved: (Locator, SharedReaderLocator?, Int?, SharedReaderLocator?) -> Unit,
    scrollToNativeLocator: (Locator?, Int?, Int?) -> Unit,
    onFragmentLoad: (String?) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onClearInitialScrollTarget: () -> Unit
) {
    val activeFootnoteHtml = navigation.activeFootnoteHtml ?: return
    FootnoteBottomSheet(
        htmlContent = activeFootnoteHtml,
        effectiveBg = effectiveBg,
        effectiveText = effectiveText,
        onInternalLinkClick = { href ->
            scope.launch {
                val currentLocator = currentJumpLocator()
                val bookPaginator = paginator as? BookPaginator
                val currentChapterPath = chapters.getOrNull(currentChapterIndex)?.absPath.orEmpty()
                val extractionBaseUrl = "file://${epubBook.extractionBasePath.trimEnd('/')}/"
                val decodedHref = runCatching {
                    java.net.URLDecoder.decode(href, "UTF-8")
                }.getOrDefault(href)
                val bookRelativeHref = decodedHref.removePrefix(extractionBaseUrl).trimStart('/')
                val hrefPath = bookRelativeHref.substringBefore('#')
                val hasBookRootPath = chapters.any { chapter ->
                    chapter.absPath.trimStart('/') == hrefPath
                }
                val hrefForNavigation = if (hasBookRootPath) bookRelativeHref else href
                val resolutionBase = if (hasBookRootPath) "" else currentChapterPath
                val (targetLocator, targetPage) = withContext(Dispatchers.IO) {
                    val locator = bookPaginator?.findStableLocatorForHref(resolutionBase, hrefForNavigation)
                    locator to (locator?.let { bookPaginator.findStablePageForLocator(it) }
                        ?: bookPaginator?.findStablePageForHref(resolutionBase, hrefForNavigation))
                }
                if (targetLocator == null && targetPage == null) return@launch

                val destination = targetLocator
                    ?: targetPage?.let { bookPaginator?.getLocatorForPage(it) }
                destination?.let {
                    onDestinationResolved(it, currentLocator, targetPage, it.toEpubJumpLocator(targetPage))
                }
                when {
                    isNativeVerticalMode -> scrollToNativeLocator(
                        destination,
                        targetPage,
                        destination?.chapterIndex
                    )
                    currentRenderMode == RenderMode.VERTICAL_SCROLL -> {
                        val targetChapter = destination?.chapterIndex
                            ?: targetPage?.let { bookPaginator?.findChapterIndexForPage(it) }
                        if (targetChapter != null) {
                            onFragmentLoad(hrefForNavigation.substringAfter('#', "").takeIf(String::isNotBlank))
                            onClearInitialScrollTarget()
                            onSelectChapter(targetChapter)
                        }
                    }
                    targetPage != null -> paginatedPagerState.scrollToPage(targetPage)
                }
            }
        },
        onDismiss = { navigation.activeFootnoteHtml = null }
    )
}

@Composable
internal fun EpubReaderPageSliderLayer(
    navigation: EpubReaderNavigationState,
    scope: CoroutineScope,
    isVisible: Boolean,
    totalPages: Int,
    isNativeVerticalMode: Boolean,
    currentRenderMode: RenderMode,
    nativeVerticalTotalPages: Int,
    clientHeightPx: Int,
    webViewRefForTts: WebView?,
    bottomPadding: Dp,
    isJumpHistoryVisible: Boolean,
    isPageInfoVisible: Boolean,
    pageInfoPosition: PageInfoPosition,
    pageInfoBarHeight: Dp,
    colors: ReaderSliderChromeColors,
    motionPolicy: ReaderMotionPolicy,
    modifier: Modifier = Modifier,
    currentJumpLocator: () -> SharedReaderLocator?,
    recordJump: (SharedReaderLocator?, SharedReaderLocator?) -> Unit,
    jumpTargetForPage: (Int) -> SharedReaderLocator?,
    scrollNativeProgress: (Float) -> Unit,
    scrollToPaginatedPage: (Int) -> Unit,
    paginatedJumpLocatorForPage: (Int) -> Locator?,
    jumpPaginatedToPage: suspend (Int, Locator?) -> Unit
) {
    EpubReaderPageSlider(
        isVisible = isVisible,
        totalPages = totalPages,
        sliderCurrentPage = navigation.sliderCurrentPage,
        sliderStartPage = navigation.sliderStartPage,
        onScrub = { newValue ->
            navigation.sliderCurrentPage = newValue
            navigation.isFastScrubbing = true
            val scrubOrigin = navigation.pendingSliderJumpOrigin ?: currentJumpLocator().also {
                navigation.pendingSliderJumpOrigin = it
            }
            val scrubGeneration = navigation.sliderJumpGeneration + 1
            navigation.sliderJumpGeneration = scrubGeneration
            navigation.scrubDebounceJob?.cancel()
            navigation.scrubDebounceJob = scope.launch {
                try {
                    delay(200)
                    if (isActive) {
                        val targetPage = newValue.roundToInt()
                        jumpTargetForPage(targetPage)?.let {
                            recordJump(it, scrubOrigin)
                        }
                        if (isNativeVerticalMode) {
                            scrollNativeProgress(
                                nativeVerticalProgressForCompatPage(
                                    pageIndex = targetPage - 1,
                                    totalPageCount = nativeVerticalTotalPages
                                )
                            )
                        } else if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                            val scrollY = (targetPage - 1) * clientHeightPx
                            webViewRefForTts?.evaluateJavascript("window.scrollTo(0, $scrollY);", null)
                        } else {
                            scrollToPaginatedPage(targetPage - 1)
                        }
                        navigation.isFastScrubbing = false
                    }
                } finally {
                    if (navigation.sliderJumpGeneration == scrubGeneration) {
                        navigation.pendingSliderJumpOrigin = null
                    }
                }
            }
        },
        onJumpToPage = { page ->
            scope.launch {
                navigation.scrubDebounceJob?.cancel()
                navigation.sliderJumpGeneration += 1
                navigation.pendingSliderJumpOrigin = null
                val currentLocator = currentJumpLocator()
                jumpTargetForPage(page)?.let {
                    recordJump(it, currentLocator)
                }
                if (isNativeVerticalMode) {
                    navigation.sliderCurrentPage = page.toFloat()
                    scrollNativeProgress(
                        nativeVerticalProgressForCompatPage(
                            pageIndex = page - 1,
                            totalPageCount = nativeVerticalTotalPages
                        )
                    )
                } else if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                    navigation.sliderCurrentPage = page.toFloat()
                    val scrollY = (page - 1) * clientHeightPx
                    webViewRefForTts?.evaluateJavascript("window.scrollTo(0, $scrollY);", null)
                } else {
                    navigation.sliderCurrentPage = page.toFloat()
                    val targetLocator = paginatedJumpLocatorForPage(page - 1)
                    jumpPaginatedToPage(page - 1, targetLocator)
                }
            }
        },
        modifier = modifier
            .padding(
                bottom = bottomPadding + 45.dp +
                    if (isJumpHistoryVisible) 40.dp else 0.dp +
                    if (isPageInfoVisible && pageInfoPosition == PageInfoPosition.BOTTOM) {
                        pageInfoBarHeight
                    } else {
                        0.dp
                    }
            ),
        activeColor = colors.activeTrackColor,
        inactiveColor = colors.inactiveTrackColor,
        contentColor = colors.contentColor,
        readerMotionPolicy = motionPolicy
    )

    if (isVisible && navigation.isFastScrubbing) {
        PageScrubbingAnimation(currentPage = navigation.sliderCurrentPage.roundToInt(), totalPages = totalPages)
    }
}
