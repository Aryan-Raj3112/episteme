// EpubReaderOverlayLayers.kt
//
// Overlay layers extracted from EpubReaderHost's scaffold content closure.
// The original monolithic closure compiled into a single ART-hostile method
// (tens of thousands of bytecode units with hundreds of registers) that some
// verifiers reject at class-load time. Keeping each layer in its own file and
// composable bounds generated method sizes.
package com.aryan.reader.epubreader

import com.aryan.reader.shared.ui.SharedMobileEpubLoading
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
            // Keyed on id, which never changes. Keyed on cfi this silently dropped the note whenever
            // the highlight had been re-anchored — repairing a locator rewrites its position, and
            // re-highlighting the same span rewrites cfi — leaving the edit to land on whatever
            // highlight now occupies the old position, or on none.
            val index = userHighlights.indexOfFirst { it.id == targetHighlight.id }
            if (index != -1) {
                userHighlights[index] = userHighlights[index].copy(
                    note = noteText.takeIf { it.isNotBlank() }
                )
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
    isTwoPageSpread: Boolean,
    totalBookPageCount: Int,
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
    // The shared slider owns its own thumb, so this is the *scrub* position only: `sliderCurrentPage`
    // stays the committed step (written by `onJumpToPage` and the host's page-change effect) and is
    // what feeds the widget's `pageIndex`. Writing the live scrub value back into `pageIndex` would
    // re-key the widget's internal value every frame and make the thumb stutter.
    var scrubStep by remember { mutableStateOf<Int?>(null) }
    EpubReaderPageSlider(
        isVisible = isVisible,
        totalPages = totalPages,
        sliderCurrentPage = navigation.sliderCurrentPage,
        isTwoPageSpread = isTwoPageSpread,
        totalBookPageCount = totalBookPageCount,
        onScrub = { newValue ->
            scrubStep = newValue.roundToInt()
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
        onScrubFinished = {
            scrubStep = null
            navigation.isFastScrubbing = false
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
        PageScrubbingAnimation(
            currentPage = scrubStep ?: navigation.sliderCurrentPage.roundToInt(),
            totalPages = totalPages
        )
    }
}

/**
 * Musician mode's auto-scroll temp-pause overlay: left/right regions that jump to the
 * chapter's top/bottom on tap, or pause auto-scroll for a second while held. Extracted from
 * `EpubReaderHost`'s scaffold content closure to bound that closure's generated method size;
 * the overlay owns only the gesture ring/pulse, the scrolling itself stays with the host.
 */
@Composable
internal fun EpubReaderMusicianAutoScrollOverlay(
    scope: CoroutineScope,
    webViewRefForTts: WebView?,
    motionPolicy: ReaderMotionPolicy,
    currentClientHeightValue: Int,
    triggerAutoScrollTempPause: (Long) -> Unit
) {
    var leftPulseTrigger by remember { mutableLongStateOf(0L) }
    var rightPulseTrigger by remember { mutableLongStateOf(0L) }

    var leftHoldProgress by remember { mutableFloatStateOf(0f) }
    var rightHoldProgress by remember { mutableFloatStateOf(0f) }

    val leftPulseAlpha by animateFloatAsState(
        targetValue = if (System.currentTimeMillis() - leftPulseTrigger < 150) 0.3f else 0f,
        animationSpec = tween(motionPolicy.durationMillis(150)), label = "leftPulse"
    )
    val rightPulseAlpha by animateFloatAsState(
        targetValue = if (System.currentTimeMillis() - rightPulseTrigger < 150) 0.3f else 0f,
        animationSpec = tween(motionPolicy.durationMillis(150)), label = "rightPulse"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        val regionHeight = Modifier.fillMaxHeight(0.4f)
        val regionWidth = Modifier.fillMaxWidth(0.25f)
        val topOffset = 100.dp

        // Left Region
        Box(
            modifier = regionWidth
                .then(regionHeight)
                .align(Alignment.TopStart)
                .offset(y = topOffset)
                .padding(start = 8.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = leftPulseAlpha), RoundedCornerShape(12.dp))
                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var isLongPress = false
                        val job = scope.launch {
                            val startTime = System.currentTimeMillis()
                            while (isActive) {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= 1000) {
                                    leftHoldProgress = 0f
                                    isLongPress = true
                                    leftPulseTrigger = System.currentTimeMillis()
                                    triggerAutoScrollTempPause(1000L)

                                    scope.launch {
                                        webViewRefForTts?.evaluateJavascript(
                                            "window.scrollTo({ top: 0, behavior: 'auto' });", null
                                        )
                                    }
                                    break
                                }
                                leftHoldProgress = elapsed / 1000f
                                delay(16)
                            }
                        }

                        val up = waitForUpOrCancellation()
                        job.cancel()
                        leftHoldProgress = 0f

                        if (!isLongPress && up != null) {
                            up.consume()
                            leftPulseTrigger = System.currentTimeMillis()
                            triggerAutoScrollTempPause(600L)
                            val amount = (currentClientHeightValue * 0.75f).toInt()
                            webViewRefForTts?.evaluateJavascript(
                                "window.scrollBy({ top: -${amount}, behavior: '${motionPolicy.webViewScrollBehavior()}' });", null
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (leftHoldProgress > 0f) {
                CircularProgressIndicator(
                    progress = { leftHoldProgress },
                    modifier = Modifier.size(48.dp).alpha(0.6f),
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = Color.Transparent,
                    strokeWidth = 4.dp
                )
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).alpha(0.6f),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Right Region
        Box(
            modifier = regionWidth
                .then(regionHeight)
                .align(Alignment.TopEnd)
                .offset(y = topOffset)
                .padding(end = 8.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = rightPulseAlpha), RoundedCornerShape(12.dp))
                .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var isLongPress = false
                        val job = scope.launch {
                            val startTime = System.currentTimeMillis()
                            while (isActive) {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= 1000) {
                                    rightHoldProgress = 0f
                                    isLongPress = true
                                    rightPulseTrigger = System.currentTimeMillis()
                                    triggerAutoScrollTempPause(1000L)

                                    scope.launch {
                                        webViewRefForTts?.evaluateJavascript(
                                            "window.scrollTo({ top: document.body.scrollHeight, behavior: 'auto' });", null
                                        )
                                    }
                                    break
                                }
                                rightHoldProgress = elapsed / 1000f
                                delay(16)
                            }
                        }

                        val up = waitForUpOrCancellation()
                        job.cancel()
                        rightHoldProgress = 0f

                        if (!isLongPress && up != null) {
                            up.consume()
                            rightPulseTrigger = System.currentTimeMillis()
                            triggerAutoScrollTempPause(600L)
                            val amount = (currentClientHeightValue * 0.75f).toInt()
                            webViewRefForTts?.evaluateJavascript(
                                "window.scrollBy({ top: ${amount}, behavior: '${motionPolicy.webViewScrollBehavior()}' });", null
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (rightHoldProgress > 0f) {
                CircularProgressIndicator(
                    progress = { rightHoldProgress },
                    modifier = Modifier.size(48.dp).alpha(0.6f),
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = Color.Transparent,
                    strokeWidth = 4.dp
                )
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).alpha(0.6f),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Solid scrims that fill the system-bar strips while the reader chrome is hidden, extracted from
 * `EpubReaderHost`'s scaffold content closure to bound that closure's generated method size.
 * Call from the same `Box` as the bars so the alignment modifiers resolve there.
 */
@Composable
internal fun BoxScope.EpubReaderNavBarScrims(color: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(color)
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .windowInsetsStartWidth(WindowInsets.navigationBars)
            .background(color)
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .windowInsetsEndWidth(WindowInsets.navigationBars)
            .background(color)
    )
}
