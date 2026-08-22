// PaginatedReader.kt
@file:Suppress("VariableNeverRead")

package com.aryan.reader.paginatedreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.aryan.reader.copyPlainTextToClipboard
import androidx.compose.ui.unit.isSpecified
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest.Builder
import com.aryan.reader.R
import com.aryan.reader.countWords
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.PaginatedTextSelectionMenu
import com.aryan.reader.epubreader.PaletteManagerDialog
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt


@Suppress("unused")
@SuppressLint("UnusedBoxWithConstraintsScope", "BinaryOperationInTimber")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun PaginatedReaderContent(
    uiState: PaginatedReaderUiState,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    isRightToLeftPagination: Boolean = false,
    effectiveBg: Color,
    effectiveText: Color,
    searchQuery: String,
    ttsHighlightInfo: TtsHighlightInfo?,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    onGetPage: (Int) -> Page?,
    onGetChapterIndex: (Int) -> Int?,
    onGetChapterPath: (Int) -> String?,
    onLinkClick: (currentChapterPath: String, href: String, onNavComplete: (Int) -> Unit) -> Unit,
    onInternalLinkNavigated: (Int, Locator?) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onGetChapterInfo: (Int) -> Pair<String, Int?>?,
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String, SharedReaderLocator, HighlightStyle) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<Int>,
    onUpdatePalette: (Int, Int) -> Unit,
    isDarkTheme: Boolean,
    pageTextureModifier: Modifier = Modifier,
    pageTextureBitmap: ImageBitmap? = null,
    pageTextureAlpha: Float = 0f
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pageViewConfiguration = LocalViewConfiguration.current
    var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val textMeasurer = rememberTextMeasurer()
    var activeSelection by remember { mutableStateOf<PaginatedSelection?>(null) }

    if (showExternalLinkDialog != null) {
        val urlToShow = showExternalLinkDialog!!
        AlertDialog(
            onDismissRequest = { showExternalLinkDialog = null },
            title = { Text(stringResource(R.string.dialog_external_link_title)) },
            text = {
                Text(
                    stringResource(R.string.dialog_external_link_desc, urlToShow)
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            val copied = copyPlainTextToClipboard(
                                context = context,
                                label = context.getString(R.string.clip_label_copied_link),
                                text = urlToShow
                            )
                            if (!copied) {
                                Toast.makeText(context, context.getString(R.string.error_copy_to_clipboard), Toast.LENGTH_SHORT).show()
                            }
                            showExternalLinkDialog = null
                        }) { Text(stringResource(R.string.action_copy)) }
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Timber.e(
                                    e, "No activity found to handle intent for URL: $urlToShow"
                                )
                                Toast.makeText(
                                    context, context.getString(R.string.error_no_browser), Toast.LENGTH_LONG
                                ).show()
                            }
                            showExternalLinkDialog = null
                        }) { Text(stringResource(R.string.action_open)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalLinkDialog = null }) { Text(stringResource(R.string.action_cancel)) }
            })
    }

    var pageTurnTouchY by remember { mutableStateOf<Float?>(null) }
    var lastKnownSelectionRect by remember { mutableStateOf<Pair<Rect, Int>?>(null) }

    LaunchedEffect(activeSelection) {
        if (activeSelection != null && activeSelection!!.rect != Rect.Zero) {
            lastKnownSelectionRect = activeSelection!!.rect to pagerState.currentPage
        }
    }

    val blockLayoutMap = remember {
        ReactiveBlockMap()
    }

    var showPaletteManager by remember { mutableStateOf(false) }
    var pagerWindowBounds by remember { mutableStateOf(Rect.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    var isDraggingHandle by remember { mutableStateOf(false) }
    var pendingCrossPageSelection by remember { mutableStateOf<PendingCrossPageSelection?>(null) }
    var crossPageTriggerInfo by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(pagerState) {
        var previousPage = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { newPage ->
            if (newPage == previousPage + 1) {
                if (crossPageTriggerInfo != null && crossPageTriggerInfo!!.first == previousPage) {
                    pendingCrossPageSelection = PendingCrossPageSelection(fromPageIndex = previousPage)
                    Timber.d("CrossPageSelection: Strict bottom trigger activated, queued for page $newPage")
                }
            } else if (newPage != previousPage) {
                pendingCrossPageSelection = null
            }
            crossPageTriggerInfo = null
            previousPage = newPage
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Timber.tag("ReflowPaginationDiag").d("PaginatedReaderContent: isLoading=false, totalPageCount=${uiState.totalPageCount}")
        if (uiState.totalPageCount > 0) {
            uiState.generation

            var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }

            val magnifierModifier = if (magnifierCenter.isSpecified) {
                Modifier.magnifier(
                    sourceCenter = { magnifierCenter },
                    zoom = 1.5f,
                    size = DpSize(140.dp, 48.dp),
                    cornerRadius = 24.dp,
                    elevation = 4.dp
                )
            } else Modifier

            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { rootCoords = it }.then(magnifierModifier)) {
                run {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().onGloballyPositioned { coords ->
                            pagerWindowBounds =
                                Rect(coords.positionInWindow(), coords.size.toSize())
                        }.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) {
                                        pageTurnTouchY = down.position.y
                                    }
                                }
                            }
                        },
                        beyondViewportPageCount = 1,
                        reverseLayout = isRightToLeftPagination
                    ) { pageIndex ->
                        val pageOffset =
                            (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        val zIndex = -pageOffset

                        val pageModifier = if (isPageTurnAnimationEnabled) {
                            Modifier.zIndex(zIndex).realisticBookPage(
                                pagerState,
                                pageIndex,
                                effectiveBg,
                                isDarkTheme,
                                pageTurnTouchY,
                                pageTextureBitmap,
                                pageTextureAlpha
                            )
                        } else Modifier

                        var pageContent by remember { mutableStateOf<Page?>(null) }
                        var currentChapterPath by remember { mutableStateOf<String?>(null) }
                        var pageLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        val pageChapterIndex = onGetChapterIndex(pageIndex)
                        val pageUserHighlights = highlightsForPaginatedPage(
                            pageChapterIndex = pageChapterIndex,
                            userHighlights = userHighlights
                        )
                        val themedPageContent = remember(pageContent, isDarkTheme, effectiveBg, effectiveText) {
                            pageContent?.applyReaderThemeForDisplay(
                                isDarkTheme = isDarkTheme,
                                themeBackgroundColor = effectiveBg,
                                themeTextColor = effectiveText
                            )
                        }

                        if (pageUserHighlights.size != userHighlights.size) {
                            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                "page_scope page=$pageIndex pageChapter=$pageChapterIndex " +
                                    "inputHighlightCount=${userHighlights.size} " +
                                    "pageHighlightCount=${pageUserHighlights.size} " +
                                    "inputHighlightChapters=${userHighlights.map { it.chapterIndex }.distinct()}"
                            )
                        }

                        LaunchedEffect(pageIndex, uiState.generation) {
                            if (DEBUG_PAGE_TURN_DIAG) {
                                Timber.tag("PageTurnDiag").d("Page $pageIndex: Starting content fetch")
                            }
                            val fetchStartTime = if (DEBUG_PAGE_TURN_DIAG) System.currentTimeMillis() else 0L

                            pageContent = onGetPage(pageIndex)

                            if (DEBUG_PAGE_TURN_DIAG) {
                                val fetchDuration = System.currentTimeMillis() - fetchStartTime
                                Timber.tag("PageTurnDiag").d("Page $pageIndex: Content fetched in ${fetchDuration}ms")
                            }

                            onGetChapterPath(pageIndex)?.let { currentChapterPath = it }
                        }

                        LaunchedEffect(pageIndex, pageChapterIndex, currentChapterPath, themedPageContent) {
                            if (!READER_LINK_DIAGNOSTICS_ENABLED) return@LaunchedEffect
                            val page = themedPageContent ?: return@LaunchedEffect
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "page_render page=$pageIndex chapter=$pageChapterIndex " +
                                    "chapterPath=${currentChapterPath.orEmpty().readerLinkDiagPreview()} " +
                                    page.readerPageLinkDiagSummary()
                            )
                        }

                        val textBlocksOnPage =
                            themedPageContent?.content?.extractTextBlocks()
                                ?.filter { it.cfi != null } ?: emptyList()
                        val lastTextBlock = textBlocksOnPage.lastOrNull()
                        val lastBlockAbs = lastTextBlock?.let {
                            when (it) {
                                is ParagraphBlock -> it.startCharOffsetInSource
                                is HeaderBlock -> it.startCharOffsetInSource
                                is QuoteBlock -> it.startCharOffsetInSource
                                is ListItemBlock -> it.startCharOffsetInSource
                            }
                        }

                        LaunchedEffect(activeSelection, lastTextBlock, isDraggingHandle) {
                            if (isDraggingHandle && activeSelection != null && lastTextBlock != null &&
                                activeSelection!!.endPageIndex == pageIndex &&
                                activeSelection!!.endBlockIndex == lastTextBlock.blockIndex &&
                                activeSelection!!.endBlockCharOffset == lastBlockAbs) {
                                if (activeSelection!!.endOffset >= lastTextBlock.content.text.length - 3) {
                                    if (crossPageTriggerInfo?.first != pageIndex) {
                                        Timber.tag("TextSelectionDiag")
                                            .d("Cross-page trigger ACTIVATED. Selection at bottom-right of page $pageIndex.")
                                        crossPageTriggerInfo = pageIndex to lastTextBlock.cfi!!
                                    }
                                    return@LaunchedEffect
                                }
                            }
                            if (!isDraggingHandle && activeSelection == null && crossPageTriggerInfo?.first == pageIndex) {
                                Timber.tag("TextSelectionDiag")
                                    .d("Cross-page trigger CLEARED on page $pageIndex (Custom).")
                                crossPageTriggerInfo = null
                            }
                        }

                        // Smart Cross-page selection logic
                        LaunchedEffect(pendingCrossPageSelection, pageContent) {
                            val pending = pendingCrossPageSelection ?: return@LaunchedEffect
                            if (pageIndex != pending.fromPageIndex + 1) return@LaunchedEffect
                            val content = pageContent ?: return@LaunchedEffect

                            val firstTextBlock =
                                content.content.extractTextBlocks()
                                    .firstOrNull { it.cfi != null } ?: run {
                                    pendingCrossPageSelection = null
                                    return@LaunchedEffect
                                }

                            var layoutInfo: Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>? =
                                null
                            for (i in 0 until 20) {
                                layoutInfo = blockLayoutMap["${firstTextBlock.cfi}_$pageIndex"]
                                if (layoutInfo != null && layoutInfo.second.isAttached) break
                                delay(50)
                            }

                            if (layoutInfo == null || !layoutInfo.second.isAttached) {
                                pendingCrossPageSelection = null
                                return@LaunchedEffect
                            }

                            val text = firstTextBlock.content.text
                            if (text.isEmpty()) {
                                pendingCrossPageSelection = null
                                return@LaunchedEffect
                            }

                            // Smart boundary logic (>10 chars & ends at a word)
                            var endIndex = minOf(text.length, 10)
                            if (text.length > 10) {
                                for (i in 10 until text.length) {
                                    if (text[i].isWhitespace() || !text[i].isLetterOrDigit()) {
                                        endIndex = i
                                        break
                                    }
                                }
                            }

                            try {
                                val path = layoutInfo.first.getPathForRange(0, endIndex)
                                val localRect = path.getBounds()
                                val windowTopLeft =
                                    layoutInfo.second.localToWindow(localRect.topLeft)
                                val windowBottomRight =
                                    layoutInfo.second.localToWindow(localRect.bottomRight)

                                val previousSel = activeSelection

                                val firstTextBlockAbs = when (firstTextBlock) {
                                    is ParagraphBlock -> firstTextBlock.startCharOffsetInSource
                                    is HeaderBlock -> firstTextBlock.startCharOffsetInSource
                                    is QuoteBlock -> firstTextBlock.startCharOffsetInSource
                                    is ListItemBlock -> firstTextBlock.startCharOffsetInSource
                                }

                                val newTextPerBlock = (previousSel?.textPerBlock ?: emptyMap()).toMutableMap()
                                newTextPerBlock[
                                    buildSelectionBlockKey(
                                        pageIndex = pageIndex,
                                        blockIndex = firstTextBlock.blockIndex,
                                        blockCharOffset = firstTextBlockAbs
                                    )
                                ] = text.substring(0, endIndex)

                                val newText = newTextPerBlock.entries
                                    .sortedWith { first, second ->
                                        compareSelectionBlockKeys(first.key, second.key)
                                    }
                                    .joinToString(" ") { it.value }

                                activeSelection = PaginatedSelection(
                                    startBlockIndex = previousSel?.startBlockIndex ?: firstTextBlock.blockIndex,
                                    endBlockIndex = firstTextBlock.blockIndex,
                                    startBaseCfi = previousSel?.startBaseCfi ?: firstTextBlock.cfi!!,
                                    endBaseCfi = firstTextBlock.cfi!!,
                                    startOffset = previousSel?.startOffset ?: 0,
                                    endOffset = endIndex,
                                    text = newText,
                                    rect = Rect(windowTopLeft, windowBottomRight),
                                    startPageIndex = previousSel?.startPageIndex ?: pending.fromPageIndex,
                                    endPageIndex = pageIndex,
                                    startBlockCharOffset = previousSel?.startBlockCharOffset ?: firstTextBlockAbs,
                                    endBlockCharOffset = firstTextBlockAbs,
                                    textPerBlock = newTextPerBlock
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "CrossPageSelection: Failed to create selection")
                            }

                            pendingCrossPageSelection = null
                        }

                        val onGeneralTapCallback: (Offset) -> Unit = { offset ->
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "page_general_tap source=content page=$pageIndex x=${offset.x.roundToInt()} y=${offset.y.roundToInt()}"
                            )
                            activeSelection = null
                            onTap(offset)
                        }
                        val onLinkClickCallback: (String) -> Unit = { href ->
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "link_click_callback page=$pageIndex currentPagerPage=${pagerState.currentPage} " +
                                    "chapterPath=${currentChapterPath.orEmpty().readerLinkDiagPreview()} " +
                                    "href=${href.readerLinkDiagPreview()}"
                            )
                            if (href.isReaderExternalHref()) {
                                Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                    "external_link_dialog href=${href.readerLinkDiagPreview()}"
                                )
                                showExternalLinkDialog = href.readerExternalHrefForDisplay()
                            } else {
                                val path = currentChapterPath
                                if (path == null) {
                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).w(
                                        "internal_link_dropped reason=missing_current_chapter_path href=${href.readerLinkDiagPreview()}"
                                    )
                                } else {
                                    onLinkClick(path, href) { targetPageIndex ->
                                        onInternalLinkNavigated(targetPageIndex, null)
                                        coroutineScope.launch {
                                            Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG).d(
                                                "link_scroll targetPage=$targetPageIndex currentPage=${pagerState.currentPage}"
                                            )
                                            pagerState.scrollToPage(targetPageIndex)
                                        }
                                    }
                                }
                            }
                        }
                        val latestPageLayoutCoordinates = rememberUpdatedState(pageLayoutCoordinates)
                        val latestOnLinkClickCallback = rememberUpdatedState(onLinkClickCallback)
                        val pageHorizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                        val pageVerticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                        val pageContentBoundsProvider = {
                            pageLayoutCoordinates
                                ?.takeIf { it.isAttached }
                                ?.androidEpubPageContentBounds(
                                    horizontalPaddingPx = pageHorizontalPaddingPx,
                                    verticalPaddingPx = pageVerticalPaddingPx
                                )
                        }
                        val cutoffLogSignatures = remember(pageIndex, uiState.generation) {
                            mutableStateMapOf<String, Boolean>()
                        }
                        val renderedBlockBounds = remember(pageIndex, uiState.generation) {
                            mutableStateMapOf<Int, AndroidEpubRenderedBlockBounds>()
                        }
                        val cutoffDiagnosticsEnabled = !uiState.isLoading
                        val cutoffDiagnosticsContext =
                            "generation=${uiState.generation} loading=${uiState.isLoading} pageCount=${uiState.totalPageCount} " +
                                "density=${density.density} fontScale=${density.fontScale} " +
                                "locale=${context.resources.configuration.locales[0]} " +
                                "layoutDirection=${context.resources.configuration.layoutDirection}"

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(effectiveBg)
                                .then(pageTextureModifier)
                                .then(pageModifier)
                                .onGloballyPositioned { coordinates ->
                                    pageLayoutCoordinates = coordinates
                                    if (cutoffDiagnosticsEnabled) {
                                        logAndroidEpubPageBoundsIfNeeded(
                                            pageIndex = pageIndex,
                                            pageContentBounds = coordinates.androidEpubPageContentBounds(
                                                horizontalPaddingPx = pageHorizontalPaddingPx,
                                                verticalPaddingPx = pageVerticalPaddingPx
                                            ),
                                            diagnosticsContext = cutoffDiagnosticsContext,
                                            signatureAlreadyLogged = { signature ->
                                                cutoffLogSignatures[signature] == true
                                            },
                                            markSignatureLogged = { signature ->
                                                cutoffLogSignatures[signature] = true
                                            }
                                        )
                                    }
                                }
                                .pointerInput(pageIndex, pageViewConfiguration.touchSlop) {
                                    awaitEachGesture {
                                        awaitReaderLinkTap(
                                            source = "PageLinkInterceptor:page=$pageIndex",
                                            urlAtPosition = { offset ->
                                                val hit = latestPageLayoutCoordinates.value
                                                    ?.takeIf { it.isAttached }
                                                    ?.let { coordinates ->
                                                        blockLayoutMap.readerLinkAtPagePosition(
                                                            pageCoordinates = coordinates,
                                                            pageIndex = pageIndex,
                                                            position = offset
                                                        )
                                                    }
                                                if (hit != null) {
                                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                                        "page_link_interceptor_hit page=$pageIndex block=${hit.blockIndex} " +
                                                            "cfi=${hit.cfi.orEmpty().readerLinkDiagPreview()} " +
                                                            "href=${hit.href.readerLinkDiagPreview()}"
                                                    )
                                                }
                                                hit?.href
                                            },
                                            touchSlop = pageViewConfiguration.touchSlop,
                                            onLinkClick = { latestOnLinkClickCallback.value(it) }
                                        )
                                    }
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                                "page_general_tap source=background page=$pageIndex " +
                                                    "x=${offset.x.roundToInt()} y=${offset.y.roundToInt()}"
                                            )
                                            activeSelection = null
                                            onTap(offset)
                                        })
                                })
                                Box(modifier = Modifier.fillMaxSize().padding(
                                    horizontal = horizontalPadding,
                                    vertical = verticalPadding
                                ), contentAlignment = Alignment.TopStart) {
                                    if (themedPageContent != null) {
                                        val displayPage = themedPageContent

                                        // Measure page blocks at their natural height; pagination, not Column, owns page breaks.
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(unbounded = true)
                                        ) {
                                            val searchHighlightColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            val ttsHighlightColor =
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)

                                            displayPage.content.forEach { block ->
                                                val marginModifier = Modifier.padding(
                                                    top = block.style.margin.top.coerceAtLeast(0.dp),
                                                    bottom = block.style.margin.bottom.coerceAtLeast(
                                                        0.dp
                                                    )
                                                )

                                                val alignModifier =
                                                    if (block.style.horizontalAlign == "center") {
                                                        Modifier.align(Alignment.CenterHorizontally)
                                                    } else {
                                                        Modifier.padding(
                                                            start = block.style.margin.left.coerceAtLeast(
                                                                0.dp
                                                            ),
                                                            end = block.style.margin.right.coerceAtLeast(
                                                                0.dp
                                                            )
                                                        )
                                                    }

                                                val widthModifier =
                                                    if (block.style.width != Dp.Unspecified) {
                                                        Modifier.width(block.style.width)
                                                    } else {
                                                        Modifier.fillMaxWidth()
                                                    }.then(
                                                        Modifier.widthIn(
                                                            min = block.style.minWidth.takeIf { it.isSpecified && it > 0.dp } ?: Dp.Unspecified,
                                                            max = block.style.maxWidth.takeIf { it.isSpecified && it > 0.dp } ?: Dp.Unspecified
                                                        )
                                                    )

                                                val styleModifier =
                                                    alignModifier.then(if (block.style.horizontalAlign == "center") widthModifier else Modifier)
                                                        .drawCssBorders(
                                                            blockStyle = block.style,
                                                            density = density
                                                        )
                                                        .then(if (block.style.visibility == "hidden") Modifier.graphicsLayer(alpha = 0f) else Modifier)

                                                val diagnosticModifier =
                                                    Modifier.onGloballyPositioned { coordinates ->
                                                        val actualHeight =
                                                            coordinates.size.height
                                                        if (cutoffDiagnosticsEnabled) {
                                                            val pageContentBounds = pageContentBoundsProvider()
                                                            if (pageContentBounds != null) {
                                                                renderedBlockBounds[block.blockIndex] = AndroidEpubRenderedBlockBounds(
                                                                    blockIndex = block.blockIndex,
                                                                    kind = block.androidEpubKindName(),
                                                                    leftPx = coordinates.positionInWindow().x.roundToInt(),
                                                                    topPx = coordinates.positionInWindow().y.roundToInt() - pageContentBounds.topPx,
                                                                    widthPx = coordinates.size.width,
                                                                    heightPx = coordinates.size.height,
                                                                    expectedHeightPx = block.expectedHeight,
                                                                    sourceRange = block.androidEpubSourceRangeLabel(),
                                                                    textChars = block.androidEpubTextCharCount(),
                                                                    marginTopPx = with(density) { block.style.margin.top.coerceAtLeast(0.dp).roundToPx() },
                                                                    marginBottomPx = with(density) { block.style.margin.bottom.coerceAtLeast(0.dp).roundToPx() },
                                                                    paddingTopPx = with(density) { block.style.padding.top.coerceAtLeast(0.dp).roundToPx() },
                                                                    paddingBottomPx = with(density) { block.style.padding.bottom.coerceAtLeast(0.dp).roundToPx() }
                                                                )
                                                            }
                                                            val didLogOverflow = logAndroidEpubBlockOverflowIfNeeded(
                                                                pageIndex = pageIndex,
                                                                block = block,
                                                                coordinates = coordinates,
                                                                pageContentBounds = pageContentBounds,
                                                                diagnosticsContext = cutoffDiagnosticsContext,
                                                                signatureAlreadyLogged = { signature ->
                                                                    cutoffLogSignatures[signature] == true
                                                                },
                                                                markSignatureLogged = { signature ->
                                                                    cutoffLogSignatures[signature] = true
                                                                }
                                                            )
                                                            if (didLogOverflow) {
                                                                logAndroidEpubPageBlockBoundsIfNeeded(
                                                                    pageIndex = pageIndex,
                                                                    triggerBlock = block,
                                                                    renderedBounds = renderedBlockBounds.values,
                                                                    pageContentBounds = pageContentBounds,
                                                                    diagnosticsContext = cutoffDiagnosticsContext,
                                                                    signatureAlreadyLogged = { signature ->
                                                                        cutoffLogSignatures[signature] == true
                                                                    },
                                                                    markSignatureLogged = { signature ->
                                                                        cutoffLogSignatures[signature] = true
                                                                    }
                                                                )
                                                            }
                                                            logAndroidEpubRenderedTablePageIfNeeded(
                                                                pageIndex = pageIndex,
                                                                renderedBounds = renderedBlockBounds.values,
                                                                pageContentBounds = pageContentBounds,
                                                                diagnosticsContext = cutoffDiagnosticsContext,
                                                                signatureAlreadyLogged = { signature ->
                                                                    cutoffLogSignatures[signature] == true
                                                                },
                                                                markSignatureLogged = { signature ->
                                                                    cutoffLogSignatures[signature] = true
                                                                }
                                                            )
                                                            logAndroidEpubRenderedPageGapIfNeeded(
                                                                pageIndex = pageIndex,
                                                                renderedBounds = renderedBlockBounds.values,
                                                                pageContentBounds = pageContentBounds,
                                                                diagnosticsContext = cutoffDiagnosticsContext,
                                                                signatureAlreadyLogged = { signature ->
                                                                    cutoffLogSignatures[signature] == true
                                                                },
                                                                markSignatureLogged = { signature ->
                                                                    cutoffLogSignatures[signature] = true
                                                                }
                                                            )
                                                        }
                                                        if (block.expectedHeight > 0) {
                                                            val snippet = when (block) {
                                                                is ParagraphBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is HeaderBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is QuoteBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is ListItemBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                is TextContentBlock -> block.content.text.take(
                                                                    50
                                                                )

                                                                else -> "Non-text content"
                                                            }

                                                            checkLayoutMismatch(
                                                                blockIndex = block.blockIndex,
                                                                blockType = block::class.simpleName
                                                                    ?: "Block",
                                                                expectedHeight = block.expectedHeight,
                                                                actualHeight = actualHeight,
                                                                textSnippet = snippet,
                                                                diagnostics = buildString {
                                                                    append("page=")
                                                                    append(pageIndex)
                                                                    append(", width=")
                                                                    append(coordinates.size.width)
                                                                    append("px, styleWidth=")
                                                                    append(block.style.width)
                                                                    append(", maxWidth=")
                                                                    append(block.style.maxWidth)
                                                                    append(", margin=")
                                                                    append(block.style.margin)
                                                                    append(", padding=")
                                                                    append(block.style.padding)
                                                                    append(", borders=(")
                                                                    append(block.style.borderLeft?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderTop?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderRight?.width ?: 0.dp)
                                                                    append(", ")
                                                                    append(block.style.borderBottom?.width ?: 0.dp)
                                                                    append(")")
                                                                    when (block) {
                                                                        is ParagraphBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is HeaderBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is QuoteBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                            append(", textAlign=")
                                                                            append(block.textAlign)
                                                                        }

                                                                        is ListItemBlock -> {
                                                                            append(", start=")
                                                                            append(block.startCharOffsetInSource)
                                                                            append(", end=")
                                                                            append(block.endCharOffsetInSource)
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                        }

                                                                        is TextContentBlock -> {
                                                                            append(", chars=")
                                                                            append(block.content.length)
                                                                        }

                                                                        else -> Unit
                                                                    }
                                                                },
                                                                tolerance = 2
                                                            )
                                                        }
                                                    }.then(marginModifier).then(styleModifier)

                                                Box(modifier = diagnosticModifier.androidEpubNaturalHeight()) {
                                                    val paddingModifier = Modifier.padding(
                                                        start = block.style.padding.left.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderLeft?.width ?: 0.dp),
                                                        top = block.style.padding.top.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderTop?.width ?: 0.dp),
                                                        end = block.style.padding.right.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderRight?.width
                                                            ?: 0.dp),
                                                        bottom = block.style.padding.bottom.coerceAtLeast(
                                                            0.dp
                                                        ) + (block.style.borderBottom?.width
                                                            ?: 0.dp)
                                                    ).then(
                                                        if (block.style.horizontalAlign != "center") widthModifier else Modifier.fillMaxWidth()
                                                    )

                                                    block.style.backgroundImage
                                                        ?.trim()
                                                        ?.takeIf { it.isNotBlank() && !it.contains("gradient(", ignoreCase = true) }
                                                        ?.let { backgroundImagePath ->
                                                            val backgroundFile = remember(backgroundImagePath) { File(backgroundImagePath) }
                                                            AsyncImage(
                                                                model = if (backgroundFile.exists()) backgroundFile else backgroundImagePath,
                                                                contentDescription = null,
                                                                modifier = Modifier.matchParentSize(),
                                                                contentScale = imageContentScale(block.style)
                                                            )
                                                        }

                                                    @Suppress("DEPRECATION") when (block) {
                                                        is ChantScoreBlock -> NativeChantScore(block, textStyle, paddingModifier)
                                                        is ParagraphBlock -> {
                                                            val paragraphStyle = textStyle.copy(
                                                                textAlign = block.textAlign
                                                                    ?: textStyle.textAlign
                                                            )
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        // Define absolute ranges
                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        // Calculate intersection
                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
                                                                            )

                                                                        // Check for overlap and apply
                                                                        // style
                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }

                                                            @Suppress(
                                                                "UnusedVariable",
                                                                "Unused"
                                                            ) val diagnosticModifier =
                                                                if (block.textAlign == TextAlign.Justify) {
                                                                    Modifier.onGloballyPositioned { coordinates ->
                                                                        val width =
                                                                            coordinates.size.width
                                                                        Timber.d(
                                                                            """
                                                                [UI Render]
                                                                Block Index: ${block.blockIndex}
                                                                Text Start: ${
                                                                                block.content.text.take(
                                                                                    20
                                                                                )
                                                                            }...
                                                                Actual Render Width Px: $width
                                                                ------------------------------------------------
                                                            """.trimIndent()
                                                                        )
                                                                    }
                                                                } else {
                                                                    Modifier
                                                                }

                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = paragraphStyle,
                                                                modifier = paddingModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = pageUserHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(highlight.cfi)
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                pageContentBoundsProvider = pageContentBoundsProvider,
                                                                cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is HeaderBlock -> {
                                                            val style = createHeaderTextStyle(
                                                                baseStyle = textStyle,
                                                                level = block.level,
                                                                textAlign = block.textAlign
                                                            )
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
                                                                            )

                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }
                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = style,
                                                                modifier = paddingModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = pageUserHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(
                                                                        highlight.cfi
                                                                    )
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                pageContentBoundsProvider = pageContentBoundsProvider,
                                                                cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is QuoteBlock -> {
                                                            val quoteStyle = textStyle.copy(
                                                                textAlign = block.textAlign
                                                                    ?: textStyle.textAlign
                                                            )
                                                            val quoteModifier =
                                                                paddingModifier.padding(start = 16.dp)
                                                            val searchHighlighted =
                                                                highlightQueryInText(
                                                                    block.content,
                                                                    searchQuery,
                                                                    searchHighlightColor
                                                                )
                                                            val finalContent =
                                                                if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                    buildAnnotatedString {
                                                                        append(searchHighlighted)

                                                                        val blockStartAbs =
                                                                            block.startCharOffsetInSource
                                                                        val blockEndAbs =
                                                                            block.startCharOffsetInSource + searchHighlighted.length
                                                                        val highlightStartAbs =
                                                                            ttsHighlightInfo.offset
                                                                        val highlightEndAbs =
                                                                            ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                        val intersectionStartAbs =
                                                                            maxOf(
                                                                                blockStartAbs,
                                                                                highlightStartAbs
                                                                            )
                                                                        val intersectionEndAbs =
                                                                            minOf(
                                                                                blockEndAbs,
                                                                                highlightEndAbs
                                                                            )

                                                                        if (intersectionStartAbs < intersectionEndAbs) {
                                                                            val highlightStartRelative =
                                                                                intersectionStartAbs - blockStartAbs
                                                                            val highlightEndRelative =
                                                                                intersectionEndAbs - blockStartAbs
                                                                            addStyle(
                                                                                style = SpanStyle(
                                                                                    background = ttsHighlightColor
                                                                                ),
                                                                                start = highlightStartRelative,
                                                                                end = highlightEndRelative
                                                                            )
                                                                        }
                                                                    }
                                                                } else {
                                                                    searchHighlighted
                                                                }
                                                            TextWithEmphasis(
                                                                text = finalContent,
                                                                style = quoteStyle,
                                                                modifier = quoteModifier,
                                                                pageIndex = pageIndex,
                                                                textMeasurer = textMeasurer,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback,
                                                                block = block,
                                                                userHighlights = pageUserHighlights,
                                                                activeSelection = activeSelection,
                                                                onSelectionChange = { sel ->
                                                                    activeSelection = sel
                                                                },
                                                                onHighlightClick = { highlight, _ ->
                                                                    onNoteRequested(highlight.cfi)
                                                                    activeSelection = null
                                                                },
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                pageContentBoundsProvider = pageContentBoundsProvider,
                                                                cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                onRegisterLayout = { layout, coords ->
                                                                    if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                        Triple(
                                                                            layout,
                                                                            coords,
                                                                            block
                                                                        )
                                                                })
                                                        }

                                                        is ListItemBlock -> {
                                                            Row(
                                                                modifier = paddingModifier,
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                val markerAreaModifier =
                                                                    Modifier.width(32.dp)
                                                                        .padding(end = 8.dp)
                                                                val itemMarkerImage = block.itemMarkerImage
                                                                val itemMarker = block.itemMarker

                                                                if (itemMarkerImage != null) {
                                                                    val imageRequest =
                                                                        Builder(LocalContext.current).data(
                                                                            File(
                                                                                itemMarkerImage
                                                                            )
                                                                        ).crossfade(true).build()
                                                                    val imageSize = with(density) {
                                                                        (textStyle.fontSize.value * 0.8f).sp.toDp()
                                                                    }

                                                                    AsyncImage(
                                                                        model = imageRequest,
                                                                        contentDescription = stringResource(R.string.content_desc_list_item_marker),
                                                                        modifier = markerAreaModifier.height(
                                                                            imageSize
                                                                        ),
                                                                        alignment = Alignment.CenterEnd,
                                                                        contentScale = ContentScale.FillHeight
                                                                    )
                                                                } else if (itemMarker != null) {
                                                                    Text(
                                                                        text = itemMarker,
                                                                        style = textStyle.copy(
                                                                            textAlign = TextAlign.End
                                                                        ),
                                                                        modifier = markerAreaModifier
                                                                    )
                                                                }
                                                                val searchHighlighted =
                                                                    highlightQueryInText(
                                                                        block.content,
                                                                        searchQuery,
                                                                        searchHighlightColor
                                                                    )
                                                                val finalContent =
                                                                    if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
                                                                        buildAnnotatedString {
                                                                            append(searchHighlighted)

                                                                            val blockStartAbs =
                                                                                block.startCharOffsetInSource
                                                                            val blockEndAbs =
                                                                                block.startCharOffsetInSource + searchHighlighted.length
                                                                            val highlightStartAbs =
                                                                                ttsHighlightInfo.offset
                                                                            val highlightEndAbs =
                                                                                ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                                                                            val intersectionStartAbs =
                                                                                maxOf(
                                                                                    blockStartAbs,
                                                                                    highlightStartAbs
                                                                                )
                                                                            val intersectionEndAbs =
                                                                                minOf(
                                                                                    blockEndAbs,
                                                                                    highlightEndAbs
                                                                                )

                                                                            if (intersectionStartAbs < intersectionEndAbs) {
                                                                                val highlightStartRelative =
                                                                                    intersectionStartAbs - blockStartAbs
                                                                                val highlightEndRelative =
                                                                                    intersectionEndAbs - blockStartAbs
                                                                                addStyle(
                                                                                    style = SpanStyle(
                                                                                        background = ttsHighlightColor
                                                                                    ),
                                                                                    start = highlightStartRelative,
                                                                                    end = highlightEndRelative
                                                                                )
                                                                            }
                                                                        }
                                                                    } else {
                                                                        searchHighlighted
                                                                    }
                                                                TextWithEmphasis(
                                                                    text = finalContent,
                                                                    style = textStyle,
                                                                    modifier = Modifier.weight(1f),
                                                                    pageIndex = pageIndex,
                                                                    textMeasurer = textMeasurer,
                                                                    onLinkClick = onLinkClickCallback,
                                                                    onGeneralTap = onGeneralTapCallback,
                                                                    block = block,
                                                                    userHighlights = pageUserHighlights,
                                                                    activeSelection = activeSelection,
                                                                    onSelectionChange = { sel ->
                                                                        activeSelection = sel
                                                                    },
                                                                    onHighlightClick = { highlight, _ ->
                                                                        onNoteRequested(highlight.cfi)
                                                                        activeSelection = null
                                                                    },
                                                                    isDarkTheme = isDarkTheme,
                                                                    themeBackgroundColor = effectiveBg,
                                                                    themeTextColor = effectiveText,
                                                                    pageContentBoundsProvider = pageContentBoundsProvider,
                                                                    cutoffDiagnosticsEnabled = cutoffDiagnosticsEnabled,
                                                                    cutoffDiagnosticsContext = cutoffDiagnosticsContext,
                                                                    onRegisterLayout = { layout, coords ->
                                                                        if (block.cfi != null) blockLayoutMap["${block.cfi}_$pageIndex"] =
                                                                            Triple(
                                                                                layout,
                                                                                coords,
                                                                                block
                                                                            )
                                                                    })
                                                            }
                                                        }

                                                        is WrappingContentBlock -> {
                                                            WrappingContentLayout(
                                                                block = block,
                                                                textStyle = textStyle,
                                                                imageSizeMultiplier = imageSizeMultiplier,
                                                                hideImages = hideImages,
                                                                modifier = paddingModifier,
                                                                searchQuery = searchQuery,
                                                                ttsHighlightInfo = ttsHighlightInfo,
                                                                searchHighlightColor = searchHighlightColor,
                                                                ttsHighlightColor = ttsHighlightColor,
                                                                isDarkTheme = isDarkTheme,
                                                                themeBackgroundColor = effectiveBg,
                                                                themeTextColor = effectiveText,
                                                                onLinkClick = onLinkClickCallback,
                                                                onGeneralTap = onGeneralTapCallback
                                                            )
                                                        }

                                                        is FlexContainerBlock -> {

                                                            if (block.style.flexDirection == "row") {
                                                                val horizontalArrangement =
                                                                    when (block.style.justifyContent) {
                                                                        "center" -> Arrangement.Center
                                                                        "flex-end" -> Arrangement.End
                                                                        "space-between" -> Arrangement.SpaceBetween
                                                                        "space-around" -> Arrangement.SpaceAround
                                                                        else -> Arrangement.Start
                                                                    }
                                                                val verticalAlignment =
                                                                    when (block.style.alignItems) {
                                                                        "center" -> Alignment.CenterVertically
                                                                        "flex-end" -> Alignment.Bottom
                                                                        else -> Alignment.Top
                                                                    }
                                                                val chantChildren: @Composable () -> Unit = {
                                                                    block.children.forEach { childBlock ->
                                                                        RenderFlexChildBlock(
                                                                            childBlock = childBlock,
                                                                            textStyle = textStyle,
                                                                            imageSizeMultiplier = imageSizeMultiplier,
                                                                            hideImages = hideImages,
                                                                            searchQuery = searchQuery,
                                                                            searchHighlightColor = searchHighlightColor,
                                                                            ttsHighlightInfo = ttsHighlightInfo,
                                                                            ttsHighlightColor = ttsHighlightColor,
                                                                            textMeasurer = textMeasurer,
                                                                            onLinkClickCallback = onLinkClickCallback,
                                                                            onGeneralTapCallback = onGeneralTapCallback,
                                                                            userHighlights = pageUserHighlights,
                                                                            activeSelection = activeSelection,
                                                                            onSelectionChange = { sel ->
                                                                                activeSelection =
                                                                                    sel
                                                                            },
                                                                            onHighlightClick = { highlight, _ ->
                                                                                onNoteRequested(
                                                                                    highlight.cfi
                                                                                )
                                                                                activeSelection =
                                                                                    null
                                                                            },
                                                                            isDarkTheme = isDarkTheme,
                                                                            themeBackgroundColor = effectiveBg,
                                                                            themeTextColor = effectiveText,
                                                                            blockLayoutMap = blockLayoutMap,
                                                                            density = density,
                                                                            imageLoader = imageLoader,
                                                                            pageIndex = pageIndex
                                                                        )
                                                                    }
                                                                }
                                                                if (block.style.display == "reader-chant-flow") {
                                                                    FlowRow(
                                                                        modifier = paddingModifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalArrangement = Arrangement.Bottom,
                                                                        content = { chantChildren() }
                                                                    )
                                                                } else {
                                                                    Row(
                                                                        modifier = paddingModifier.fillMaxWidth(),
                                                                        horizontalArrangement = horizontalArrangement,
                                                                        verticalAlignment = verticalAlignment,
                                                                        content = { chantChildren() }
                                                                    )
                                                                }
                                                            } else {
                                                                val verticalArrangement =
                                                                    when (block.style.justifyContent) {
                                                                        "center" -> Arrangement.Center
                                                                        "flex-end" -> Arrangement.Bottom
                                                                        "space-between" -> Arrangement.SpaceBetween
                                                                        "space-around" -> Arrangement.SpaceAround
                                                                        else -> Arrangement.Top
                                                                    }
                                                                val horizontalAlignment =
                                                                    when (block.style.alignItems) {
                                                                        "center" -> Alignment.CenterHorizontally
                                                                        "flex-end" -> Alignment.End
                                                                        else -> Alignment.Start
                                                                    }
                                                                Column(
                                                                    modifier = paddingModifier.fillMaxWidth(),
                                                                    verticalArrangement = verticalArrangement,
                                                                    horizontalAlignment = horizontalAlignment
                                                                ) {
                                                                    block.children.forEach { childBlock ->
                                                                        RenderFlexChildBlock(
                                                                            childBlock = childBlock,
                                                                            textStyle = textStyle,
                                                                            imageSizeMultiplier = imageSizeMultiplier,
                                                                            hideImages = hideImages,
                                                                            searchQuery = searchQuery,
                                                                            searchHighlightColor = searchHighlightColor,
                                                                            ttsHighlightInfo = ttsHighlightInfo,
                                                                            ttsHighlightColor = ttsHighlightColor,
                                                                            textMeasurer = textMeasurer,
                                                                            onLinkClickCallback = onLinkClickCallback,
                                                                            onGeneralTapCallback = onGeneralTapCallback,
                                                                            userHighlights = pageUserHighlights,
                                                                            activeSelection = activeSelection,
                                                                            onSelectionChange = { sel ->
                                                                                activeSelection =
                                                                                    sel
                                                                            },
                                                                            onHighlightClick = { highlight, _ ->
                                                                                onNoteRequested(
                                                                                    highlight.cfi
                                                                                )
                                                                                activeSelection =
                                                                                    null
                                                                            },
                                                                            isDarkTheme = isDarkTheme,
                                                                            themeBackgroundColor = effectiveBg,
                                                                            themeTextColor = effectiveText,
                                                                            blockLayoutMap = blockLayoutMap,
                                                                            density = density,
                                                                            imageLoader = imageLoader,
                                                                            pageIndex = pageIndex
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        is MathBlock -> {
                                                            val svgContent = block.svgContent?.takeIf { it.isNotBlank() }
                                                            Timber.d(
                                                                "PaginatedReader: Rendering MathBlock. Alt: '${block.altText}', Has SVG: ${svgContent != null}"
                                                            )
                                                            if (svgContent != null) {
                                                                val nonBlankSvgContent = svgContent
                                                                BoxWithConstraints(
                                                                    modifier = paddingModifier
                                                                ) {
                                                                    val localDensity =
                                                                        LocalDensity.current
                                                                    val fontSizePx =
                                                                        with(localDensity) {
                                                                            textStyle.fontSize.toPx()
                                                                        }
                                                                    val containerWidthPx =
                                                                        with(localDensity) {
                                                                            maxWidth.roundToPx()
                                                                        }
                                                                    val widthPx = parseSvgDimension(
                                                                        block.svgWidth,
                                                                        fontSizePx,
                                                                        containerWidthPx,
                                                                        localDensity
                                                                    )
                                                                    val heightPx =
                                                                        parseSvgDimension(
                                                                            block.svgHeight,
                                                                            fontSizePx,
                                                                            containerWidthPx,
                                                                            localDensity
                                                                        )

                                                                    var imageModifier: Modifier =
                                                                        Modifier
                                                                    if (widthPx != null) {
                                                                        val finalWidthDp =
                                                                            with(localDensity) { widthPx.toDp() }
                                                                        Timber.d("Applying calculated width to MathBlock image: $finalWidthDp")
                                                                        imageModifier =
                                                                            imageModifier.width(
                                                                                finalWidthDp
                                                                            )
                                                                    } else {
                                                                        Timber.w("Could not calculate a specific width for MathBlock. It will fill available space.")
                                                                        imageModifier =
                                                                            imageModifier.fillMaxWidth()
                                                                    }

                                                                    if (heightPx != null) {
                                                                        val finalHeightDp =
                                                                            with(localDensity) { heightPx.toDp() }
                                                                        Timber.d("Applying calculated height to MathBlock image: $finalHeightDp")
                                                                        imageModifier =
                                                                            imageModifier.height(
                                                                                finalHeightDp
                                                                            )
                                                                    } else {
                                                                        val viewBoxParts =
                                                                            block.svgViewBox?.split(
                                                                                ' ',
                                                                                ','
                                                                            )
                                                                                ?.mapNotNull { it.toFloatOrNull() }
                                                                        if (viewBoxParts != null && viewBoxParts.size == 4 && viewBoxParts[2] > 0) {
                                                                            val aspectRatio =
                                                                                viewBoxParts[3] / viewBoxParts[2]
                                                                            val effectiveWidth =
                                                                                widthPx
                                                                                    ?: containerWidthPx.toFloat()
                                                                            val finalHeightDp =
                                                                                with(localDensity) { (effectiveWidth * aspectRatio).toDp() }
                                                                            imageModifier =
                                                                                imageModifier.height(
                                                                                    finalHeightDp
                                                                                )
                                                                        } else {
                                                                            val fallbackHeightDp =
                                                                                with(localDensity) { (textStyle.fontSize.value * 3).sp.toDp() }
                                                                            imageModifier =
                                                                                imageModifier.height(
                                                                                    fallbackHeightDp
                                                                                )
                                                                        }
                                                                    }

                                                                    val imageRequest =
                                                                        Builder(LocalContext.current).data(
                                                                            SvgData(
                                                                                nonBlankSvgContent
                                                                            )
                                                                        ).listener(
                                                                            onError = { _, result ->
                                                                                Timber.e(
                                                                                    result.throwable,
                                                                                    "Coil failed to load SVG for MathBlock."
                                                                                )
                                                                            }).build()

                                                                    val colorFilter =
                                                                        if (block.isFromMathJax) ColorFilter.tint(
                                                                            textStyle.color
                                                                        )
                                                                        else null

                                                                    AsyncImage(
                                                                        model = imageRequest,
                                                                        contentDescription = block.altText
                                                                            ?: "Equation",
                                                                        modifier = imageModifier,
                                                                        contentScale = ContentScale.Fit,
                                                                        colorFilter = colorFilter,
                                                                        imageLoader = imageLoader
                                                                    )
                                                                }
                                                            } else {
                                                                Timber.w(
                                                                    "PaginatedReader: MathBlock has no SVG content, rendering alt text."
                                                                )
                                                                Text(
                                                                    text = block.altText
                                                                        ?: "[Equation not available]",
                                                                    style = textStyle,
                                                                    modifier = paddingModifier
                                                                )
                                                            }
                                                        }

                                                        is ImageBlock -> if (!hideImages) {
                                                            val style = block.style
                                                            val colorFilter =
                                                                if (block.style.filter == "invert(100%)") {
                                                                    val matrix = floatArrayOf(
                                                                        -1f,
                                                                        0f,
                                                                        0f,
                                                                        0f,
                                                                        255f,
                                                                        0f,
                                                                        -1f,
                                                                        0f,
                                                                        0f,
                                                                        255f,
                                                                        0f,
                                                                        0f,
                                                                        -1f,
                                                                        0f,
                                                                        255f,
                                                                        0f,
                                                                        0f,
                                                                        0f,
                                                                        1f,
                                                                        0f
                                                                    )
                                                                    ColorFilter.colorMatrix(
                                                                        ColorMatrix(matrix)
                                                                    )
                                                                } else {
                                                                    null
                                                                }
                                                            val context = LocalContext.current
                                                            val imageRequest =
                                                                Builder(context).data(File(block.path))
                                                                    .listener(onSuccess = { _, _ ->
                                                                        Timber.d(
                                                                            "Coil successfully loaded image: ${block.path}"
                                                                        )
                                                                    }, onError = { _, result ->
                                                                        Timber.e(
                                                                            result.throwable,
                                                                            "Coil FAILED to load image: ${block.path}"
                                                                        )
                                                                    }).crossfade(true).build()

                                                            BoxWithConstraints(
                                                                modifier = paddingModifier,
                                                                contentAlignment = imageBlockContentAlignment(style)
                                                            ) {
                                                                val scaledSize = computeImageRenderSizeDp(
                                                                    block = block,
                                                                    density = density,
                                                                    maxWidthDp = maxWidth,
                                                                    imageSizeMultiplier = imageSizeMultiplier
                                                                )
                                                                val finalImageModifier = Modifier
                                                                    .then(
                                                                        if (scaledSize != null) {
                                                                            Modifier.width(scaledSize.first).height(scaledSize.second)
                                                                        } else if (style.width.isSpecified && style.width > 0.dp) {
                                                                            Modifier.width(style.width)
                                                                        } else {
                                                                            Modifier.fillMaxWidth()
                                                                        }
                                                                    )
                                                                    .then(
                                                                        if (scaledSize == null && style.maxWidth.isSpecified && style.maxWidth > 0.dp) {
                                                                            Modifier.widthIn(max = style.maxWidth)
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                                    .then(
                                                                        if (scaledSize == null) {
                                                                            if (block.expectedHeight > 0) {
                                                                                Modifier.height(with(density) { (block.expectedHeight * imageSizeMultiplier).toDp() })
                                                                            } else {
                                                                                Modifier.height(250.dp)
                                                                            }
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )

                                                                AsyncImage(
                                                                    model = imageRequest,
                                                                    contentDescription = block.altText
                                                                        ?: "Image from EPUB",
                                                                    modifier = finalImageModifier,
                                                                    contentScale = imageContentScale(style),
                                                                    colorFilter = colorFilter
                                                                )
                                                            }
                                                        }

                                                        is SpacerBlock -> {
                                                            Box(
                                                                modifier = Modifier.fillMaxWidth()
                                                                    .height(block.height)
                                                                    .drawCssBorders(
                                                                        block.style,
                                                                        density
                                                                    )
                                                            )
                                                        }

                                                        is TableBlock -> {
                                                            Column(modifier = paddingModifier) {
                                                                val stackRows = block.shouldStackRowsForNarrowPagination()
                                                                val rowsForLayout = if (stackRows) {
                                                                    block.rowsForNarrowPaginationLayout()
                                                                } else {
                                                                    block.rows
                                                                }
                                                                rowsForLayout.forEachIndexed { rowIndex, tableRow ->
                                                                    val rowModifier = if (stackRows) {
                                                                        Modifier.fillMaxWidth()
                                                                    } else {
                                                                        Modifier.fillMaxWidth()
                                                                            .height(
                                                                                IntrinsicSize.Min
                                                                            )
                                                                    }
                                                                    val rowTextChars = tableRow.sumOf { rowCell ->
                                                                        rowCell.content.sumOf { it.androidEpubTextCharCount() }
                                                                    }
                                                                    val rowDiagnosticModifier =
                                                                        if (cutoffDiagnosticsEnabled) {
                                                                            Modifier.onGloballyPositioned { coordinates ->
                                                                                logAndroidEpubRenderedTablePartIfNeeded(
                                                                                    pageIndex = pageIndex,
                                                                                    tableBlockIndex = block.blockIndex,
                                                                                    partKind = "row",
                                                                                    rowIndex = rowIndex,
                                                                                    cellIndex = null,
                                                                                    coordinates = coordinates,
                                                                                    pageContentBounds = pageContentBoundsProvider(),
                                                                                    stackRows = stackRows,
                                                                                    tableExpectedHeightPx = block.expectedHeight,
                                                                                    rowCount = rowsForLayout.size,
                                                                                    textChars = rowTextChars,
                                                                                    paddingTopPx = 0,
                                                                                    paddingBottomPx = 0,
                                                                                    isLikelySpeakerCell = false,
                                                                                    diagnosticsContext = cutoffDiagnosticsContext,
                                                                                    signatureAlreadyLogged = { signature ->
                                                                                        cutoffLogSignatures[signature] == true
                                                                                    },
                                                                                    markSignatureLogged = { signature ->
                                                                                        cutoffLogSignatures[signature] = true
                                                                                    }
                                                                                )
                                                                            }
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    Row(
                                                                        rowModifier.then(rowDiagnosticModifier)
                                                                    ) {
                                                                        val hasFixedWidths =
                                                                            !stackRows && tableRow.any {
                                                                                it.style.blockStyle.width != Dp.Unspecified
                                                                            }

                                                                        tableRow.forEachIndexed { cellIndex, cell ->
                                                                            val cellStyle =
                                                                                cell.style.blockStyle

                                                                            val cellContainerModifier =
                                                                                if (stackRows) {
                                                                                    Modifier.fillMaxWidth()
                                                                                } else if (hasFixedWidths) {
                                                                                    if (cellStyle.width != Dp.Unspecified) Modifier.width(
                                                                                        cellStyle.width
                                                                                    )
                                                                                    else Modifier.weight(
                                                                                        cell.colspan.coerceAtLeast(1).toFloat(),
                                                                                        fill = true
                                                                                    )
                                                                                } else {
                                                                                    Modifier.weight(
                                                                                        cell.colspan.coerceAtLeast(1).toFloat(),
                                                                                        fill = true
                                                                                    )
                                                                                }

                                                                            val alignment =
                                                                                if (stackRows) {
                                                                                    Alignment.Start
                                                                                } else {
                                                                                    when (cell.style.paragraphStyle.textAlign) {
                                                                                        TextAlign.Center -> Alignment.CenterHorizontally
                                                                                        TextAlign.End -> Alignment.End
                                                                                        else -> Alignment.Start
                                                                                    }
                                                                                }

                                                                            val stackedCellTopPadding = cellStyle.padding.top.coerceAtLeast(0.dp)
                                                                            val cellTextChars = cell.content.sumOf { it.androidEpubTextCharCount() }
                                                                            val cellPaddingTopPx = with(density) { cellStyle.padding.top.coerceAtLeast(0.dp).roundToPx() }
                                                                            val cellPaddingBottomPx = with(density) { cellStyle.padding.bottom.coerceAtLeast(0.dp).roundToPx() }
                                                                            val cellDiagnosticModifier =
                                                                                if (cutoffDiagnosticsEnabled) {
                                                                                    Modifier.onGloballyPositioned { coordinates ->
                                                                                        logAndroidEpubRenderedTablePartIfNeeded(
                                                                                            pageIndex = pageIndex,
                                                                                            tableBlockIndex = block.blockIndex,
                                                                                            partKind = "cell",
                                                                                            rowIndex = rowIndex,
                                                                                            cellIndex = cellIndex,
                                                                                            coordinates = coordinates,
                                                                                            pageContentBounds = pageContentBoundsProvider(),
                                                                                            stackRows = stackRows,
                                                                                            tableExpectedHeightPx = block.expectedHeight,
                                                                                            rowCount = rowsForLayout.size,
                                                                                            textChars = cellTextChars,
                                                                                            paddingTopPx = cellPaddingTopPx,
                                                                                            paddingBottomPx = cellPaddingBottomPx,
                                                                                            isLikelySpeakerCell = cell.isLikelyDramaSpeakerCell(),
                                                                                            diagnosticsContext = cutoffDiagnosticsContext,
                                                                                            signatureAlreadyLogged = { signature ->
                                                                                                cutoffLogSignatures[signature] == true
                                                                                            },
                                                                                            markSignatureLogged = { signature ->
                                                                                                cutoffLogSignatures[signature] = true
                                                                                            }
                                                                                        )
                                                                                    }
                                                                                } else {
                                                                                    Modifier
                                                                                }
                                                                            val cellModifier =
                                                                                cellContainerModifier
                                                                                    .then(
                                                                                        if (cellStyle.backgroundColor.isSpecified) {
                                                                                            Modifier.background(
                                                                                                cellStyle.backgroundColor
                                                                                            )
                                                                                        } else {
                                                                                            Modifier
                                                                                        }
                                                                                    )
                                                                                    .drawCssBorders(
                                                                                        cellStyle,
                                                                                        density
                                                                                    ).padding(
                                                                                        start = if (stackRows) 0.dp else cellStyle.padding.left.coerceAtLeast(
                                                                                            0.dp
                                                                                        ),
                                                                                        top = if (stackRows) stackedCellTopPadding else cellStyle.padding.top.coerceAtLeast(
                                                                                            0.dp
                                                                                        ),
                                                                                        end = if (stackRows) 0.dp else cellStyle.padding.right.coerceAtLeast(
                                                                                            0.dp
                                                                                        ),
                                                                                        bottom = if (stackRows) 0.dp else cellStyle.padding.bottom.coerceAtLeast(
                                                                                            0.dp
                                                                                        )
                                                                                    )
                                                                                    .then(cellDiagnosticModifier)

                                                                            Column(
                                                                                modifier = cellModifier.wrapContentHeight(Alignment.Top),
                                                                                horizontalAlignment = alignment
                                                                            ) {
                                                                                val cellTextStyle =
                                                                                    if (cell.isHeader) {
                                                                                        textStyle.copy(
                                                                                            fontWeight = FontWeight.Bold
                                                                                        )
                                                                                    } else {
                                                                                        textStyle
                                                                                    }
                                                                                val renderedCellTextStyle = if (stackRows) {
                                                                                    cellTextStyle.copy(textAlign = TextAlign.Left)
                                                                                } else {
                                                                                    cellTextStyle
                                                                                }
                                                                                val cellContentForRender = if (stackRows) {
                                                                                    cell.contentForStackedPaginationMeasurement()
                                                                                } else {
                                                                                    cell.content
                                                                                }

                                                                                cellContentForRender.forEach { blockInCell ->
                                                                                    when (blockInCell) {
                                                                                        is ParagraphBlock -> {
                                                                                            LinkAwareText(
                                                                                                text = if (stackRows) blockInCell.content.withParagraphTextAlignStart() else blockInCell.content,
                                                                                                style = renderedCellTextStyle,
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                isDarkTheme = isDarkTheme,
                                                                                                themeBackgroundColor = effectiveBg,
                                                                                                themeTextColor = effectiveText,
                                                                                                onLinkClick = onLinkClickCallback,
                                                                                                onGeneralTap = onGeneralTapCallback,
                                                                                                wrapDiagnosticsContext = "page=${pageIndex + 1} source=table_cell tableBlock=${block.blockIndex} row=$rowIndex cell=$cellIndex cellBlock=${blockInCell.blockIndex}"
                                                                                            )
                                                                                        }

                                                                                        is HeaderBlock -> {
                                                                                            LinkAwareText(
                                                                                                text = if (stackRows) blockInCell.content.withParagraphTextAlignStart() else blockInCell.content,
                                                                                                style = renderedCellTextStyle.copy(
                                                                                                    fontWeight = FontWeight.Bold
                                                                                                ),
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                isDarkTheme = isDarkTheme,
                                                                                                themeBackgroundColor = effectiveBg,
                                                                                                themeTextColor = effectiveText,
                                                                                                onLinkClick = onLinkClickCallback,
                                                                                                onGeneralTap = onGeneralTapCallback,
                                                                                                wrapDiagnosticsContext = "page=${pageIndex + 1} source=table_cell tableBlock=${block.blockIndex} row=$rowIndex cell=$cellIndex cellBlock=${blockInCell.blockIndex}"
                                                                                            )
                                                                                        }

                                                                                        is ListItemBlock -> {
                                                                                            Row(
                                                                                                verticalAlignment = Alignment.Top
                                                                                            ) {
                                                                                                val itemMarker = blockInCell.itemMarker
                                                                                                if (itemMarker != null) {
                                                                                                    Text(
                                                                                                        text = itemMarker,
                                                                                                        style = renderedCellTextStyle,
                                                                                                        modifier = Modifier.padding(
                                                                                                            end = 4.dp
                                                                                                        )
                                                                                                    )
                                                                                                }
                                                                                                LinkAwareText(
                                                                                                    text = if (stackRows) blockInCell.content.withParagraphTextAlignStart() else blockInCell.content,
                                                                                                    style = renderedCellTextStyle,
                                                                                                    modifier = Modifier.weight(
                                                                                                        1f
                                                                                                    ),
                                                                                                    isDarkTheme = isDarkTheme,
                                                                                                    themeBackgroundColor = effectiveBg,
                                                                                                    themeTextColor = effectiveText,
                                                                                                    onLinkClick = onLinkClickCallback,
                                                                                                    onGeneralTap = onGeneralTapCallback,
                                                                                                    wrapDiagnosticsContext = "page=${pageIndex + 1} source=table_cell tableBlock=${block.blockIndex} row=$rowIndex cell=$cellIndex cellBlock=${blockInCell.blockIndex}"
                                                                                                )
                                                                                            }
                                                                                        }

                                                                                        is SpacerBlock -> {
                                                                                            Spacer(
                                                                                                modifier = Modifier.fillMaxWidth()
                                                                                                    .height(
                                                                                                        blockInCell.height
                                                                                                    )
                                                                                                    .drawCssBorders(
                                                                                                        blockInCell.style,
                                                                                                        density
                                                                                                    )
                                                                                            )
                                                                                        }

                                                                                        is ImageBlock -> if (!hideImages) {
                                                                                            AsyncImage(
                                                                                                model = Builder(
                                                                                                    LocalContext.current
                                                                                                ).data(
                                                                                                    File(
                                                                                                        blockInCell.path
                                                                                                    )
                                                                                                )
                                                                                                    .build(),
                                                                                                contentDescription = blockInCell.altText,
                                                                                                contentScale = imageContentScale(blockInCell.style),
                                                                                                modifier = tableCellImageModifier(
                                                                                                    block = blockInCell,
                                                                                                    density = density,
                                                                                                    imageSizeMultiplier = imageSizeMultiplier
                                                                                                )
                                                                                            )
                                                                                        }

                                                                                        is TextContentBlock -> {
                                                                                            LinkAwareText(
                                                                                                text = if (stackRows) blockInCell.content.withParagraphTextAlignStart() else blockInCell.content,
                                                                                                style = renderedCellTextStyle,
                                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                                isDarkTheme = isDarkTheme,
                                                                                                themeBackgroundColor = effectiveBg,
                                                                                                themeTextColor = effectiveText,
                                                                                                onLinkClick = onLinkClickCallback,
                                                                                                onGeneralTap = onGeneralTapCallback,
                                                                                                wrapDiagnosticsContext = "page=${pageIndex + 1} source=table_cell tableBlock=${block.blockIndex} row=$rowIndex cell=$cellIndex cellBlock=${blockInCell.blockIndex}"
                                                                                            )
                                                                                        }

                                                                                        else -> {}
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        var chapterInfo by remember {
                                            mutableStateOf<Pair<String, Int?>?>(null)
                                        }
                                        LaunchedEffect(pageIndex) {
                                            chapterInfo = onGetChapterInfo(pageIndex)
                                        }

                                        ChapterLoadingPlaceholder(title = chapterInfo?.first)
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeSelection != null) {
                    val sel = activeSelection!!
                    val currentPageSuffix = "_${pagerState.currentPage}"

                    val currentPageBlocks =
                        blockLayoutMap.filterKeys { it.endsWith(currentPageSuffix) }.values.filter { it.second.isAttached }
                    val visibleSelectedBlocks =
                        currentPageBlocks.filter { isBlockSelectedOnPage(it.third, pagerState.currentPage, sel) }

                    LaunchedEffect(sel, pagerState.currentPage) {
                        listOf(true, false).forEach { isStart ->
                            val page = if (isStart) sel.startPageIndex else sel.endPageIndex
                            val cfi = if (isStart) sel.startBaseCfi else sel.endBaseCfi
                            val blockAbs = if (isStart) sel.startBlockCharOffset else sel.endBlockCharOffset
                            val legacyLayout = blockLayoutMap[legacyTextBlockLayoutKey(cfi, page)]
                            val exactLayout = findSelectionLayout(blockLayoutMap, cfi, page, blockAbs)
                            Timber.tag(TAG_READER_INTERACTION_DIAG).d(
                                "selection_handle surface=paginated edge=${if (isStart) "start" else "end"} " +
                                    "page=$page currentPage=${pagerState.currentPage} cfiHash=${cfi.hashCode()} blockAbs=$blockAbs " +
                                    "legacyFound=${legacyLayout != null} legacyBlockAbs=${legacyLayout?.third?.let(::getTextBlockCharOffset)} " +
                                    "exactFound=${exactLayout != null} attached=${exactLayout?.second?.isAttached} " +
                                    "rootAttached=${rootCoords?.isAttached} visibleSelectedBlocks=${visibleSelectedBlocks.size} " +
                                    "locale=${context.resources.configuration.locales[0]} " +
                                    "layoutDirection=${context.resources.configuration.layoutDirection}"
                            )
                        }
                    }

                    if (!isDraggingHandle && visibleSelectedBlocks.isNotEmpty()) {
                        val menuAnchorRect = run {
                            var minLeft = Float.MAX_VALUE
                            var minTop = Float.MAX_VALUE
                            var maxRight = Float.MIN_VALUE
                            var maxBottom = Float.MIN_VALUE

                            visibleSelectedBlocks.forEach { triple ->
                                val (textLayout, coords, block) = triple

                                val currentBlockAbs = getTextBlockCharOffset(block)
                                val isStartBlockPart =
                                    pagerState.currentPage == sel.startPageIndex &&
                                        block.blockIndex == sel.startBlockIndex &&
                                        currentBlockAbs == sel.startBlockCharOffset
                                val isEndBlockPart =
                                    pagerState.currentPage == sel.endPageIndex &&
                                        block.blockIndex == sel.endBlockIndex &&
                                        currentBlockAbs == sel.endBlockCharOffset

                                val blockStartOffset = if (isStartBlockPart) sel.startOffset else 0
                                val blockEndOffset = if (isEndBlockPart) sel.endOffset else textLayout.layoutInput.text.length

                                val textLen = textLayout.layoutInput.text.length
                                val maxIdx = maxOf(0, textLen - 1)

                                val safeStart = blockStartOffset.coerceIn(0, textLen)
                                val safeEnd = blockEndOffset.coerceIn(safeStart, textLen)

                                if (safeStart < safeEnd) {
                                    try {
                                        val startBox = textLayout.getBoundingBox(safeStart.coerceIn(0, maxIdx))
                                        val endBox = textLayout.getBoundingBox((safeEnd - 1).coerceIn(0, maxIdx))

                                        val topWin =
                                            coords.localToWindow(Offset(0f, startBox.top)).y
                                        val bottomWin =
                                            coords.localToWindow(Offset(0f, endBox.bottom)).y
                                        val leftWin1 =
                                            coords.localToWindow(Offset(startBox.left, 0f)).x
                                        val rightWin1 =
                                            coords.localToWindow(Offset(startBox.right, 0f)).x
                                        val leftWin2 =
                                            coords.localToWindow(Offset(endBox.left, 0f)).x
                                        val rightWin2 =
                                            coords.localToWindow(Offset(endBox.right, 0f)).x

                                        minTop = minOf(minTop, topWin)
                                        maxBottom = maxOf(maxBottom, bottomWin)
                                        minLeft = minOf(minLeft, leftWin1, leftWin2)
                                        maxRight = maxOf(maxRight, rightWin1, rightWin2)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error calculating exact selection bounds")
                                    }
                                }
                            }

                            val handleSizePx = with(density) { 36.dp.toPx() }
                            if (minTop != Float.MAX_VALUE && maxBottom != Float.MIN_VALUE) {
                                Rect(minLeft, minTop, maxRight, maxBottom + handleSizePx)
                            } else {
                                Rect(
                                    sel.rect.left,
                                    sel.rect.top,
                                    sel.rect.right,
                                    sel.rect.bottom + handleSizePx
                                )
                            }
                        }

                        Popup(
                            popupPositionProvider = remember(
                                menuAnchorRect,
                                density
                            ) { SmartPopupPositionProvider(menuAnchorRect, density) },
                            onDismissRequest = { activeSelection = null },
                            properties = PopupProperties(
                                dismissOnClickOutside = false
                            )
                        ) {
                            PaginatedTextSelectionMenu(
                                onCopy = {
                                    val copied = copyPlainTextToClipboard(
                                        context = context,
                                        label = context.getString(R.string.clip_label_copied_text),
                                        text = sel.text
                                    )
                                    if (!copied) {
                                        Toast.makeText(context, context.getString(R.string.error_copy_to_clipboard), Toast.LENGTH_SHORT).show()
                                    }
                                    activeSelection = null
                                },
                                onSelectAll = null,
                                onDictionary = {
                                    if (isProUser || countWords(sel.text) <= 1) {
                                        onWordSelectedForAiDefinition(sel.text)
                                    } else {
                                        onShowDictionaryUpsellDialog()
                                    }
                                    activeSelection = null
                                },
                                onTranslate = {
                                    onTranslate(sel.text)
                                    activeSelection = null
                                },
                                onSearch = {
                                    onSearch(sel.text)
                                    activeSelection = null
                                },
                                onHighlight = { color, style ->
                                    val startAbsoluteOffset = sel.startBlockCharOffset + sel.startOffset
                                    val endAbsoluteOffset = sel.endBlockCharOffset + sel.endOffset
                                    val finalCfi =
                                        "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                    val absoluteCandidateCfi =
                                        "${sel.startBaseCfi}:$startAbsoluteOffset|${sel.endBaseCfi}:$endAbsoluteOffset"
                                    val locator = sel.toSharedHighlightLocator(
                                        chapterIndex = onGetChapterIndex(sel.startPageIndex),
                                        cfi = finalCfi
                                    )
                                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                        "create_request source=highlight_menu colorArgb=$color " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                        "create_request surface=paginated action=highlight colorArgb=$color " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "locator=${locator} textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    onHighlightCreated(finalCfi, sel.text, color.toString(), locator, style)
                                    activeSelection = null
                                },
                                onNote = { style ->
                                    onNoteRequested(null)
                                    val startAbsoluteOffset = sel.startBlockCharOffset + sel.startOffset
                                    val endAbsoluteOffset = sel.endBlockCharOffset + sel.endOffset
                                    val finalCfi =
                                        "${sel.startBaseCfi}:${sel.startOffset}|${sel.endBaseCfi}:${sel.endOffset}"
                                    val absoluteCandidateCfi =
                                        "${sel.startBaseCfi}:$startAbsoluteOffset|${sel.endBaseCfi}:$endAbsoluteOffset"
                                    val locator = sel.toSharedHighlightLocator(
                                        chapterIndex = onGetChapterIndex(sel.startPageIndex),
                                        cfi = finalCfi
                                    )
                                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                        "create_request source=note_menu color=${HighlightColor.YELLOW.id} " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                        "create_request surface=paginated action=note color=${HighlightColor.YELLOW.id} " +
                                            "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                            "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                            "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                            "startBaseCfi=${sel.startBaseCfi} endBaseCfi=${sel.endBaseCfi} " +
                                            "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                            "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                            "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                            "locator=${locator} textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                    )
                                    onHighlightCreated(finalCfi, sel.text, (activeHighlightPalette.firstOrNull() ?: HighlightColor.YELLOW.color.toArgb()).toString(), locator, style)
                                    activeSelection = null
                                },
                                onTts = {
                                    val startAbs = sel.startOffset + sel.startBlockCharOffset
                                    onStartTtsFromSelection(sel.startBaseCfi, startAbs)
                                    activeSelection = null
                                },
                                onDelete = null,
                                isProUser = isProUser,
                                isOss = isOss,
                                activeHighlightPalette = activeHighlightPalette,
                                onOpenPaletteManager = { showPaletteManager = true })
                        }
                    }

                    val updateSelection: (Offset, SelectionHandle) -> SelectionHandle =
                        { windowPos, currentDragHandle ->
                            var activeDragHandle = currentDragHandle

                            val attachedBlocks =
                                blockLayoutMap.filterKeys { it.endsWith(currentPageSuffix) }.values.filter { it.second.isAttached }
                                    .sortedBy { it.second.positionInWindow().y }

                            if (attachedBlocks.isNotEmpty()) {
                                val targetTriple = attachedBlocks.minByOrNull {
                                    val coords = it.second
                                    val rect = Rect(coords.positionInWindow(), coords.size.toSize())
                                    val dx =
                                        maxOf(rect.left - windowPos.x, 0f, windowPos.x - rect.right)
                                    val dy =
                                        maxOf(rect.top - windowPos.y, 0f, windowPos.y - rect.bottom)
                                    dx * dx + dy * dy
                                } ?: attachedBlocks.last()

                                val (textLayout, coords, block) = targetTriple
                                val localPos = coords.windowToLocal(windowPos)
                                val offset = textLayout.getOffsetForPosition(localPos)
                                    .coerceIn(0, textLayout.layoutInput.text.length)

                                val isStartHandle = activeDragHandle == SelectionHandle.START
                                var newStartIdx =
                                    if (isStartHandle) block.blockIndex else sel.startBlockIndex
                                var newEndIdx =
                                    if (isStartHandle) sel.endBlockIndex else block.blockIndex
                                var newStartOffset = if (isStartHandle) offset else sel.startOffset
                                var newEndOffset = if (isStartHandle) sel.endOffset else offset
                                var newStartCfi = if (isStartHandle) block.cfi!! else sel.startBaseCfi
                                var newEndCfi = if (isStartHandle) sel.endBaseCfi else block.cfi!!
                                var newStartPageIdx = if (isStartHandle) pagerState.currentPage else sel.startPageIndex
                                var newEndPageIdx = if (isStartHandle) sel.endPageIndex else pagerState.currentPage

                                val currentBlockAbs = getTextBlockCharOffset(block)
                                var newStartBlockAbs = if (isStartHandle) currentBlockAbs else sel.startBlockCharOffset
                                var newEndBlockAbs = if (!isStartHandle) currentBlockAbs else sel.endBlockCharOffset

                                val isReversed = when {
                                    newStartPageIdx != newEndPageIdx -> newStartPageIdx > newEndPageIdx
                                    else -> {
                                        val blockCompare = compareBlockPositionsOnPage(
                                            newStartIdx,
                                            newStartBlockAbs,
                                            newEndIdx,
                                            newEndBlockAbs
                                        )
                                        if (blockCompare != 0) blockCompare > 0 else newStartOffset > newEndOffset
                                    }
                                }

                                if (isReversed) {
                                    newStartPageIdx = newEndPageIdx.also { newEndPageIdx = newStartPageIdx }
                                    newStartIdx = newEndIdx.also { newEndIdx = newStartIdx }
                                    newStartOffset = newEndOffset.also { newEndOffset = newStartOffset }
                                    newStartCfi = newEndCfi.also { newEndCfi = newStartCfi }
                                    newStartBlockAbs = newEndBlockAbs.also { newEndBlockAbs = newStartBlockAbs }
                                    activeDragHandle = if (activeDragHandle == SelectionHandle.START) SelectionHandle.END else SelectionHandle.START
                                }

                                if (
                                    newStartPageIdx != sel.startPageIndex ||
                                    newEndPageIdx != sel.endPageIndex ||
                                    newStartIdx != sel.startBlockIndex ||
                                    newEndIdx != sel.endBlockIndex ||
                                    newStartOffset != sel.startOffset ||
                                    newEndOffset != sel.endOffset
                                ) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    val relevantBlocks = attachedBlocks
                                        .filter {
                                            isBlockSelectedOnPage(
                                                block = it.third,
                                                pageIndex = pagerState.currentPage,
                                                selection = PaginatedSelection(
                                                    startBlockIndex = newStartIdx,
                                                    endBlockIndex = newEndIdx,
                                                    startBaseCfi = newStartCfi,
                                                    endBaseCfi = newEndCfi,
                                                    startOffset = newStartOffset,
                                                    endOffset = newEndOffset,
                                                    text = sel.text,
                                                    rect = sel.rect,
                                                    startPageIndex = newStartPageIdx,
                                                    endPageIndex = newEndPageIdx,
                                                    startBlockCharOffset = newStartBlockAbs,
                                                    endBlockCharOffset = newEndBlockAbs,
                                                    textPerBlock = sel.textPerBlock
                                                )
                                            )
                                        }
                                        .sortedWith(compareBy({ it.third.blockIndex }, { getTextBlockCharOffset(it.third) }))

                                    val newTextPerBlock = sel.textPerBlock.toMutableMap()
                                    newTextPerBlock.keys.removeAll { keyStr ->
                                        parseSelectionBlockKey(keyStr)?.pageIndex == pagerState.currentPage
                                    }

                                    for (b in relevantBlocks) {
                                        val txt = b.third.content.text
                                        val bAbs = getTextBlockCharOffset(b.third)
                                        val isStartBlockPart = b.third.blockIndex == newStartIdx && bAbs == newStartBlockAbs
                                        val isEndBlockPart = b.third.blockIndex == newEndIdx && bAbs == newEndBlockAbs

                                        val s = if (isStartBlockPart) newStartOffset else 0
                                        val e = if (isEndBlockPart) newEndOffset else txt.length

                                        val safeS = s.coerceIn(0, txt.length)
                                        val safeE = e.coerceIn(safeS, txt.length)

                                        if (safeS < safeE) {
                                            newTextPerBlock[
                                                buildSelectionBlockKey(
                                                    pageIndex = pagerState.currentPage,
                                                    blockIndex = b.third.blockIndex,
                                                    blockCharOffset = bAbs
                                                )
                                            ] = txt.substring(safeS, safeE)
                                        } else {
                                            newTextPerBlock.remove(
                                                buildSelectionBlockKey(
                                                    pageIndex = pagerState.currentPage,
                                                    blockIndex = b.third.blockIndex,
                                                    blockCharOffset = bAbs
                                                )
                                            )
                                        }
                                    }

                                    val newText = newTextPerBlock.entries
                                        .sortedWith { first, second ->
                                            compareSelectionBlockKeys(first.key, second.key)
                                        }
                                        .joinToString(" ") { it.value }

                                    val sLayout = blockLayoutMap["${newStartCfi}_$newStartPageIdx"]?.takeIf {
                                        val abs = getTextBlockCharOffset(it.third)
                                        abs == newStartBlockAbs
                                    }

                                    val eLayout = blockLayoutMap["${newEndCfi}_$newEndPageIdx"]
                                    var newRect = sel.rect

                                    if (sLayout != null && eLayout != null && sLayout.second.isAttached && eLayout.second.isAttached) {
                                        val sMaxIdx = maxOf(0, sLayout.first.layoutInput.text.length - 1)
                                        val eMaxIdx = maxOf(0, eLayout.first.layoutInput.text.length - 1)

                                        val sRectLocal = sLayout.first.getBoundingBox(
                                            newStartOffset.coerceIn(0, sMaxIdx)
                                        )
                                        val sRectWin = Rect(
                                            sLayout.second.localToWindow(sRectLocal.topLeft),
                                            sLayout.second.localToWindow(sRectLocal.bottomRight)
                                        )
                                        val eRectLocal = eLayout.first.getBoundingBox(
                                            (newEndOffset - 1).coerceIn(0, eMaxIdx)
                                        )
                                        val eRectWin = Rect(
                                            eLayout.second.localToWindow(eRectLocal.topLeft),
                                            eLayout.second.localToWindow(eRectLocal.bottomRight)
                                        )
                                        newRect = Rect(
                                            minOf(sRectWin.left, eRectWin.left),
                                            sRectWin.top,
                                            maxOf(sRectWin.right, eRectWin.right),
                                            eRectWin.bottom
                                        )
                                    } else {
                                        var minLeft = Float.MAX_VALUE
                                        var minTop = Float.MAX_VALUE
                                        var maxRight = Float.MIN_VALUE
                                        var maxBottom = Float.MIN_VALUE
                                        relevantBlocks.forEach { b ->
                                            if (b.second.isAttached) {
                                                val r = Rect(
                                                    b.second.positionInWindow(),
                                                    b.second.size.toSize()
                                                )
                                                minLeft = minOf(minLeft, r.left)
                                                minTop = minOf(minTop, r.top)
                                                maxRight = maxOf(maxRight, r.right)
                                                maxBottom = maxOf(maxBottom, r.bottom)
                                            }
                                        }
                                        if (minLeft != Float.MAX_VALUE) {
                                            newRect = Rect(minLeft, minTop, maxRight, maxBottom)
                                        }
                                    }

                                    activeSelection = PaginatedSelection(
                                        startBlockIndex = newStartIdx,
                                        endBlockIndex = newEndIdx,
                                        startBaseCfi = newStartCfi,
                                        endBaseCfi = newEndCfi,
                                        startOffset = newStartOffset,
                                        endOffset = newEndOffset,
                                        text = newText,
                                        rect = newRect,
                                        startPageIndex = newStartPageIdx,
                                        endPageIndex = newEndPageIdx,
                                        startBlockCharOffset = newStartBlockAbs,
                                        endBlockCharOffset = newEndBlockAbs,
                                        textPerBlock = newTextPerBlock
                                    )
                                }
                            }
                            activeDragHandle
                        }

                    val latestUpdateSelection by rememberUpdatedState(updateSelection)

                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = ReaderSelectionHandleOverlayAlignment
                    ) {
                        listOf(SelectionHandle.START, SelectionHandle.END).forEach { handleType ->
                        val isStart = handleType == SelectionHandle.START
                        var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    @Suppress("UNUSED_VARIABLE") val animOffset = pagerState.currentPageOffsetFraction
                                    @Suppress("UNUSED_VARIABLE") val currPage = pagerState.currentPage
                                    @Suppress("UNUSED_VARIABLE") val isScrolling = pagerState.isScrollInProgress
                                    @Suppress("UNUSED_VARIABLE") val tick = blockLayoutMap.tick

                                    val handlePageIndex = if (isStart) sel.startPageIndex else sel.endPageIndex
                                    val pos = if (handlePageIndex == pagerState.currentPage) {
                                        val selCfi = if (isStart) sel.startBaseCfi else sel.endBaseCfi
                                        val selOffset = if (isStart) sel.startOffset else sel.endOffset
                                        val targetBlockAbs = if (isStart) sel.startBlockCharOffset else sel.endBlockCharOffset
                                        val layoutInfo = blockLayoutMap["${selCfi}_$handlePageIndex"]?.takeIf {
                                            val blockAbs = getTextBlockCharOffset(it.third)
                                            blockAbs == targetBlockAbs
                                        }

                                        if (layoutInfo != null && layoutInfo.second.isAttached && rootCoords != null && rootCoords!!.isAttached) {
                                            val textLayout = layoutInfo.first
                                            val coords = layoutInfo.second
                                            val maxIdx = maxOf(0, textLayout.layoutInput.text.length - 1)
                                            val safeOffset = selOffset.coerceIn(0, textLayout.layoutInput.text.length)
                                            val safeOffsetForLine = safeOffset.coerceIn(0, maxIdx)

                                            val line = textLayout.getLineForOffset(safeOffsetForLine)
                                            val x = textLayout.getHorizontalPosition(safeOffset, usePrimaryDirection = true)
                                            val y = textLayout.getLineBottom(line)

                                            try {
                                                val windowPos = coords.localToWindow(Offset(x, y))
                                                rootCoords!!.windowToLocal(windowPos)
                                            } catch (e: Exception) {
                                                Offset.Unspecified
                                            }
                                        } else {
                                            Offset.Unspecified
                                        }
                                    } else {
                                        Offset.Unspecified
                                    }

                                    if (pos.isSpecified) {
                                        translationX = pos.x - 18.dp.toPx()
                                        translationY = pos.y
                                        alpha = 1f
                                    } else {
                                        alpha = 0f
                                    }
                                }
                                .size(36.dp)
                                .onGloballyPositioned { handleCoords = it }
                                .pointerInput(handleType) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        isDraggingHandle = true
                                        var currentDragHandle = handleType

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()

                                            if (handleCoords != null && rootCoords != null && handleCoords!!.isAttached && rootCoords!!.isAttached) {
                                                try {
                                                    val pointerWindow = handleCoords!!.localToWindow(change.position)
                                                    val pointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                                    val targetRootY = pointerRoot.y - 36.dp.toPx()
                                                    val targetRootPos = Offset(pointerRoot.x, targetRootY)

                                                    magnifierCenter = targetRootPos

                                                    val targetWindowPos = rootCoords!!.localToWindow(targetRootPos)
                                                    currentDragHandle = latestUpdateSelection(targetWindowPos, currentDragHandle)
                                                } catch (e: Exception) {
                                                    // Ignore detachment crashes during fast scrolls
                                                }
                                            }
                                        }
                                        isDraggingHandle = false
                                        magnifierCenter = Offset.Unspecified
                                    }
                                },
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.teardrop),
                                contentDescription = if (isStart) "Start handle" else "End handle",
                                modifier = Modifier.size(36.dp).graphicsLayer {
                                    rotationZ = if (isStart) 30f else -30f
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                },
                                tint = Color(0xFF1976D2)
                            )
                        }
                        }
                    }
                }

                if (showPaletteManager) {
                    PaletteManagerDialog(
                        currentPalette = activeHighlightPalette,
                        onDismiss = { showPaletteManager = false },
                        onSave = { newPalette ->
                            newPalette.forEachIndexed { index, color ->
                                onUpdatePalette(index, color)
                            }
                            showPaletteManager = false
                        })
                }
            }
        } else {
            Timber.w("Book has no pages to display.")
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.msg_book_no_content))
            }
        }
    }
}

@Composable
internal fun ChapterLoadingPlaceholder(title: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(16.dp))
            }
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Preparing chapter.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
internal fun RenderFlexChildBlock(
    childBlock: ContentBlock,
    textStyle: TextStyle,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false,
    searchQuery: String,
    searchHighlightColor: Color,
    ttsHighlightInfo: TtsHighlightInfo?,
    ttsHighlightColor: Color,
    textMeasurer: TextMeasurer,
    onLinkClickCallback: (String) -> Unit,
    onGeneralTapCallback: (Offset) -> Unit,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    blockLayoutMap: MutableMap<String, Triple<TextLayoutResult, LayoutCoordinates, TextContentBlock>>,
    density: Density,
    imageLoader: ImageLoader,
    pageIndex: Int,
    registerStableLayoutKey: Boolean = false
) {
    @Composable
    fun renderTextBlock(block: TextContentBlock) {
        val searchHighlighted =
            highlightQueryInText(block.content, searchQuery, searchHighlightColor)
        val finalContent = if (ttsHighlightInfo != null && block.cfi == ttsHighlightInfo.cfi) {
            buildAnnotatedString {
                append(searchHighlighted)
                val blockStartAbs = block.startCharOffsetInSource
                val blockEndAbs = block.startCharOffsetInSource + searchHighlighted.length
                val highlightStartAbs = ttsHighlightInfo.offset
                val highlightEndAbs = ttsHighlightInfo.offset + ttsHighlightInfo.text.length

                val intersectionStartAbs = maxOf(blockStartAbs, highlightStartAbs)
                val intersectionEndAbs = minOf(blockEndAbs, highlightEndAbs)

                if (intersectionStartAbs < intersectionEndAbs) {
                    val highlightStartRelative = intersectionStartAbs - blockStartAbs
                    val highlightEndRelative = intersectionEndAbs - blockStartAbs
                    addStyle(
                        style = SpanStyle(background = ttsHighlightColor),
                        start = highlightStartRelative,
                        end = highlightEndRelative
                    )
                }
            }
        } else {
            searchHighlighted
        }

        val finalStyle = when (block) {
            is HeaderBlock -> createHeaderTextStyle(
                baseStyle = textStyle,
                level = block.level,
                textAlign = block.textAlign
            )
            is ParagraphBlock -> textStyle.copy(textAlign = block.textAlign ?: textStyle.textAlign)
            is QuoteBlock -> textStyle.copy(textAlign = block.textAlign ?: textStyle.textAlign)
            is ListItemBlock -> textStyle
        }

        TextWithEmphasis(
            text = finalContent,
            style = finalStyle,
            modifier = Modifier,
            pageIndex = pageIndex,
            textMeasurer = textMeasurer,
            onLinkClick = onLinkClickCallback,
            onGeneralTap = onGeneralTapCallback,
            block = block,
            userHighlights = userHighlights,
            activeSelection = activeSelection,
            onSelectionChange = onSelectionChange,
            onHighlightClick = onHighlightClick,
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor,
            onRegisterLayout = { layout, coords ->
                block.cfi?.let { cfi ->
                    val key = if (registerStableLayoutKey) {
                        textBlockLayoutKey(cfi, pageIndex, block)
                    } else {
                        legacyTextBlockLayoutKey(cfi, pageIndex)
                    }
                    blockLayoutMap[key] = Triple(layout, coords, block)
                }
            })
    }

    when (childBlock) {
        is ListItemBlock -> {
            Row(modifier = Modifier, verticalAlignment = Alignment.Top) {
                val markerAreaModifier = Modifier
                    .width(32.dp)
                    .padding(end = 8.dp)
                val itemMarkerImage = childBlock.itemMarkerImage
                val itemMarker = childBlock.itemMarker

                if (itemMarkerImage != null) {
                    val imageRequest =
                        Builder(LocalContext.current).data(nativeVerticalImageModelData(itemMarkerImage))
                            .crossfade(true).build()
                    val imageSize = with(density) { (textStyle.fontSize.value * 0.8f).sp.toDp() }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.content_desc_list_item_marker),
                        modifier = markerAreaModifier.height(imageSize),
                        alignment = Alignment.CenterEnd,
                        contentScale = ContentScale.FillHeight
                    )
                } else if (itemMarker != null) {
                    Text(
                        text = itemMarker,
                        style = textStyle.copy(textAlign = TextAlign.End),
                        modifier = markerAreaModifier
                    )
                }

                // Reuse text rendering logic
                renderTextBlock(childBlock)
            }
        }

        is ParagraphBlock -> renderTextBlock(childBlock)
        is HeaderBlock -> renderTextBlock(childBlock)
        is QuoteBlock -> renderTextBlock(childBlock)
        is TextContentBlock -> renderTextBlock(childBlock)
        is ImageBlock -> if (!hideImages) {
            val style = childBlock.style
            val colorFilter = if (childBlock.style.filter == "invert(100%)") {
                val matrix = floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f
                )
                ColorFilter.colorMatrix(ColorMatrix(matrix))
            } else null

            BoxWithConstraints(contentAlignment = imageBlockContentAlignment(style)) {
                val scaledSize = computeImageRenderSizeDp(
                    block = childBlock,
                    density = density,
                    maxWidthDp = maxWidth,
                    imageSizeMultiplier = imageSizeMultiplier
                )
                val imageModifier = Modifier
                    .then(
                        if (scaledSize != null) {
                            Modifier.width(scaledSize.first).height(scaledSize.second)
                        } else if (style.width != Dp.Unspecified && style.width > 0.dp) {
                            Modifier.width(style.width)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .then(
                        if (scaledSize == null && style.maxWidth != Dp.Unspecified && style.maxWidth > 0.dp) {
                            Modifier.widthIn(max = style.maxWidth)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (scaledSize == null) {
                            if (childBlock.expectedHeight > 0) {
                                Modifier.height(with(density) { (childBlock.expectedHeight * imageSizeMultiplier).toDp() })
                            } else {
                                Modifier.height(250.dp)
                            }
                        } else {
                            Modifier
                        }
                    )

                AsyncImage(
                    model = Builder(LocalContext.current).data(nativeVerticalImageModelData(childBlock.path)).crossfade(true)
                        .build(),
                    contentDescription = childBlock.altText,
                    modifier = imageModifier,
                    contentScale = imageContentScale(childBlock.style),
                    colorFilter = colorFilter,
                    imageLoader = imageLoader
                )
            }
        }

        is SpacerBlock -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(childBlock.height)
                    .drawCssBorders(childBlock.style, density)
            )
        }

        is TableBlock -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                val stackRows = childBlock.shouldStackRowsForNarrowPagination()
                val rowsForLayout = if (stackRows) {
                    childBlock.rowsForNarrowPaginationLayout()
                } else {
                    childBlock.rows
                }
                rowsForLayout.forEachIndexed { rowIndex, tableRow ->
                    val rowModifier = if (stackRows) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    }
                    Row(rowModifier) {
                        val hasFixedWidths =
                            !stackRows && tableRow.any { it.style.blockStyle.width != Dp.Unspecified }

                        tableRow.forEachIndexed { cellIndex, cell ->
                            val cellStyle = cell.style.blockStyle
                            val stackedCellTopPadding = cellStyle.padding.top.coerceAtLeast(0.dp)
                            val cellContainerModifier = if (stackRows) {
                                Modifier.fillMaxWidth()
                            } else if (hasFixedWidths && cellStyle.width != Dp.Unspecified) {
                                Modifier.width(cellStyle.width)
                            } else {
                                Modifier.weight(cell.colspan.coerceAtLeast(1).toFloat(), fill = true)
                            }
                            val cellModifier = cellContainerModifier
                                .then(
                                    if (cellStyle.backgroundColor.isSpecified) Modifier.background(
                                        cellStyle.backgroundColor
                                    )
                                    else Modifier
                                )
                                .drawCssBorders(cellStyle, density)
                                .padding(
                                    start = if (stackRows) 0.dp else cellStyle.padding.left.coerceAtLeast(
                                        0.dp
                                    ),
                                    top = if (stackRows) stackedCellTopPadding else cellStyle.padding.top.coerceAtLeast(0.dp),
                                    end = if (stackRows) 0.dp else cellStyle.padding.right.coerceAtLeast(
                                        0.dp
                                    ),
                                    bottom = if (stackRows) 0.dp else cellStyle.padding.bottom.coerceAtLeast(
                                        0.dp
                                    )
                                )

                            val alignment = if (stackRows) {
                                Alignment.Start
                            } else {
                                when (cell.style.paragraphStyle.textAlign) {
                                    TextAlign.Center -> Alignment.CenterHorizontally
                                    TextAlign.End -> Alignment.End
                                    else -> Alignment.Start
                                }
                            }

                            Column(
                                modifier = cellModifier.wrapContentHeight(Alignment.Top),
                                horizontalAlignment = alignment
                            ) {
                                val cellTextStyle =
                                    if (cell.isHeader) textStyle.copy(fontWeight = FontWeight.Bold)
                                    else textStyle
                                val renderedCellTextStyle = if (stackRows) {
                                    cellTextStyle.copy(textAlign = TextAlign.Left)
                                } else {
                                    cellTextStyle
                                }
                                val cellContentForRender = if (stackRows) {
                                    cell.contentForStackedPaginationMeasurement()
                                } else {
                                    cell.content
                                }

                                cellContentForRender.forEach { blockInCell ->
                                    if (blockInCell is TextContentBlock) {
                                        LinkAwareText(
                                            text = if (stackRows) blockInCell.content.withParagraphTextAlignStart() else blockInCell.content,
                                            style = renderedCellTextStyle,
                                            modifier = Modifier.fillMaxWidth(),
                                            isDarkTheme = isDarkTheme,
                                            themeBackgroundColor = themeBackgroundColor,
                                            themeTextColor = themeTextColor,
                                            onLinkClick = onLinkClickCallback,
                                            onGeneralTap = onGeneralTapCallback,
                                            wrapDiagnosticsContext = "page=${pageIndex + 1} source=flex_table_cell tableBlock=${childBlock.blockIndex} row=$rowIndex cell=$cellIndex cellBlock=${blockInCell.blockIndex}"
                                        )
                                    } else if (blockInCell is ImageBlock && !hideImages) {
                                        AsyncImage(
                                            model = Builder(LocalContext.current).data(
                                                nativeVerticalImageModelData(blockInCell.path)
                                            ).build(),
                                            contentDescription = blockInCell.altText,
                                            contentScale = imageContentScale(blockInCell.style),
                                            modifier = tableCellImageModifier(
                                                block = blockInCell,
                                                density = density,
                                                imageSizeMultiplier = imageSizeMultiplier
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is FlexContainerBlock -> {
            val content: @Composable () -> Unit = {
                childBlock.children.forEach { nested ->
                    RenderFlexChildBlock(
                        childBlock = nested,
                        textStyle = textStyle,
                        imageSizeMultiplier = imageSizeMultiplier,
                        hideImages = hideImages,
                        searchQuery = searchQuery,
                        searchHighlightColor = searchHighlightColor,
                        ttsHighlightInfo = ttsHighlightInfo,
                        ttsHighlightColor = ttsHighlightColor,
                        textMeasurer = textMeasurer,
                        onLinkClickCallback = onLinkClickCallback,
                        onGeneralTapCallback = onGeneralTapCallback,
                        userHighlights = userHighlights,
                        activeSelection = activeSelection,
                        onSelectionChange = onSelectionChange,
                        onHighlightClick = onHighlightClick,
                        isDarkTheme = isDarkTheme,
                        themeBackgroundColor = themeBackgroundColor,
                        themeTextColor = themeTextColor,
                        blockLayoutMap = blockLayoutMap,
                        density = density,
                        imageLoader = imageLoader,
                        pageIndex = pageIndex,
                        registerStableLayoutKey = registerStableLayoutKey
                    )
                }
            }
            if (childBlock.style.flexDirection == "row") {
                Row { content() }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
            }
        }

        else -> {
            Timber.w(
                "FlexContainerBlock child type still not supported: ${childBlock::class.simpleName}"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.realisticBookPage(
    pagerState: PagerState,
    pageIndex: Int,
    paperColor: Color,
    isDarkTheme: Boolean,
    touchY: Float?,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f
): Modifier = composed {

    val frontPath = remember { Path() }
    val backPath = remember { Path() }
    val reflectedScreenPath = remember { Path() }

    this
        .graphicsLayer {
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction

            if (abs(pageOffset) > 0.001f && abs(pageOffset) < 0.999f) {
                Timber.tag("PageTurnFixDiag").d("graphicsLayer: Page $pageIndex, Offset: $pageOffset")
            }

            if (pageOffset <= 1f && pageOffset > -1f) {
                translationX = -pageOffset * size.width
            }

            if (pageOffset != 0f) {
                shadowElevation = 10f
                shape = RectangleShape
                clip = false
            }
        }
        .drawWithContent {
            val drawStart = System.nanoTime()
            val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
            fun drawPaperBackground() {
                drawRect(color = paperColor)
                if (textureBitmap != null && textureAlpha > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                        blendMode = BlendMode.SrcOver,
                        alpha = textureAlpha
                    )
                }
            }

            if (abs(pageOffset) < 0.001f) {
                drawPaperBackground()
                drawContent()
            }
            else if (pageOffset < 0f && pageOffset > -1f) {
                val progress = -pageOffset
                val w = size.width
                val h = size.height

                val startY = touchY ?: h
                val rawCenterDist = ((startY - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)

                val flattenFactor = if (progress > 0.75f) {
                    ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val centerDist = rawCenterDist * (1f - flattenFactor)

                val cornerY = if (centerDist >= 0) h else 0f

                val dragX = w - w * 2.2f * progress
                val dragY = cornerY - h * 0.5f * progress * centerDist

                val midX = (w + dragX) / 2f
                val midY = (cornerY + dragY) / 2f

                val dx = w - dragX
                val dy = cornerY - dragY
                val nLen = sqrt(dx * dx + dy * dy)

                // CRITICAL GEOMETRY LOG
                if (progress > 0.8f) { // Focus logs on the "end" of the turn where the stall happens
                    Timber.tag("PageTurnFixDiag").i(
                        "Geometry Page $pageIndex: progress=$progress, nLen=$nLen, cornerY=$cornerY, dragX=$dragX, midX=$midX"
                    )
                }

                if (nLen > 0f) {
                    val nx = dx / nLen
                    val ny = dy / nLen

                    if (nx.isNaN() || ny.isNaN()) {
                        Timber.tag("PageTurnFixDiag").e("NAN DETECTED in Normal Vectors: nx=$nx, ny=$ny")
                    }

                    val huge = w * 3f
                    val vx = -ny

                    val p1X = midX + vx * huge
                    val p1Y = midY + nx * huge
                    val p2X = midX - vx * huge
                    val p2Y = midY - nx * huge

                    frontPath.rewind()
                    frontPath.moveTo(p1X, p1Y)
                    frontPath.lineTo(p2X, p2Y)
                    frontPath.lineTo(p2X - nx * huge, p2Y - ny * huge)
                    frontPath.lineTo(p1X - nx * huge, p1Y - ny * huge)
                    frontPath.close()

                    clipPath(frontPath) {
                        drawPaperBackground()
                        this@drawWithContent.drawContent()
                    }

                    val shadowWidth = (40.dp.toPx() * (1f - progress)).coerceAtLeast(10.dp.toPx())
                    backPath.rewind()
                    backPath.moveTo(p1X, p1Y)
                    backPath.lineTo(p2X, p2Y)
                    backPath.lineTo(p2X + nx * huge, p2Y + ny * huge)
                    backPath.lineTo(p1X + nx * huge, p1Y + ny * huge)
                    backPath.close()

                    val dropShadowBrush = Brush.linearGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
                        start = Offset(midX, midY),
                        end = Offset(midX + nx * shadowWidth, midY + ny * shadowWidth)
                    )
                    clipRect(0f, 0f, w, h) {
                        drawPath(backPath, dropShadowBrush)
                    }

                    fun reflect(px: Float, py: Float): Offset {
                        val vX = px - midX
                        val vY = py - midY
                        val dist = vX * nx + vY * ny
                        return Offset(px - 2 * dist * nx, py - 2 * dist * ny)
                    }

                    val rTL = reflect(0f, 0f)
                    val rTR = reflect(w, 0f)
                    val rBR = reflect(w, h)
                    val rBL = reflect(0f, h)

                    reflectedScreenPath.rewind()
                    reflectedScreenPath.moveTo(rTL.x, rTL.y)
                    reflectedScreenPath.lineTo(rTR.x, rTR.y)
                    reflectedScreenPath.lineTo(rBR.x, rBR.y)
                    reflectedScreenPath.lineTo(rBL.x, rBL.y)
                    reflectedScreenPath.close()

                    clipRect(0f, 0f, w, h) {
                        clipPath(frontPath) {
                            drawPath(reflectedScreenPath, color = paperColor)
                            if (textureBitmap != null && textureAlpha > 0f) {
                                clipPath(reflectedScreenPath) {
                                    drawRect(
                                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                                        blendMode = BlendMode.SrcOver,
                                        alpha = textureAlpha
                                    )
                                }
                            }
                            val flapTint = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            drawPath(reflectedScreenPath, color = flapTint)

                            val innerShadowWidth = shadowWidth * 0.7f
                            val innerShadowBrush = Brush.linearGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.05f), Color.Transparent),
                                start = Offset(midX, midY),
                                end = Offset(midX - nx * innerShadowWidth, midY - ny * innerShadowWidth)
                            )
                            drawPath(reflectedScreenPath, innerShadowBrush)

                            drawPath(
                                path = reflectedScreenPath,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        drawLine(
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            start = Offset(p1X, p1Y),
                            end = Offset(p2X, p2Y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                } else {
                    drawPaperBackground()
                    drawContent()
                }
            }
            else {
                drawPaperBackground()
                drawContent()
            }

            val drawDuration = (System.nanoTime() - drawStart) / 1_000_000.0
            if (drawDuration > 12.0) { // Log slow frames (anything near the 16ms frame budget)
                Timber.tag("PageTurnFixDiag").w("Slow Draw on Page $pageIndex: ${drawDuration}ms")
            }
        }
}

@Suppress("KotlinConstantConditions")
@Composable
fun Modifier.drawCssBorders(
    blockStyle: BlockStyle,
    @Suppress("unused") density: Density
): Modifier = this.drawBehind {
    val borderTop = blockStyle.borderTop
    val borderRight = blockStyle.borderRight
    val borderBottom = blockStyle.borderBottom
    val borderLeft = blockStyle.borderLeft
    val topWidth = borderTop?.width?.toPx() ?: 0f
    val rightWidth = borderRight?.width?.toPx() ?: 0f
    val bottomWidth = borderBottom?.width?.toPx() ?: 0f
    val leftWidth = borderLeft?.width?.toPx() ?: 0f

    val tlRadius = blockStyle.borderTopLeftRadius.toPx()
    val trRadius = blockStyle.borderTopRightRadius.toPx()
    val brRadius = blockStyle.borderBottomRightRadius.toPx()
    val blRadius = blockStyle.borderBottomLeftRadius.toPx()

    if (blockStyle.backgroundColor.isSpecified && blockStyle.backgroundColor != Color.Transparent) {
        val bgPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(tlRadius, tlRadius),
                    topRight = CornerRadius(trRadius, trRadius),
                    bottomRight = CornerRadius(brRadius, brRadius),
                    bottomLeft = CornerRadius(blRadius, blRadius)
                )
            )
        }
        drawPath(bgPath, color = blockStyle.backgroundColor, style = Fill)
    }

    // 2. Helper for PathEffects
    fun getPathEffect(style: String?, width: Float): PathEffect? {
        return when (style) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(width * 3f, width * 2f), 0f)
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(width, width), 0f)
            else -> null
        }
    }

    // TOP
    if (topWidth > 0f && borderTop != null) {
        val color = borderTop.color
        val effect = getPathEffect(borderTop.style, topWidth)
        val offset = topWidth / 2f

        val startX = if (tlRadius > 0) tlRadius else 0f
        val endX = if (trRadius > 0) size.width - trRadius else size.width

        drawLine(
            color = color,
            start = Offset(startX, offset),
            end = Offset(endX, offset),
            strokeWidth = topWidth,
            pathEffect = effect
        )
    }

    // BOTTOM
    if (bottomWidth > 0f && borderBottom != null) {
        val color = borderBottom.color
        val effect = getPathEffect(borderBottom.style, bottomWidth)
        val offset = size.height - (bottomWidth / 2f)

        val startX = if (blRadius > 0) blRadius else 0f
        val endX = if (brRadius > 0) size.width - brRadius else size.width

        drawLine(
            color = color,
            start = Offset(startX, offset),
            end = Offset(endX, offset),
            strokeWidth = bottomWidth,
            pathEffect = effect
        )
    }

    // LEFT
    if (leftWidth > 0f && borderLeft != null) {
        val color = borderLeft.color
        val effect = getPathEffect(borderLeft.style, leftWidth)
        val offset = leftWidth / 2f

        val startY = if (tlRadius > 0) tlRadius else 0f
        val endY = if (blRadius > 0) size.height - blRadius else size.height

        drawLine(
            color = color,
            start = Offset(offset, startY),
            end = Offset(offset, endY),
            strokeWidth = leftWidth,
            pathEffect = effect
        )
    }

    // RIGHT
    if (rightWidth > 0f && borderRight != null) {
        val color = borderRight.color
        val effect = getPathEffect(borderRight.style, rightWidth)
        val offset = size.width - (rightWidth / 2f)

        val startY = if (trRadius > 0) trRadius else 0f
        val endY = if (brRadius > 0) size.height - brRadius else size.height

        drawLine(
            color = color,
            start = Offset(offset, startY),
            end = Offset(offset, endY),
            strokeWidth = rightWidth,
            pathEffect = effect
        )
    }

    if (tlRadius > 0f && topWidth > 0f && leftWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 180f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth/2f, topWidth/2f),
            size = Size(tlRadius * 2 - leftWidth, tlRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (trRadius > 0f && topWidth > 0f && rightWidth > 0f && borderTop != null) {
        drawArc(
            color = borderTop.color,
            startAngle = 270f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (trRadius * 2) + (rightWidth/2f), topWidth/2f),
            size = Size(trRadius * 2 - rightWidth, trRadius * 2 - topWidth),
            style = Stroke(width = topWidth)
        )
    }

    if (brRadius > 0f && bottomWidth > 0f && rightWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 0f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width - (brRadius * 2) + (rightWidth/2f), size.height - (brRadius * 2) + (bottomWidth/2f)),
            size = Size(brRadius * 2 - rightWidth, brRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }

    if (blRadius > 0f && bottomWidth > 0f && leftWidth > 0f && borderBottom != null) {
        drawArc(
            color = borderBottom.color,
            startAngle = 90f, sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(leftWidth/2f, size.height - (blRadius * 2) + (bottomWidth/2f)),
            size = Size(blRadius * 2 - leftWidth, blRadius * 2 - bottomWidth),
            style = Stroke(width = bottomWidth)
        )
    }
}
