// PaginatedReader.kt
@file:Suppress("VariableNeverRead")

package com.aryan.reader.paginatedreader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.aryan.reader.BuildConfig
import com.aryan.reader.copyPlainTextToClipboard
import androidx.compose.ui.unit.isSpecified
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.magnifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest.Builder
import com.aryan.reader.R
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.countWords
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.plainTextCharacterCount
import com.aryan.reader.epubreader.HighlightColor
import com.aryan.reader.epubreader.PaginatedTextSelectionMenu
import com.aryan.reader.epubreader.PaletteManagerDialog
import com.aryan.reader.epubreader.ReaderTextAlign
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import com.aryan.reader.shared.reader.paintOnlyColorOverlayText
import com.aryan.reader.shared.reader.withoutForegroundColorSpans
import com.aryan.reader.shared.ui.sharedAcceleratedLazyWheelScroll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.net.URI
import java.net.URLDecoder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt


@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalSerializationApi::class)
@Composable
fun NativeVerticalReaderScreen(
    modifier: Modifier = Modifier,
    book: EpubBook,
    bookId: String? = null,
    isDarkTheme: Boolean,
    effectiveBg: Color,
    effectiveText: Color,
    searchQuery: String,
    fontSizeMultiplier: Float,
    lineHeightMultiplier: Float,
    paragraphGapMultiplier: Float,
    imageSizeMultiplier: Float,
    hideImages: Boolean = false,
    horizontalMarginMultiplier: Float,
    verticalMarginMultiplier: Float,
    fontFamily: FontFamily,
    fontWeight: Int,
    letterSpacing: Float,
    textAlign: ReaderTextAlign,
    bookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    bookReplacementFileId: String? = bookId,
    ttsHighlightInfo: TtsHighlightInfo?,
    initialLocator: Locator? = null,
    initialPageIndexInBook: Int = 0,
    scrollRequestPage: Int? = null,
    scrollRequestLocator: Locator? = null,
    scrollRequestLocatorId: Long = 0L,
    scrollRequestLocatorKeepVisible: Boolean = false,
    scrollRequestProgressPercent: Float? = null,
    scrollRequestProgressId: Long = 0L,
    scrollDeltaRequest: Float? = null,
    scrollDeltaRequestId: Long = 0L,
    scrollDeltaRequestAnimated: Boolean = true,
    onScrollRequestConsumed: () -> Unit = {},
    onScrollLocatorRequestConsumed: () -> Unit = {},
    onScrollProgressRequestConsumed: () -> Unit = {},
    onScrollDeltaConsumed: () -> Unit = {},
    onPaginatorReady: (IPaginator) -> Unit,
    onVisiblePageChanged: (pageIndex: Int, chapterIndex: Int?, locator: Locator?) -> Unit = { _, _, _ -> },
    onProgressChanged: (pageIndex: Int, totalPages: Int, progressPercent: Float) -> Unit = { _, _, _ -> },
    onLocationChanged: (NativeVerticalLocation) -> Unit = {},
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int, Int?) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onFootnoteRequested: (String) -> Unit = {},
    onInternalLinkNavigated: (Int, Locator?) -> Unit = { _, _ -> },
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String, SharedReaderLocator, HighlightStyle) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<Int>,
    onUpdatePalette: (Int, Int) -> Unit,
    activeTextureId: String? = null,
    activeTextureAlpha: Float = 0.55f
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val textureBitmap = remember(activeTextureId) {
        loadReaderTextureBitmap(context, activeTextureId)
    }
    val textureModifier = if (textureBitmap != null) {
        Modifier.drawBehind {
            val brush = ShaderBrush(
                ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)
            )
            drawRect(brush = brush, blendMode = BlendMode.SrcOver, alpha = activeTextureAlpha.coerceIn(0f, 1f))
        }
    } else {
        Modifier
    }
    val bookReplacementSignature = remember(bookReplacementPreferences, bookReplacementFileId) {
        bookReplacementPreferences.signatureForFile(bookReplacementFileId)
    }
    var rootWindowBounds by remember { mutableStateOf(Rect.Zero) }
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(effectiveBg)
            .then(textureModifier)
            .onGloballyPositioned { coords ->
                rootWindowBounds = Rect(coords.positionInWindow(), coords.size.toSize())
            }
            .testTag("NativeVerticalReader")
    ) {
        val textMeasurer = rememberTextMeasurer()
        val baseTextStyle = MaterialTheme.typography.bodyLarge
        val density = LocalDensity.current
        val layoutTextStyle = remember(
            baseTextStyle,
            fontSizeMultiplier,
            lineHeightMultiplier,
            fontFamily,
            fontWeight,
            letterSpacing
        ) {
            val adjustedFontSize = baseTextStyle.fontSize * fontSizeMultiplier
            val adjustedLineHeight =
                adjustedFontSize * paginationLineHeightMultiplierForWebViewSetting(lineHeightMultiplier)

            baseTextStyle.copy(
                color = Color.Unspecified,
                fontSize = adjustedFontSize,
                lineHeight = adjustedLineHeight,
                fontFamily = fontFamily,
                fontWeight = fontWeight.takeIf { it > 0 }?.let(::FontWeight),
                lineBreak = LineBreak.Simple,
                letterSpacing = letterSpacing.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Proportional,
                    trim = LineHeightStyle.Trim.None
                )
            )
        }
        val textStyle = remember(layoutTextStyle, effectiveText) {
            layoutTextStyle.copy(color = effectiveText)
        }
        val userTextAlign = remember(textAlign) {
            when (textAlign) {
                ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                ReaderTextAlign.LEFT -> TextAlign.Left
                ReaderTextAlign.RIGHT -> TextAlign.Right
                ReaderTextAlign.DEFAULT -> null
            }
        }
        val requestedHorizontalPadding = 16.dp * horizontalMarginMultiplier
        val requestedVerticalPadding = 16.dp * verticalMarginMultiplier
        val effectiveReaderPadding =
            remember(this.constraints, density, requestedHorizontalPadding, requestedVerticalPadding) {
                val requestedHorizontalPaddingPx = with(density) { requestedHorizontalPadding.roundToPx() }
                val requestedVerticalPaddingPx = with(density) { requestedVerticalPadding.roundToPx() }
                val minReadableWidthPx = with(density) { 96.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxWidth)
                val minReadableHeightPx = with(density) { 160.dp.roundToPx() }
                    .coerceAtMost(this.constraints.maxHeight)
                val horizontalPaddingPx = requestedHorizontalPaddingPx.coerceAtMost(
                    ((this.constraints.maxWidth - minReadableWidthPx) / 2).coerceAtLeast(0)
                )
                val verticalPaddingPx = requestedVerticalPaddingPx.coerceAtMost(
                    ((this.constraints.maxHeight - minReadableHeightPx) / 2).coerceAtLeast(0)
                )
                with(density) {
                    horizontalPaddingPx.toDp() to verticalPaddingPx.toDp()
                }
        }
        val horizontalPadding = effectiveReaderPadding.first
        val verticalPadding = effectiveReaderPadding.second
        val textConstraints =
            remember(this.constraints, density, horizontalPadding, verticalPadding) {
                val horizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
                val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
                this.constraints.copy(
                    minWidth = 0,
                    maxWidth = (this.constraints.maxWidth - (2 * horizontalPaddingPx)).coerceAtLeast(1),
                    minHeight = 0,
                    maxHeight = (this.constraints.maxHeight - (2 * verticalPaddingPx)).coerceAtLeast(1)
                )
            }

        val mathMLRenderer = remember { MathMLRenderer(context.applicationContext) }
        DisposableEffect(Unit) {
            onDispose {
                mathMLRenderer.destroy()
                Timber.d("NativeVerticalReaderScreen disposed, MathMLRenderer destroyed.")
            }
        }

        val paginator = remember(
            book,
            bookId,
            textConstraints,
            layoutTextStyle,
            userTextAlign,
            paragraphGapMultiplier,
            imageSizeMultiplier,
            hideImages,
            verticalMarginMultiplier,
            bookReplacementSignature,
            bookReplacementFileId
        ) {
            val userAgentStylesheet = UserAgentStylesheet.default
            var allRules = OptimizedCssRules()
            val allFontFaces = mutableListOf<FontFaceInfo>()

            val uaResult = CssParser.parse(
                cssContent = userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = layoutTextStyle.fontSize.value,
                density = density.density,
                constraints = textConstraints,
                isDarkTheme = false,
                adaptThemeColors = false
            )
            allRules = allRules.merge(uaResult.rules)
            allFontFaces.addAll(uaResult.fontFaces)

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = layoutTextStyle.fontSize.value,
                    density = density.density,
                    constraints = textConstraints,
                    isDarkTheme = false,
                    adaptThemeColors = false
                )
                allRules = allRules.merge(bookCssResult.rules)
                allFontFaces.addAll(bookCssResult.fontFaces)
            }

            val fontFamilyMap = loadFontFamilies(
                fontFaces = allFontFaces,
                extractionPath = book.extractionBasePath
            )
            val bookCacheDao =
                BookCacheDatabase.getDatabase(context.applicationContext).bookCacheDao()
            val proto = ProtoBuf { serializersModule = semanticBlockModule }
            val uniqueBookId = bookId ?: if (book.fileName.length > 20) book.fileName else book.title
            val initialChapter = initialLocator?.chapterIndex ?: 0

            Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).d(
                "paginator_create initialChapter=$initialChapter constraints=${textConstraints.maxWidth}x${textConstraints.maxHeight}"
            )
            BookPaginator(
                coroutineScope = coroutineScope,
                chapters = book.chaptersForPagination,
                textMeasurer = textMeasurer,
                constraints = textConstraints,
                textStyle = layoutTextStyle,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = fontFamilyMap,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = effectiveBg,
                themeTextColor = effectiveText,
                bookId = uniqueBookId,
                bookCacheDao = bookCacheDao,
                proto = proto,
                initialChapterToPaginate = initialChapter,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = userTextAlign,
                paragraphGapMultiplier = paragraphGapMultiplier,
                userLineHeightMultiplier = lineHeightMultiplier,
                imageSizeMultiplier = imageSizeMultiplier,
                hideImages = hideImages,
                verticalMarginMultiplier = verticalMarginMultiplier,
                bookReplacementPreferences = bookReplacementPreferences,
                bookReplacementFileId = bookReplacementFileId
            )
        }

        LaunchedEffect(paginator) {
            onPaginatorReady(paginator)
        }

        DisposableEffect(paginator) {
            Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).d("native_screen_attached paginator=${paginator.hashCode()}")
            onDispose {
                Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).d("native_screen_disposed paginator=${paginator.hashCode()}")
                paginator.dispose()
            }
        }

        var isLoading by remember { mutableStateOf(true) }
        var totalPageCount by remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        LaunchedEffect(paginator) {
            launch {
                snapshotFlow { paginator.isLoading }.collect { isLoading = it }
            }
            launch {
                snapshotFlow { paginator.totalPageCount }.collect { totalPageCount = it }
            }
            launch {
                snapshotFlow { paginator.generation }.collect { generation = it }
            }
        }

        val listState = rememberLazyListState()
        val blockLayoutMap = remember(paginator) { ReactiveBlockMap() }
        val chapterLayoutMap = remember(paginator) { mutableStateMapOf<Int, LayoutCoordinates>() }
        val flowItemLayoutMap = remember(paginator) { mutableStateMapOf<String, LayoutCoordinates>() }
        var flowChapters by remember(paginator) { mutableStateOf<List<NativeVerticalFlowChapter>?>(null) }
        val flowItems = remember(flowChapters) { buildNativeVerticalFlowItems(flowChapters.orEmpty()) }
        val latestFlowItems by rememberUpdatedState(flowItems)
        var isFlowLoading by remember(paginator) { mutableStateOf(true) }
        val initialNativeLocator = remember(paginator) { initialLocator }
        val initialNativePageIndex = remember(paginator) { initialPageIndexInBook }
        var didInitialScroll by remember(paginator) { mutableStateOf(false) }
        val placeholderFlowChapters = remember(book) {
            book.chaptersForPagination.mapIndexed { chapterIndex, chapter ->
                NativeVerticalFlowChapter(
                    chapterIndex = chapterIndex,
                    title = chapter.title,
                    blocks = emptyList(),
                    isLoaded = false,
                    estimatedLocationWeight = chapter.plainTextCharacterCount().coerceAtLeast(24)
                )
            }
        }
        val flowChapterLoadsInFlight = remember(paginator) { mutableStateMapOf<Int, Boolean>() }
        val flowChapterLoadMutex = remember(paginator) { Mutex() }

        fun ensurePlaceholderFlowChapters() {
            val current = flowChapters
            if (current == null || current.size != placeholderFlowChapters.size) {
                flowChapters = placeholderFlowChapters
            }
        }

        suspend fun loadFlowChapter(chapterIndex: Int, reason: String = "demand"): Boolean {
            if (chapterIndex !in placeholderFlowChapters.indices) return false
            flowChapters?.getOrNull(chapterIndex)?.takeIf { it.isLoaded }?.let { return true }
            while (flowChapterLoadsInFlight[chapterIndex] == true) {
                delay(16L)
                flowChapters?.getOrNull(chapterIndex)?.takeIf { it.isLoaded }?.let { return true }
            }

            return flowChapterLoadMutex.withLock {
                flowChapters?.getOrNull(chapterIndex)?.takeIf { it.isLoaded }?.let { return@withLock true }
                flowChapterLoadsInFlight[chapterIndex] = true
                val startMs = System.currentTimeMillis()
                Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).d(
                    "chapter_load_start reason=$reason chapter=$chapterIndex loaded=${flowChapters?.getOrNull(chapterIndex)?.isLoaded == true}"
                )
                try {
                    val chapter = book.chaptersForPagination.getOrNull(chapterIndex) ?: return@withLock false
                    val blocks = try {
                        paginator.getFlowBlocksForChapter(chapterIndex)
                    } catch (e: CancellationException) {
                        Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).d(
                            "chapter_load_cancelled reason=$reason chapter=$chapterIndex durationMs=${System.currentTimeMillis() - startMs}"
                        )
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).e(e, "chapter_load_error reason=$reason chapter=$chapterIndex")
                        null
                    }
                    val updated = nativeVerticalFlowChaptersAfterLoadResult(
                        currentChapters = flowChapters,
                        placeholderChapters = placeholderFlowChapters,
                        chapterIndex = chapterIndex,
                        title = chapter.title,
                        blocks = blocks,
                        estimatedLocationWeight = chapter.plainTextCharacterCount().coerceAtLeast(24)
                    ) ?: run {
                        Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).w(
                            "chapter_load_retryable reason=$reason chapter=$chapterIndex durationMs=${System.currentTimeMillis() - startMs}"
                        )
                        return@withLock false
                    }
                    flowChapters = updated
                    Timber.tag(NATIVE_VERTICAL_UI_LOAD_LOG_TAG).d(
                        "chapter_load_done reason=$reason chapter=$chapterIndex blocks=${blocks?.size ?: 0} durationMs=${System.currentTimeMillis() - startMs}"
                    )
                    true
                } finally {
                    flowChapterLoadsInFlight.remove(chapterIndex)
                }
            }
        }

        @Suppress("UNUSED_PARAMETER")
        suspend fun scrollToFlowLocator(
            locator: Locator?,
            animate: Boolean,
            keepVisible: Boolean = false
        ): Boolean {
            if (locator == null) return false
            ensurePlaceholderFlowChapters()
            if (flowChapters?.getOrNull(locator.chapterIndex)?.isLoaded != true) {
                loadFlowChapter(locator.chapterIndex, reason = "locator")
                withFrameNanos { }
            }
            val chapters = flowChapters ?: return false
            val currentFlowItems = buildNativeVerticalFlowItems(chapters)
            val exactDelta = resolveNativeVerticalScrollDeltaForLocator(
                rootWindowBounds = rootWindowBounds,
                chapterLayoutMap = chapterLayoutMap,
                flowItems = currentFlowItems,
                flowItemLayoutMap = flowItemLayoutMap,
                blockLayoutMap = blockLayoutMap,
                chapters = chapters,
                locator = locator,
                allowChapterFallback = false
            )
            if (exactDelta != null) {
                val scrollDelta = if (keepVisible) {
                    nativeVerticalCenteredScrollDelta(
                        targetOffsetInViewport = exactDelta,
                        viewportHeight = rootWindowBounds.height
                    )
                } else {
                    exactDelta
                }
                if (abs(scrollDelta) > 1f) {
                    if (animate) {
                        listState.animateScrollBy(scrollDelta)
                    } else {
                        listState.scrollBy(scrollDelta)
                    }
                }
                if (keepVisible || abs(exactDelta) > 1f) return true
            }

            val targetIndex = findNativeVerticalFlowItemIndexForLocator(
                items = currentFlowItems,
                chapters = chapters,
                locator = locator
            ) ?: return false
            if (animate) {
                listState.animateScrollToItem(targetIndex)
            } else {
                listState.scrollToItem(targetIndex)
            }
            repeat(4) {
                withFrameNanos { }
                val refinedDelta = resolveNativeVerticalScrollDeltaForLocator(
                    rootWindowBounds = rootWindowBounds,
                    chapterLayoutMap = chapterLayoutMap,
                    flowItems = currentFlowItems,
                    flowItemLayoutMap = flowItemLayoutMap,
                    blockLayoutMap = blockLayoutMap,
                    chapters = chapters,
                    locator = locator,
                    allowChapterFallback = false
                )
                if (refinedDelta != null) {
                    val scrollDelta = if (keepVisible) {
                        nativeVerticalCenteredScrollDelta(
                            targetOffsetInViewport = refinedDelta,
                            viewportHeight = rootWindowBounds.height
                        )
                    } else {
                        refinedDelta
                    }
                    if (abs(scrollDelta) > 1f) {
                        if (animate) {
                            listState.animateScrollBy(scrollDelta)
                        } else {
                            listState.scrollBy(scrollDelta)
                        }
                    }
                    return true
                }
            }
            return true
        }

        suspend fun scrollToCompatPage(pageIndex: Int, animate: Boolean): Boolean {
            val targetPage = pageIndex.coerceIn(0, (totalPageCount - 1).coerceAtLeast(0))
            val locator = paginator.getLocatorForPage(targetPage)
                ?: paginator.findChapterIndexForPage(targetPage)?.let { Locator(it, 0, 0) }
                ?: return false
            val didScroll = scrollToFlowLocator(locator, animate)
            if (didScroll) paginator.onUserScrolledTo(targetPage)
            return didScroll
        }

        suspend fun scrollToProgressPercent(progressPercent: Float): Boolean {
            if (flowItems.isEmpty()) return false
            val targetIndex = findNativeVerticalFlowItemIndexForProgress(
                items = flowItems,
                progressPercent = progressPercent
            ) ?: return false
            listState.scrollToItem(targetIndex)
            paginator.onUserScrolledTo(
                nativeVerticalCompatPageForProgress(progressPercent, totalPageCount)
            )
            return true
        }

        LaunchedEffect(paginator) {
            snapshotFlow { paginator.isLoading }.filter { !it }.first()
            isFlowLoading = true
            if (placeholderFlowChapters.isEmpty()) {
                flowChapters = emptyList()
                isFlowLoading = false
                return@LaunchedEffect
            }

            flowChapters = placeholderFlowChapters
            val initialChapter = (
                initialNativeLocator?.chapterIndex
                    ?: paginator.findChapterIndexForPage(initialNativePageIndex)
                    ?: 0
                ).coerceIn(0, placeholderFlowChapters.lastIndex)
            loadFlowChapter(initialChapter, reason = "initial")
            isFlowLoading = false
        }

        LaunchedEffect(paginator, placeholderFlowChapters.size) {
            snapshotFlow {
                didInitialScroll to latestFlowItems.getOrNull(listState.firstVisibleItemIndex)?.chapterIndex
            }
                .distinctUntilChanged()
                .collectLatest { (initialScrollComplete, visibleChapter) ->
                    if (!initialScrollComplete) return@collectLatest
                    val anchorChapter = visibleChapter ?: return@collectLatest
                    val warmupOrder = nativeVerticalChapterWarmupOrder(
                        chapterCount = placeholderFlowChapters.size,
                        anchorChapter = anchorChapter
                    )
                    warmupOrder.forEachIndexed { priority, chapterIndex ->
                        if (!isActive) return@collectLatest
                        if (priority > 0) {
                            while (isActive && listState.isScrollInProgress) {
                                delay(80L)
                            }
                        }
                        loadFlowChapter(chapterIndex, reason = if (priority == 0) "visible" else "visible_prefetch_$priority")
                        if (priority > 0) delay(48L)
                    }
                }
        }

        LaunchedEffect(flowChapters, totalPageCount, rootWindowBounds) {
            if (didInitialScroll || flowChapters == null || rootWindowBounds == Rect.Zero) return@LaunchedEffect
            val targetLocator = initialNativeLocator ?: paginator.getLocatorForPage(initialNativePageIndex)
            if (targetLocator == null) {
                didInitialScroll = true
                return@LaunchedEffect
            }
            val didLocatorScroll = scrollToFlowLocator(targetLocator, animate = false)
            val didScroll = didLocatorScroll ||
                if (shouldFallbackNativeVerticalInitialScrollToCompatPage(
                        hasInitialLocator = initialNativeLocator != null,
                        didLocatorScroll = didLocatorScroll
                    )
                ) {
                    scrollToCompatPage(initialNativePageIndex, animate = false)
                } else {
                    false
                }
            if (didScroll || initialNativeLocator != null) {
                didInitialScroll = true
            }
        }

        LaunchedEffect(scrollRequestPage, totalPageCount, flowChapters, rootWindowBounds) {
            val requestedPage = scrollRequestPage ?: return@LaunchedEffect
            if (totalPageCount <= 0 || flowChapters == null || rootWindowBounds == Rect.Zero) return@LaunchedEffect
            if (scrollToCompatPage(requestedPage, animate = true)) {
                onScrollRequestConsumed()
            }
        }

        LaunchedEffect(scrollRequestLocatorId, scrollRequestLocator, scrollRequestLocatorKeepVisible, flowChapters, rootWindowBounds) {
            val requestedLocator = scrollRequestLocator ?: return@LaunchedEffect
            if (flowChapters == null || rootWindowBounds == Rect.Zero) return@LaunchedEffect
            if (scrollToFlowLocator(
                    locator = requestedLocator,
                    animate = scrollRequestLocatorKeepVisible,
                    keepVisible = scrollRequestLocatorKeepVisible
                )
            ) {
                paginator.onUserScrolledTo(
                    nativeVerticalCompatPageForProgress(
                        estimateNativeVerticalProgressPercent(book, requestedLocator) ?: 0f,
                        totalPageCount
                    )
                )
                onScrollLocatorRequestConsumed()
            }
        }

        LaunchedEffect(scrollRequestProgressId, scrollRequestProgressPercent, flowChapters) {
            val requestedProgress = scrollRequestProgressPercent ?: return@LaunchedEffect
            if (flowChapters == null) return@LaunchedEffect
            if (scrollToProgressPercent(requestedProgress)) {
                onScrollProgressRequestConsumed()
            }
        }

        LaunchedEffect(scrollDeltaRequestId, scrollDeltaRequest, scrollDeltaRequestAnimated) {
            val delta = scrollDeltaRequest ?: return@LaunchedEffect
            if (delta != 0f) {
                if (scrollDeltaRequestAnimated) {
                    listState.animateScrollBy(delta)
                } else {
                    listState.scrollBy(delta)
                }
            }
            onScrollDeltaConsumed()
        }

        var lastReportedVisiblePage by remember { mutableIntStateOf(-1) }
        var lastReportedTotalPageCount by remember { mutableIntStateOf(0) }
        var lastReportedProgressPercent by remember { mutableFloatStateOf(-1f) }
        var lastReportedLocator by remember { mutableStateOf<Locator?>(null) }
        var lastReportedChapterPageInfo by remember { mutableStateOf<NativeVerticalChapterPageInfo?>(null) }
        var lastReportedVisibleTextRanges by remember { mutableStateOf<List<NativeVerticalVisibleTextRange>>(emptyList()) }

        LaunchedEffect(paginator, totalPageCount, rootWindowBounds, blockLayoutMap, flowChapters, flowItems) {
            snapshotFlow {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val firstVisibleItemSize = visibleItems
                    .firstOrNull { it.index == listState.firstVisibleItemIndex }
                    ?.size
                    ?: 0
                val lastVisibleItem = visibleItems.lastOrNull()
                val isAtEnd = layoutInfo.totalItemsCount > 0 &&
                    lastVisibleItem?.index == layoutInfo.totalItemsCount - 1 &&
                    lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
                NativeVerticalViewportSample(
                    firstVisiblePageIndex = listState.firstVisibleItemIndex,
                    firstVisiblePageScrollOffset = listState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = firstVisibleItemSize,
                    isAtStart = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
                    isAtEnd = isAtEnd,
                    totalPageCount = totalPageCount.takeIf { it > 0 } ?: (flowChapters?.size ?: 0),
                    layoutTick = blockLayoutMap.tick,
                    initialScrollComplete = didInitialScroll
                )
            }
                .collectLatest { sample ->
                    if (!sample.initialScrollComplete) return@collectLatest
                    val total = sample.totalPageCount
                    if (total <= 0) return@collectLatest
                    blockLayoutMap.pruneDetached()
                    val locator = resolveNativeVerticalFlowVisibleLocator(
                        rootWindowBounds = rootWindowBounds,
                        blockLayoutMap = blockLayoutMap
                    ) ?: flowItems.getOrNull(sample.firstVisiblePageIndex)
                        ?.let { locatorForNativeVerticalFlowItem(it) }
                    val visibleTextRanges = resolveNativeVerticalVisibleTextRanges(
                        rootWindowBounds = rootWindowBounds,
                        blockLayoutMap = blockLayoutMap
                    )
                    val progressPercent = when {
                        sample.isAtEnd -> 100f
                        sample.isAtStart -> 0f
                        else -> estimateNativeVerticalScrollProgressPercent(
                            items = flowItems,
                            firstVisibleItemIndex = sample.firstVisiblePageIndex,
                            firstVisibleItemScrollOffset = sample.firstVisiblePageScrollOffset,
                            firstVisibleItemSize = sample.firstVisibleItemSize
                        ) ?: estimateNativeVerticalProgressPercent(
                            book = book,
                            locator = locator
                        ) ?: 0f
                    }
                    val compatPage = nativeVerticalCompatPageForProgress(progressPercent, total)
                    paginator.onUserScrolledTo(compatPage)
                    val visibleChapterIndex = locator?.chapterIndex
                        ?: flowItems.getOrNull(sample.firstVisiblePageIndex)?.chapterIndex
                    val chapterPageInfo = visibleChapterIndex?.let { chapterIndex ->
                        nativeVerticalChapterPageInfoForScroll(
                            itemChapterIndices = flowItems.map { it.chapterIndex },
                            itemWeights = flowItems.map { it.locationWeight },
                            firstVisibleItemIndex = sample.firstVisiblePageIndex,
                            firstVisibleItemScrollOffset = sample.firstVisiblePageScrollOffset,
                            firstVisibleItemSize = sample.firstVisibleItemSize,
                            chapterPageCount = paginator.chapterPageCounts[chapterIndex]
                        )
                    }

                    if (
                        compatPage != lastReportedVisiblePage ||
                        total != lastReportedTotalPageCount ||
                        abs(progressPercent - lastReportedProgressPercent) >= 0.05f ||
                        locator != lastReportedLocator ||
                        chapterPageInfo != lastReportedChapterPageInfo ||
                        visibleTextRanges != lastReportedVisibleTextRanges
                    ) {
                        lastReportedVisiblePage = compatPage
                        lastReportedTotalPageCount = total
                        lastReportedProgressPercent = progressPercent
                        lastReportedLocator = locator
                        lastReportedChapterPageInfo = chapterPageInfo
                        lastReportedVisibleTextRanges = visibleTextRanges
                        onLocationChanged(
                            NativeVerticalLocation(
                                locator = locator,
                                chapterIndex = locator?.chapterIndex,
                                progressPercent = progressPercent,
                                compatPageIndex = compatPage,
                                compatTotalPages = total,
                                firstVisibleItemIndex = sample.firstVisiblePageIndex,
                                firstVisibleItemScrollOffset = sample.firstVisiblePageScrollOffset,
                                firstVisibleItemSize = sample.firstVisibleItemSize,
                                isAtStart = sample.isAtStart,
                                isAtEnd = sample.isAtEnd,
                                visibleTextRanges = visibleTextRanges,
                                chapterPageInfo = chapterPageInfo
                            )
                        )
                        onProgressChanged(compatPage, total, progressPercent)
                        onVisiblePageChanged(compatPage, locator?.chapterIndex, locator)
                    }
                }
        }

        val searchHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        val ttsHighlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        var activeSelection by remember { mutableStateOf<PaginatedSelection?>(null) }
        var isDraggingHandle by remember { mutableStateOf(false) }
        var selectionEdgeScrollDelta by remember { mutableFloatStateOf(0f) }
        var selectionEdgeDragWindowPos by remember { mutableStateOf(Offset.Unspecified) }
        var selectionEdgeDragHandle by remember { mutableStateOf<SelectionHandle?>(null) }
        val activeDragHandleForDisplay = selectionEdgeDragHandle
        var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }
        val magnifierModifier = if (magnifierCenter.isSpecified) {
            Modifier.magnifier(
                sourceCenter = { magnifierCenter },
                zoom = 1.5f,
                size = DpSize(140.dp, 48.dp),
                cornerRadius = 24.dp,
                elevation = 4.dp
            )
        } else {
            Modifier
        }
        var showPaletteManager by remember { mutableStateOf(false) }
        var showExternalLinkDialog by remember { mutableStateOf<String?>(null) }
        val imageLoader = context.imageLoader

        showExternalLinkDialog?.let { urlToShow ->
            AlertDialog(
                onDismissRequest = { showExternalLinkDialog = null },
                title = { Text(stringResource(R.string.dialog_external_link_title)) },
                text = { Text(urlToShow) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, urlToShow.toUri())
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Timber.e(e, "No activity found to handle intent for URL: $urlToShow")
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.error_no_browser),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            showExternalLinkDialog = null
                        }
                    ) { Text(stringResource(R.string.action_open)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val copied = copyPlainTextToClipboard(
                            context = context,
                            label = context.getString(R.string.clip_label_copied_text),
                            text = urlToShow
                        )
                        if (!copied) {
                            Toast.makeText(context, context.getString(R.string.error_copy_to_clipboard), Toast.LENGTH_SHORT).show()
                        }
                        showExternalLinkDialog = null
                    }) { Text(stringResource(R.string.action_copy)) }
                }
            )
        }

        val renderedFlowChapters = flowChapters

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootCoords = it }
                .then(magnifierModifier)
        ) {
            if (isFlowLoading || renderedFlowChapters == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (renderedFlowChapters.isNotEmpty()) {
                generation
                val chapterBoundaryGap = 44.dp * verticalMarginMultiplier.coerceIn(0.75f, 2.5f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedAcceleratedLazyWheelScroll(listState),
                    contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding)
                ) {
                    itemsIndexed(
                        items = flowItems,
                        key = { _, item -> item.key }
                    ) { _, item ->
                        val chapterIndex = item.chapterIndex
                        val block = item.block
                        val onGeneralTapCallback: (Offset) -> Unit = { offset ->
                            activeSelection = null
                            onTap(offset)
                        }
                        val onLinkClickCallback: (String) -> Unit = { href ->
                            if (href.isReaderExternalHref()) {
                                showExternalLinkDialog = href.readerExternalHrefForDisplay()
                            } else {
                                val chapterPath = book.chaptersForPagination.getOrNull(chapterIndex)?.absPath
                                coroutineScope.launch {
                                    val footnoteHtml = withContext(Dispatchers.IO) {
                                        resolveReaderFootnoteHtml(book, chapterPath.orEmpty(), href)
                                    }
                                    if (!footnoteHtml.isNullOrBlank()) {
                                        onFootnoteRequested(footnoteHtml)
                                        return@launch
                                    }
                                    val targetLocator = paginator.findStableLocatorForHref(chapterPath.orEmpty(), href)
                                    val targetPage = targetLocator?.let { paginator.findStablePageForLocator(it) }
                                        ?: paginator.findStablePageForHref(chapterPath.orEmpty(), href)
                                    if (targetPage != null) {
                                        if (targetLocator != null) {
                                            scrollToFlowLocator(targetLocator, animate = false)
                                            paginator.onUserScrolledTo(targetPage)
                                        } else {
                                            scrollToCompatPage(targetPage, animate = true)
                                        }
                                        onInternalLinkNavigated(targetPage, targetLocator)
                                    } else {
                                        Timber.tag(TAG_PAGINATED_LINK_DIAG)
                                            .w("Native vertical link failed href=$href currentChapterPath=$chapterPath")
                                    }
                                }
                            }
                        }

                        if (block == null) {
                            if (item.kind == NativeVerticalFlowItemKind.UNLOADED_CHAPTER) {
                                LaunchedEffect(chapterIndex, item.kind) {
                                    loadFlowChapter(chapterIndex, reason = "placeholder_visible")
                                }
                            }
                            val spacerHeight = when (item.kind) {
                                NativeVerticalFlowItemKind.CHAPTER_GAP -> chapterBoundaryGap
                                NativeVerticalFlowItemKind.UNLOADED_CHAPTER -> 72.dp
                                else -> 24.dp
                            }
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(spacerHeight)
                                    .onGloballyPositioned { coords ->
                                        flowItemLayoutMap[item.key] = coords
                                        chapterLayoutMap[chapterIndex] = coords
                                    }
                            )
                        } else {
                            val displayBlock = remember(block, isDarkTheme, effectiveBg, effectiveText) {
                                Page(listOf(block)).applyReaderThemeForDisplay(
                                    isDarkTheme = isDarkTheme,
                                    themeBackgroundColor = effectiveBg,
                                    themeTextColor = effectiveText
                                ).content.first()
                            }
                            val pageUserHighlights = highlightsForPaginatedPage(
                                pageChapterIndex = chapterIndex,
                                userHighlights = userHighlights
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding)
                                    .background(effectiveBg)
                                    .onGloballyPositioned { coords ->
                                        flowItemLayoutMap[item.key] = coords
                                        if (item.blockOrdinal <= 0) {
                                            chapterLayoutMap[chapterIndex] = coords
                                        }
                                    }
                                    .pointerInput(chapterIndex, item.blockOrdinal) {
                                        detectTapGestures(onTap = onGeneralTapCallback)
                                    }
                            ) {
                                NativeVerticalContentBlock(
                                    block = displayBlock,
                                    pageIndex = chapterIndex,
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
                                    onSelectionChange = { activeSelection = it },
                                    onHighlightClick = { highlight, _ ->
                                        onNoteRequested(highlight.cfi)
                                        activeSelection = null
                                    },
                                    isDarkTheme = isDarkTheme,
                                    themeBackgroundColor = effectiveBg,
                                    themeTextColor = effectiveText,
                                    blockLayoutMap = blockLayoutMap,
                                    density = density,
                                    imageLoader = imageLoader,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (activeSelection != null) {
                val sel = activeSelection!!
                @Suppress("UNUSED_VARIABLE") val selectionLayoutTick = blockLayoutMap.tick
                val selectedBlocks = visibleSelectedBlocks(blockLayoutMap, sel)

                LaunchedEffect(sel) {
                    listOf(true, false).forEach { isStart ->
                        val page = if (isStart) sel.startPageIndex else sel.endPageIndex
                        val cfi = if (isStart) sel.startBaseCfi else sel.endBaseCfi
                        val blockAbs = if (isStart) sel.startBlockCharOffset else sel.endBlockCharOffset
                        val layout = findSelectionLayout(blockLayoutMap, cfi, page, blockAbs)
                        Timber.tag(TAG_READER_INTERACTION_DIAG).d(
                            "selection_handle surface=native_vertical edge=${if (isStart) "start" else "end"} " +
                                "page=$page currentVisiblePages=${selectedBlocks.size} cfiHash=${cfi.hashCode()} " +
                                "blockAbs=$blockAbs layoutFound=${layout != null} attached=${layout?.second?.isAttached} " +
                                "rootAttached=${rootCoords?.isAttached} locale=${context.resources.configuration.locales[0]} " +
                                "layoutDirection=${context.resources.configuration.layoutDirection}"
                        )
                    }
                }

                if (!isDraggingHandle && selectedBlocks.isNotEmpty()) {
                    val handleSizePx = with(density) { 36.dp.toPx() }
                    val menuAnchorRect = selectionWindowBounds(sel, selectedBlocks, handleSizePx)
                    Popup(
                        popupPositionProvider = remember(menuAnchorRect, density) {
                            SmartPopupPositionProvider(menuAnchorRect, density)
                        },
                        onDismissRequest = { activeSelection = null },
                        properties = PopupProperties(dismissOnClickOutside = false)
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
                                    chapterIndex = sel.startPageIndex,
                                    cfi = finalCfi
                                )
                                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                    "create_request source=native_vertical_highlight_menu colorArgb=$color " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                        "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                        "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                        "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                        "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                )
                                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                    "create_request surface=native_vertical action=highlight colorArgb=$color " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
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
                                    chapterIndex = sel.startPageIndex,
                                    cfi = finalCfi
                                )
                                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                    "create_request source=native_vertical_note_menu color=${HighlightColor.YELLOW.id} " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
                                        "localOffsets=${sel.startOffset}..${sel.endOffset} " +
                                        "blockAbsStarts=${sel.startBlockCharOffset}..${sel.endBlockCharOffset} " +
                                        "absoluteOffsets=$startAbsoluteOffset..$endAbsoluteOffset " +
                                        "textLen=${sel.text.length} text='${highlightDiagSnippet(sel.text)}'"
                                )
                                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                                    "create_request surface=native_vertical action=note color=${HighlightColor.YELLOW.id} " +
                                        "savedCfi=$finalCfi absoluteCandidateCfi=$absoluteCandidateCfi " +
                                        "startPage=${sel.startPageIndex} endPage=${sel.endPageIndex} " +
                                        "startBlockIndex=${sel.startBlockIndex} endBlockIndex=${sel.endBlockIndex} " +
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
                                onStartTtsFromSelection(sel.startBaseCfi, startAbs, sel.startPageIndex)
                                activeSelection = null
                            },
                            onDelete = null,
                            isProUser = isProUser,
                            isOss = isOss,
                            activeHighlightPalette = activeHighlightPalette,
                            onOpenPaletteManager = { showPaletteManager = true }
                        )
                    }
                }

                val latestActiveSelection by rememberUpdatedState(activeSelection)
                val updateSelection: (Offset, SelectionHandle, Boolean) -> SelectionHandle =
                    updateSelection@ { windowPos, currentDragHandle, withHaptic ->
                        val currentSelection = latestActiveSelection ?: return@updateSelection currentDragHandle
                        val attachedBlocks = attachedSelectionBlocks(blockLayoutMap)
                        val updated = updatedSelectionForHandleDrag(
                            selection = currentSelection,
                            windowPos = windowPos,
                            currentDragHandle = currentDragHandle,
                            attachedBlocks = attachedBlocks,
                            blockLayoutMap = blockLayoutMap
                        )
                        if (updated != null) {
                            if (withHaptic) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            activeSelection = updated.first
                            updated.second
                        } else {
                            currentDragHandle
                        }
                    }

                val latestUpdateSelection by rememberUpdatedState(updateSelection)

                LaunchedEffect(isDraggingHandle) {
                    while (isDraggingHandle && isActive) {
                        val delta = selectionEdgeScrollDelta
                        if (abs(delta) > 0.5f) {
                            listState.scrollBy(delta)
                            withFrameNanos { }
                            val handle = selectionEdgeDragHandle
                            val targetWindowPos = selectionEdgeDragWindowPos
                            if (handle != null && targetWindowPos.isSpecified) {
                                selectionEdgeDragHandle = latestUpdateSelection(targetWindowPos, handle, false)
                            }
                        } else {
                            withFrameNanos { }
                        }
                    }
                    selectionEdgeScrollDelta = 0f
                    selectionEdgeDragWindowPos = Offset.Unspecified
                    selectionEdgeDragHandle = null
                }

                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = ReaderSelectionHandleOverlayAlignment
                ) {
                    listOf(SelectionHandle.START, SelectionHandle.END).forEach { handleType ->
                    val isStart = handleType == SelectionHandle.START
                    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

                    Box(
                        modifier = Modifier
                            .zIndex(8f)
                            .graphicsLayer {
                                @Suppress("UNUSED_VARIABLE") val tick = blockLayoutMap.tick
                                val pos = selectionHandleRootPosition(
                                    selection = sel,
                                    isStart = isStart,
                                    blockLayoutMap = blockLayoutMap,
                                    rootCoords = rootCoords
                                )
                                val shouldShowHandle = !isDraggingHandle ||
                                    activeDragHandleForDisplay == null ||
                                    activeDragHandleForDisplay == handleType

                                if (pos.isSpecified && shouldShowHandle) {
                                    translationX = pos.x - 18.dp.toPx()
                                    translationY = pos.y
                                    alpha = 1f
                                } else {
                                    alpha = 0f
                                }
                            }
                            .size(36.dp)
                            .onGloballyPositioned { handleCoords = it }
                            .pointerInput(handleType, listState) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    if (isDraggingHandle && selectionEdgeDragHandle != null) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            change.consume()
                                            if (!change.pressed) break
                                        }
                                        return@awaitEachGesture
                                    }
                                    isDraggingHandle = true
                                    var currentDragHandle = handleType
                                    selectionEdgeDragHandle = currentDragHandle
                                    selectionEdgeDragWindowPos = Offset.Unspecified
                                    var downPointerRoot = Offset.Unspecified
                                    var downHandleAnchorRoot = Offset.Unspecified
                                    if (
                                        handleCoords != null &&
                                        rootCoords != null &&
                                        handleCoords!!.isAttached &&
                                        rootCoords!!.isAttached
                                    ) {
                                        try {
                                            val pointerWindow = handleCoords!!.localToWindow(down.position)
                                            downPointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                            downHandleAnchorRoot = latestActiveSelection?.let { currentSelection ->
                                                selectionHandleRootPosition(
                                                    selection = currentSelection,
                                                    isStart = isStart,
                                                    blockLayoutMap = blockLayoutMap,
                                                    rootCoords = rootCoords
                                                )
                                            } ?: Offset.Unspecified
                                        } catch (_: Exception) {
                                            downPointerRoot = Offset.Unspecified
                                            downHandleAnchorRoot = Offset.Unspecified
                                        }
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()

                                        if (
                                            handleCoords != null &&
                                            rootCoords != null &&
                                            handleCoords!!.isAttached &&
                                            rootCoords!!.isAttached
                                        ) {
                                            try {
                                                selectionEdgeDragHandle?.let { currentDragHandle = it }
                                                val pointerWindow = handleCoords!!.localToWindow(change.position)
                                                val pointerRoot = rootCoords!!.windowToLocal(pointerWindow)
                                                val edgeSize = 64.dp.toPx()
                                                val maxScrollStep = 28.dp.toPx()
                                                val rootHeight = rootCoords!!.size.height.toFloat()
                                                val edgeScrollDelta = when {
                                                    pointerRoot.y < edgeSize ->
                                                        -(((edgeSize - pointerRoot.y) / edgeSize) * maxScrollStep)
                                                            .coerceIn(2.dp.toPx(), maxScrollStep)
                                                    pointerRoot.y > rootHeight - edgeSize ->
                                                        (((pointerRoot.y - (rootHeight - edgeSize)) / edgeSize) * maxScrollStep)
                                                            .coerceIn(2.dp.toPx(), maxScrollStep)
                                                    else -> 0f
                                                }
                                                selectionEdgeScrollDelta = edgeScrollDelta

                                                val targetRootPos = if (
                                                    downPointerRoot.isSpecified &&
                                                    downHandleAnchorRoot.isSpecified
                                                ) {
                                                    downHandleAnchorRoot + (pointerRoot - downPointerRoot)
                                                } else {
                                                    pointerRoot
                                                }
                                                magnifierCenter = targetRootPos

                                                val textHitRootPos = targetRootPos.copy(
                                                    y = targetRootPos.y - 2.dp.toPx()
                                                )
                                                val targetWindowPos = rootCoords!!.localToWindow(textHitRootPos)
                                                currentDragHandle = latestUpdateSelection(targetWindowPos, currentDragHandle, true)
                                                selectionEdgeDragWindowPos = targetWindowPos
                                                selectionEdgeDragHandle = currentDragHandle
                                            } catch (_: Exception) {
                                                // Ignore detachment during fast scroll/drag handoff.
                                            }
                                        }
                                    }
                                    isDraggingHandle = false
                                    selectionEdgeScrollDelta = 0f
                                    selectionEdgeDragWindowPos = Offset.Unspecified
                                    selectionEdgeDragHandle = null
                                    magnifierCenter = Offset.Unspecified
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.teardrop),
                            contentDescription = if (isStart) "Start handle" else "End handle",
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
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
                    }
                )
            }
        }
    }
}

@Composable
internal fun NativeVerticalPage(
    page: Page,
    pageIndex: Int,
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
    horizontalPadding: Dp,
    effectiveBg: Color,
    onTap: (Offset?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(effectiveBg)
            .padding(horizontal = horizontalPadding)
            .pointerInput(pageIndex) {
                detectTapGestures(onTap = { offset -> onTap(offset) })
            }
    ) {
        page.content.forEach { block ->
            NativeVerticalContentBlock(
                block = block,
                pageIndex = pageIndex,
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeVerticalContentBlock(
    block: ContentBlock,
    pageIndex: Int,
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
    modifier: Modifier = Modifier
) {
    val styledModifier = modifier
        .padding(
            start = block.style.margin.left.coerceAtLeast(0.dp),
            top = block.style.margin.top.coerceAtLeast(0.dp),
            end = block.style.margin.right.coerceAtLeast(0.dp),
            bottom = block.style.margin.bottom.coerceAtLeast(0.dp)
        )
        .drawCssBorders(block.style, density)
        .padding(
            start = block.style.padding.left.coerceAtLeast(0.dp),
            top = block.style.padding.top.coerceAtLeast(0.dp),
            end = block.style.padding.right.coerceAtLeast(0.dp),
            bottom = block.style.padding.bottom.coerceAtLeast(0.dp)
        )

    when (block) {
        is WrappingContentBlock -> {
            WrappingContentLayout(
                block = block,
                textStyle = textStyle,
                imageSizeMultiplier = imageSizeMultiplier,
                hideImages = hideImages,
                modifier = styledModifier,
                searchQuery = searchQuery,
                ttsHighlightInfo = ttsHighlightInfo,
                searchHighlightColor = searchHighlightColor,
                ttsHighlightColor = ttsHighlightColor,
                isDarkTheme = isDarkTheme,
                themeBackgroundColor = themeBackgroundColor,
                themeTextColor = themeTextColor,
                onLinkClick = onLinkClickCallback,
                onGeneralTap = onGeneralTapCallback
            )
        }
        is MathBlock -> {
            RenderNativeMathBlock(
                block = block,
                textStyle = textStyle,
                imageLoader = imageLoader,
                modifier = styledModifier
            )
        }
        is ChantScoreBlock -> NativeChantScore(block, textStyle, styledModifier)
        is FlexContainerBlock -> {
            val renderChild: @Composable (ContentBlock) -> Unit = { child ->
                NativeVerticalContentBlock(
                    block = child,
                    pageIndex = pageIndex,
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
                    modifier = if (child.style.display == "reader-chant-unit") {
                        Modifier.widthIn(min = 1.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
            }
            if (block.style.display == "reader-chant-flow") {
                FlowRow(
                    modifier = styledModifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    block.children.forEach { child ->
                        Box(modifier = Modifier.wrapContentHeight()) { renderChild(child) }
                    }
                }
            } else if (block.style.flexDirection == "row") {
                Row(modifier = styledModifier.fillMaxWidth()) {
                    block.children.forEach { child ->
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            renderChild(child)
                        }
                    }
                }
            } else {
                Column(modifier = styledModifier.fillMaxWidth()) {
                    block.children.forEach { child -> renderChild(child) }
                }
            }
        }
        else -> {
            Box(modifier = styledModifier) {
                RenderFlexChildBlock(
                    childBlock = block,
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
                    registerStableLayoutKey = true
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeChantScore(
    block: ChantScoreBlock,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val groups = remember(block.units) {
        buildList {
            var start = 0
            while (start < block.units.size) {
                var end = start + 1
                while (end < block.units.size && block.units[end - 1].keepWithNext) end++
                add(block.units.subList(start, end))
                start = end
            }
        }
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groups.forEach { group ->
            Row(verticalAlignment = Alignment.Bottom) {
                group.forEach { unit ->
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .drawBehind {
                                val y = size.height - 1.dp.toPx()
                                if (unit.underlineBefore) drawLine(Color.Gray, Offset(0f, y), Offset(size.width / 2f, y), 1.dp.toPx())
                                if (unit.underlineAfter) drawLine(Color.Gray, Offset(size.width / 2f, y), Offset(size.width, y), 1.dp.toPx())
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!unit.isDropCap) {
                            Text(text = unit.neume, style = textStyle, maxLines = 1, softWrap = false)
                        }
                        Text(text = unit.lyric, style = textStyle, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RenderNativeMathBlock(
    block: MathBlock,
    textStyle: TextStyle,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val svgContent = block.svgContent?.takeIf { it.isNotBlank() }
    if (svgContent != null) {
        val imageRequest = Builder(LocalContext.current)
            .data(SvgData(svgContent))
            .listener(
                onError = { _, result ->
                    Timber.e(result.throwable, "Coil failed to load SVG for native vertical MathBlock.")
                }
            )
            .build()
        AsyncImage(
            model = imageRequest,
            contentDescription = block.altText ?: "Equation",
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (block.isFromMathJax) ColorFilter.tint(textStyle.color) else null,
            imageLoader = imageLoader
        )
    } else {
        Text(
            text = block.altText ?: "[Equation not available]",
            style = textStyle,
            modifier = modifier
        )
    }
}

internal fun parseEmphasisAnnotation(annotation: String, defaultColor: Color): TextEmphasis {
    Timber.d("Parsing annotation string: '$annotation'")
    val map = annotation.split(';').filter { it.isNotBlank() }.associate {
        val (key, value) = it.split(':', limit = 2)
        key to value
    }
    val emphasis = TextEmphasis(
        style = map["s"],
        fill = map["f"],
        color = map["c"]?.toULongOrNull()?.let { Color(it) } ?: defaultColor,
        position = map["p"])
    Timber.d("Parsed annotation to object: $emphasis")
    return emphasis
}

internal fun findFuzzyMatch(source: String, target: String, ignoreCase: Boolean = true): IntRange? {
    if (target.isBlank()) return null
    val targetWords = target.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (targetWords.isEmpty()) return null

    var searchStart = 0
    while (searchStart < source.length) {
        val firstIdx = source.indexOf(targetWords[0], searchStart, ignoreCase = ignoreCase)
        if (firstIdx == -1) return null

        var currentIdx = firstIdx + targetWords[0].length
        var allMatch = true

        for (i in 1 until targetWords.size) {
            while (currentIdx < source.length && source[currentIdx].isWhitespace()) {
                currentIdx++
            }
            if (currentIdx >= source.length) {
                allMatch = false
                break
            }

            val word = targetWords[i]
            if (source.regionMatches(currentIdx, word, 0, word.length, ignoreCase = ignoreCase)) {
                currentIdx += word.length
            } else {
                allMatch = false
                break
            }
        }

        if (allMatch) return firstIdx until currentIdx
        searchStart = firstIdx + 1
    }
    return null
}

internal fun getHighlightOffsetsInBlock(
    block: TextContentBlock, highlight: UserHighlight
): IntRange? {
    @Suppress("REDUNDANT_ELSE_IN_WHEN") val blockStartAbs = when (block) {
        is ParagraphBlock -> block.startCharOffsetInSource
        is HeaderBlock -> block.startCharOffsetInSource
        is QuoteBlock -> block.startCharOffsetInSource
        is ListItemBlock -> block.startCharOffsetInSource
        else -> 0
    }
    val blockEndAbs = block.endCharOffsetInSource
        .takeIf { it > blockStartAbs }
        ?: (blockStartAbs + block.content.text.length)
    val blockText = block.content.text
    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
        "map_start blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
            "blockAbs=$blockStartAbs..$blockEndAbs blockLen=${blockText.length} " +
            "hasPreciseLocator=${highlight.locator.hasTextRange} " +
            highlight.androidHighlightRenderLabel()
    )

    locatorHighlightOffsetsInBlock(
        blockText = blockText,
        blockStartAbs = blockStartAbs,
        blockEndAbs = blockEndAbs,
        blockIndex = block.blockIndex,
        blockCfi = block.cfi,
        highlight = highlight
    )?.let { return it }

    if (block.cfi == null) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=missing_block_cfi blockIndex=${block.blockIndex} blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }

    val blockPath = CfiUtils.getPath(block.cfi!!)
    val sourceCfi = highlight.locator.cfi?.takeIf { it.isNotBlank() } ?: highlight.cfi
    val parts = sourceCfi.split('|')
    val startCfi = parts.firstOrNull() ?: highlight.cfi
    val endCfi = parts.lastOrNull()
    val isMultipartHighlight = endCfi != null && endCfi != startCfi

    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
        "map_check blockCfi=${block.cfi} blockPath=$blockPath " +
            "blockAbs=$blockStartAbs..$blockEndAbs blockLen=${block.content.text.length} " +
            "highlightId=${highlight.id} highlightChapter=${highlight.chapterIndex} " +
            "highlightCfi=$sourceCfi startCfi=$startCfi endCfi=$endCfi " +
            "highlightTextLen=${highlight.text.length} highlightText='${highlightDiagSnippet(highlight.text)}'"
    )

    val relevantPart = parts.find { cfiPart ->
        val highlightPath = CfiUtils.getPath(cfiPart)

        if (highlightPath.startsWith(blockPath)) return@find true

        val highlightSegments = highlightPath.split('/').filter { it.isNotEmpty() }
        val blockSegments = blockPath.split('/').filter { it.isNotEmpty() }

        if (highlightSegments.size > blockSegments.size) {
            val pathWithoutFirst = "/" + highlightSegments.drop(1).joinToString("/")
            if (pathWithoutFirst.startsWith(blockPath)) return@find true
        }

        if (highlightSegments.isNotEmpty() && blockSegments.isNotEmpty()) {
            if (highlightSegments[0] != blockSegments[0]) {
                val highlightTail = highlightSegments.drop(1)
                val blockTail = blockSegments.drop(1)
                if (blockTail.isNotEmpty() && highlightTail.size >= blockTail.size) {
                    var match = true
                    for (i in blockTail.indices) {
                        if (blockTail[i] != highlightTail[i]) {
                            match = false
                            break
                        }
                    }
                    if (match) return@find true
                }
            }
        }
        false
    }

    if (relevantPart != null) {
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_relevant_part blockCfi=${block.cfi} highlightId=${highlight.id} part=$relevantPart"
        )
    }

    val highlightText = highlight.text

    if (blockText.isEmpty() || highlightText.isEmpty()) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=empty_text blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "blockTextLen=${blockText.length} highlightTextLen=${highlightText.length} " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }

    val isIntermediateBlock = relevantPart == null &&
        isMultipartHighlight &&
        CfiUtils.isPathStrictlyBetween(block.cfi!!, startCfi, endCfi!!)

    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
        "map_decision blockCfi=${block.cfi} highlightId=${highlight.id} " +
            "relevantPart=$relevantPart isIntermediateBlock=$isIntermediateBlock"
    )

    if (relevantPart == null) {
        if (!isIntermediateBlock) {
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_skip reason=no_relevant_cfi_part blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "startCfi=$startCfi endCfi=$endCfi " +
                    highlight.androidHighlightRenderLabel()
            )
            return null
        }
        if (highlightText.contains(blockText, ignoreCase = false)) {
            val range = 0 until blockText.length
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_result reason=intermediate_exact blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "range=$range " + highlight.androidHighlightRenderLabel()
            )
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_result reason=intermediate_exact blockCfi=${block.cfi} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
            )
            return range
        }
        if (highlightText.contains(blockText, ignoreCase = true)) {
            val range = 0 until blockText.length
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_result reason=intermediate_exact_ignore_case blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "range=$range " + highlight.androidHighlightRenderLabel()
            )
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_result reason=intermediate_exact_ignore_case blockCfi=${block.cfi} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
            )
            return range
        }
        val normBlock = blockText.filter { !it.isWhitespace() }
        val normHighlight = highlightText.filter { !it.isWhitespace() }
        return if (normBlock.isNotBlank() && normHighlight.contains(normBlock, ignoreCase = true)) {
            val range = 0 until blockText.length
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_result reason=intermediate_normalized blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "range=$range " + highlight.androidHighlightRenderLabel()
            )
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_result reason=intermediate_normalized blockCfi=${block.cfi} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
            )
            range
        } else {
            Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                "map_skip reason=intermediate_text_miss blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                    "blockText='${highlightDiagSnippet(blockText)}' " +
                    highlight.androidHighlightRenderLabel()
            )
            null
        }
    }

    if (relevantPart != null) {
        fun arePathsEquivalent(path1: String, path2: String): Boolean {
            val p1 = CfiUtils.getPath(path1).split('/').filter { it.isNotEmpty() }
            val p2 = CfiUtils.getPath(path2).split('/').filter { it.isNotEmpty() }

            if (p1 == p2) return true

            if (p1.size == p2.size && p1.isNotEmpty()) {
                return p1.drop(1) == p2.drop(1)
            }
            return false
        }

        val startMatches = arePathsEquivalent(startCfi, block.cfi!!)
        val endMatches = if (endCfi != null) arePathsEquivalent(endCfi, block.cfi!!) else false

        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_path_equivalence blockCfi=${block.cfi} highlightId=${highlight.id} " +
                "startMatches=$startMatches endMatches=$endMatches"
        )

        if (startMatches || endMatches) {
            val startAbs = CfiUtils.getOffsetOrNull(startCfi)
            val endAbs = endCfi?.let { CfiUtils.getOffsetOrNull(it) }
            val startLocal = startAbs?.let {
                cfiOffsetToBlockLocal(
                    offset = it,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )
            }
            val endLocal = endAbs?.let {
                cfiOffsetToBlockLocal(
                    offset = it,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )
            }
            Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                "map_offset_inputs blockCfi=${block.cfi} highlightId=${highlight.id} " +
                    "blockAbs=$blockStartAbs..$blockEndAbs cfiOffsets=$startAbs..$endAbs " +
                    "localOffsets=$startLocal..$endLocal"
            )
            if (startMatches && endMatches && startLocal != null && endLocal != null) {
                val rangeStartLocal = minOf(startLocal, endLocal)
                val rangeEndLocal = maxOf(startLocal, endLocal)
                if (rangeEndLocal <= 0 || rangeStartLocal >= blockText.length) {
                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                        "map_skip reason=same_path_split_outside_offsets blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                            "highlightLocal=$rangeStartLocal..$rangeEndLocal blockLen=${blockText.length} " +
                            highlight.androidHighlightRenderLabel()
                    )
                    Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                        "map_skip reason=same_path_split_outside_offsets blockCfi=${block.cfi} " +
                            "highlightId=${highlight.id} highlightLocal=$rangeStartLocal..$rangeEndLocal " +
                            "blockAbs=$blockStartAbs..$blockEndAbs"
                    )
                    return null
                }
            } else {
                if (startMatches && startLocal != null && startLocal >= blockText.length) {
                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                        "map_skip reason=start_offset_after_block blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                            "startLocal=$startLocal blockLen=${blockText.length} " +
                            highlight.androidHighlightRenderLabel()
                    )
                    return null
                }
                if (endMatches && endLocal != null && endLocal <= 0) {
                    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                        "map_skip reason=end_offset_before_block blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                            "endLocal=$endLocal blockLen=${blockText.length} " +
                            highlight.androidHighlightRenderLabel()
                    )
                    return null
                }
            }
            var s = 0
            var e = blockText.length

            if (startMatches) {
                val rawOffset = startAbs ?: CfiUtils.getOffset(startCfi)
                val relOffset = cfiOffsetToBlockLocal(
                    offset = rawOffset,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )

                if (relOffset < 0) {
                    s = 0
                } else {
                    val safeStart = (relOffset - 50).coerceAtLeast(0)
                    val safeEnd = (relOffset + 50).coerceAtMost(blockText.length)

                    if (safeStart < safeEnd) {
                        val windowText = blockText.substring(safeStart, safeEnd)
                        val prefix = highlightText.trim().take(20).trim()

                        var snapped = false
                        if (prefix.isNotEmpty()) {
                            val matches = mutableListOf<Int>()
                            var idx = windowText.indexOf(prefix, ignoreCase = true)
                            while (idx != -1) {
                                matches.add(idx)
                                idx = windowText.indexOf(prefix, idx + 1, ignoreCase = true)
                            }

                            if (matches.isNotEmpty()) {
                                val targetRel = relOffset - safeStart
                                val bestRel = matches.minByOrNull { abs(it - targetRel) }!!
                                val newS = safeStart + bestRel
                                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                                    "map_snap_start blockCfi=${block.cfi} highlightId=${highlight.id} " +
                                        "fromRel=$relOffset toRel=$newS prefix='$prefix'"
                                )
                                s = newS
                                snapped = true
                            }
                        }

                        if (!snapped) {
                            s = relOffset
                        }
                    } else {
                        s = relOffset
                    }
                }
            }

            if (endMatches) {
                val rawOffset = endAbs ?: CfiUtils.getOffset(endCfi!!)
                val relOffset = cfiOffsetToBlockLocal(
                    offset = rawOffset,
                    blockStartAbs = blockStartAbs,
                    blockEndAbs = blockEndAbs,
                    textLength = blockText.length
                )

                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                    "map_end_match blockCfi=${block.cfi} highlightId=${highlight.id} " +
                        "rawOffset=$rawOffset relOffset=$relOffset blockLen=${blockText.length}"
                )

                e = if (relOffset > blockText.length) {
                    blockText.length
                } else {
                    relOffset
                }
            }

            s = s.coerceIn(0, blockText.length)
            e = e.coerceIn(0, blockText.length)

            if (s < e) {
                val range = s until e
                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                    "map_result reason=cfi_offsets blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                        "range=$range startMatches=$startMatches endMatches=$endMatches " +
                        "startAbs=$startAbs endAbs=$endAbs startLocal=$startLocal endLocal=$endLocal " +
                        highlight.androidHighlightRenderLabel()
                )
                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                    "map_result reason=cfi_offsets blockCfi=${block.cfi} " +
                        "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
                )
                return range
            } else {
                Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                    "map_skip reason=invalid_cfi_range blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                        "range=$s..$e startMatches=$startMatches endMatches=$endMatches " +
                        highlight.androidHighlightRenderLabel()
                )
                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).w(
                    "map_skip reason=invalid_range blockCfi=${block.cfi} " +
                        "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$s..$e"
                )
                return null
            }
        }
    }

    if (highlightText.contains(blockText, ignoreCase = false)) {
        val range = 0 until blockText.length
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=block_inside_highlight_text blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$range " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=block_inside_highlight_text blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
        )
        return range
    }
    if (highlightText.contains(blockText, ignoreCase = true)) {
        val range = 0 until blockText.length
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=block_inside_highlight_text_ignore_case blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$range " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=block_inside_highlight_text_ignore_case blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
        )
        return range
    }

    var startIndex = blockText.indexOf(highlightText, ignoreCase = false)
    if (startIndex == -1) {
        startIndex = blockText.indexOf(highlightText, ignoreCase = true)
    }

    if (startIndex >= 0) {
        val range = startIndex until (startIndex + highlightText.length)
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=highlight_text_inside_block blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$range startIndex=$startIndex " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=highlight_text_inside_block blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$range"
        )
        return range
    }

    val match = findFuzzyMatch(blockText, highlightText)
    if (match != null) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=fuzzy_text blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "range=$match " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_result reason=fuzzy_text blockCfi=${block.cfi} " +
                "blockAbs=$blockStartAbs..$blockEndAbs highlightId=${highlight.id} range=$match"
        )
        return match
    }

    if (relevantPart != null) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=cfi_match_text_miss blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "relevantPart=$relevantPart " + highlight.androidHighlightRenderLabel()
        )
        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
            "map_skip reason=cfi_match_text_miss blockCfi=${block.cfi} " +
                "highlightId=${highlight.id} highlightCfi=${highlight.cfi}"
        )
    }

    if (highlight.locator.hasTextRange) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_skip reason=precise_locator_and_cfi_miss blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
                "sourceCfi=$sourceCfi " + highlight.androidHighlightRenderLabel()
        )
        return null
    }

    Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
        "map_skip reason=no_mapping_match blockIndex=${block.blockIndex} blockCfi=${block.cfi} " +
            highlight.androidHighlightRenderLabel()
    )
    return null
}

internal fun androidHighlightSourceCfi(highlight: UserHighlight): String {
    return highlight.locator.cfi?.takeIf { it.isNotBlank() } ?: highlight.cfi
}

internal fun androidCfiPathsEquivalent(first: String, second: String): Boolean {
    val firstPath = CfiUtils.getPath(first)
    val secondPath = CfiUtils.getPath(second)
    if (firstPath == secondPath || firstPath.startsWith("$secondPath/") || secondPath.startsWith("$firstPath/")) {
        return true
    }
    val firstParts = firstPath.split('/').filter { it.isNotEmpty() }
    val secondParts = secondPath.split('/').filter { it.isNotEmpty() }
    if (firstParts == secondParts) return true
    return firstParts.size == secondParts.size &&
        firstParts.isNotEmpty() &&
        firstParts.drop(1) == secondParts.drop(1)
}

internal fun androidHighlightHasMultipartCfiRange(highlight: UserHighlight): Boolean {
    val parts = androidHighlightSourceCfi(highlight)
        .split('|')
        .filter { it.startsWith("/") }
    if (parts.size < 2) return false
    val first = parts.first()
    return parts.drop(1).any { !androidCfiPathsEquivalent(first, it) }
}

internal fun androidHighlightCfiTouchesBlock(highlight: UserHighlight, blockCfi: String?): Boolean {
    val blockPath = blockCfi?.takeIf { it.startsWith("/") } ?: return false
    return androidHighlightSourceCfi(highlight)
        .split('|')
        .filter { it.startsWith("/") }
        .any { androidCfiPathsEquivalent(it, blockPath) }
}

internal fun cfiOffsetToBlockLocal(
    offset: Int,
    blockStartAbs: Int,
    blockEndAbs: Int,
    textLength: Int
): Int {
    return when {
        offset in 0..textLength -> offset
        offset in blockStartAbs..blockEndAbs -> offset - blockStartAbs
        else -> offset
    }
}

internal fun locatorHighlightOffsetsInBlock(
    blockText: String,
    blockStartAbs: Int,
    blockEndAbs: Int,
    blockIndex: Int,
    blockCfi: String?,
    highlight: UserHighlight
): IntRange? {
    if (blockText.isEmpty()) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=empty_block_text blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    if (androidHighlightHasMultipartCfiRange(highlight)) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=multipart_cfi_uses_cfi_mapper blockIndex=$blockIndex blockCfi=$blockCfi " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val locatorBlockIndex = highlight.locator.blockIndex
    val blockMatchesLocator = locatorBlockIndex != null && locatorBlockIndex == blockIndex
    val cfiMatchesBlock = androidHighlightCfiTouchesBlock(highlight, blockCfi)
    val hasStructuralScope = locatorBlockIndex != null || androidHighlightSourceCfi(highlight).startsWith("/")
    if (hasStructuralScope && !blockMatchesLocator && !cfiMatchesBlock) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_miss reason=structural_scope_miss blockIndex=$blockIndex blockCfi=$blockCfi " +
                "blockMatchesLocator=$blockMatchesLocator cfiMatchesBlock=$cfiMatchesBlock " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val start = highlight.locator.startOffset ?: run {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=missing_start blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val end = highlight.locator.endOffset ?: run {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_skip reason=missing_end blockAbs=$blockStartAbs..$blockEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val rangeStartAbs = minOf(start, end)
    val rangeEndAbs = maxOf(start, end)
    if (rangeEndAbs <= blockStartAbs || rangeStartAbs >= blockEndAbs) {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_miss reason=no_intersection blockAbs=$blockStartAbs..$blockEndAbs " +
                "highlightAbs=$rangeStartAbs..$rangeEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        return null
    }
    val localStart = (rangeStartAbs - blockStartAbs).coerceIn(0, blockText.length)
    val localEnd = (rangeEndAbs - blockStartAbs).coerceIn(localStart, blockText.length)
    return if (localStart < localEnd) {
        val range = localStart until localEnd
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "map_result reason=locator_offsets blockAbs=$blockStartAbs..$blockEndAbs " +
                "range=$range highlightAbs=$rangeStartAbs..$rangeEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        range
    } else {
        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
            "locator_check_miss reason=invalid_local_range blockAbs=$blockStartAbs..$blockEndAbs " +
                "local=$localStart..$localEnd highlightAbs=$rangeStartAbs..$rangeEndAbs " +
                highlight.androidHighlightRenderLabel()
        )
        null
    }
}

internal fun List<ContentBlock>.extractTextBlocks(): List<TextContentBlock> {
    val result = mutableListOf<TextContentBlock>()
    for (block in this) {
        when (block) {
            is WrappingContentBlock -> result.addAll(block.paragraphsToWrap)
            is FlexContainerBlock -> result.addAll(block.children.extractTextBlocks())
            is TableBlock -> {
                block.rows.forEach { row ->
                    row.forEach { cell ->
                        result.addAll(cell.content.extractTextBlocks())
                    }
                }
            }
            is TextContentBlock -> result.add(block)
            else -> {}
        }
    }
    return result
}

internal fun LayoutCoordinates.androidEpubPageContentBounds(
    horizontalPaddingPx: Int,
    verticalPaddingPx: Int
): AndroidEpubPageContentBounds {
    val pageTopPx = positionInWindow().y.roundToInt()
    val contentTopPx = pageTopPx + verticalPaddingPx
    val contentBottomPx = pageTopPx + size.height - verticalPaddingPx
    return AndroidEpubPageContentBounds(
        topPx = contentTopPx,
        bottomPx = contentBottomPx,
        widthPx = (size.width - (horizontalPaddingPx * 2)).coerceAtLeast(0),
        heightPx = (contentBottomPx - contentTopPx).coerceAtLeast(0),
        pageWidthPx = size.width,
        pageHeightPx = size.height,
        horizontalPaddingPx = horizontalPaddingPx,
        verticalPaddingPx = verticalPaddingPx
    )
}

internal fun logReaderUiAndroidEpubCutoff(message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(ReaderUiCutoffLogTag, message)
}

internal fun logReaderUiAndroidEpubPageGapDiag(message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(ReaderUiPageGapDiagLogTag, message)
}

internal fun logAndroidEpubEdgeDiag(message: String) {
    if (!BuildConfig.DEBUG) return
    Log.d(AndroidEpubEdgeDiagLogTag, message)
}

internal fun readerHorizontalOverflowPx(
    minLineLeftPx: Int,
    maxLineRightPx: Int,
    maxLineVisualWidthPx: Int,
    boxWidthPx: Int
): Int = maxOf(
    (-minLineLeftPx).coerceAtLeast(0),
    (maxLineRightPx - boxWidthPx).coerceAtLeast(0),
    (maxLineVisualWidthPx - boxWidthPx).coerceAtLeast(0)
)

internal fun Modifier.androidEpubNaturalHeight(): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        )
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
)

internal fun TextContentBlock.androidEpubSourceRangeLabel(): String {
    val start = startCharOffsetInSource
    val end = endCharOffsetInSource.takeIf { it > start } ?: (start + content.text.length)
    return "$start..$end"
}

internal fun ContentBlock.androidEpubSourceRangeLabel(): String {
    return when (this) {
        is TextContentBlock -> androidEpubSourceRangeLabel()
        else -> "unknown"
    }
}

internal fun ContentBlock.androidEpubTextCharCount(): Int {
    return when (this) {
        is TextContentBlock -> content.text.length
        is TableBlock -> rows.flatten().sumOf { cell -> cell.content.sumOf { block -> block.androidEpubTextCharCount() } }
        is FlexContainerBlock -> children.sumOf { it.androidEpubTextCharCount() }
        is WrappingContentBlock -> paragraphsToWrap.sumOf { it.androidEpubTextCharCount() }
        else -> 0
    }
}
internal fun TextContentBlock.androidEpubKindName(): String {
    return when (this) {
        is HeaderBlock -> "header"
        is ParagraphBlock -> "paragraph"
        is QuoteBlock -> "quote"
        is ListItemBlock -> "list_item"
        else -> "text"
    }
}

internal fun ContentBlock.androidEpubKindName(): String {
    return when (this) {
        is HeaderBlock -> "header"
        is ParagraphBlock -> "paragraph"
        is QuoteBlock -> "quote"
        is ListItemBlock -> "list_item"
        is TextContentBlock -> "text"
        is ImageBlock -> "image"
        is MathBlock -> "math"
        is TableBlock -> "table"
        is FlexContainerBlock -> "flex"
        is ChantScoreBlock -> "chant"
        is WrappingContentBlock -> "wrapping"
        is SpacerBlock -> "spacer"
    }
}

internal fun logAndroidEpubPageBoundsIfNeeded(
    pageIndex: Int,
    pageContentBounds: AndroidEpubPageContentBounds,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
) {
    val signature = "page_bounds:$pageIndex:${pageContentBounds.pageWidthPx}x${pageContentBounds.pageHeightPx}:" +
        "${pageContentBounds.widthPx}x${pageContentBounds.heightPx}:" +
        "${pageContentBounds.horizontalPaddingPx}x${pageContentBounds.verticalPaddingPx}"
    if (signatureAlreadyLogged(signature)) return
    markSignatureLogged(signature)
    logReaderUiAndroidEpubCutoff(
        "cutoff_probe layer=android_page_bounds page=${pageIndex + 1} " +
            "contentPx=${pageContentBounds.widthPx}x${pageContentBounds.heightPx} " +
            "pagePx=${pageContentBounds.pageWidthPx}x${pageContentBounds.pageHeightPx} " +
            "contentTopPx=${pageContentBounds.topPx} contentBottomPx=${pageContentBounds.bottomPx} " +
            "pageClipBottomPx=${pageContentBounds.pageClipBottomPx} " +
            "paddingPx=${pageContentBounds.horizontalPaddingPx}x${pageContentBounds.verticalPaddingPx} " +
            diagnosticsContext
    )
}
internal fun logAndroidEpubBlockOverflowIfNeeded(
    pageIndex: Int,
    block: ContentBlock,
    coordinates: LayoutCoordinates,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
): Boolean {
    val bounds = pageContentBounds ?: return false
    val blockTopPx = coordinates.positionInWindow().y.roundToInt()
    val blockBottomPx = blockTopPx + coordinates.size.height
    val contentOverflowPx = blockBottomPx - bounds.bottomPx
    val pageClipOverflowPx = blockBottomPx - bounds.pageClipBottomPx
    if (pageClipOverflowPx <= AndroidEpubCutoffTolerancePx) return false
    val relativeTopPx = blockTopPx - bounds.topPx
    val signature = "block:$pageIndex:${block.blockIndex}:$relativeTopPx:${coordinates.size.height}:$pageClipOverflowPx"
    if (signatureAlreadyLogged(signature)) return false
    markSignatureLogged(signature)
    logReaderUiAndroidEpubCutoff(
        "cutoff_probe layer=android_rendered_block_overflow page=${pageIndex + 1} " +
            "block=${block.blockIndex} kind=${block.androidEpubKindName()} " +
            "blockTopPx=$relativeTopPx blockHeightPx=${coordinates.size.height} " +
            "blockBottomPx=${blockBottomPx - bounds.topPx} contentPx=${bounds.widthPx}x${bounds.heightPx} " +
            "pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} contentOverflowPx=$contentOverflowPx " +
            "pageClipOverflowPx=$pageClipOverflowPx " +
            "expectedHeightPx=${block.expectedHeight} actualHeightPx=${coordinates.size.height} " +
            "sourceRange=${block.androidEpubSourceRangeLabel()} textChars=${block.androidEpubTextCharCount()} " +
            "paddingPx=${bounds.horizontalPaddingPx}x${bounds.verticalPaddingPx} $diagnosticsContext"
    )
    return true
}

internal fun logAndroidEpubPageBlockBoundsIfNeeded(
    pageIndex: Int,
    triggerBlock: ContentBlock,
    renderedBounds: Collection<AndroidEpubRenderedBlockBounds>,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
) {
    val bounds = pageContentBounds ?: return
    val sortedBounds = renderedBounds.sortedBy { it.topPx }
    if (sortedBounds.isEmpty()) return
    val signature = "page_block_bounds:$pageIndex:${triggerBlock.blockIndex}:${sortedBounds.size}:${sortedBounds.maxOf { it.bottomPx }}"
    if (signatureAlreadyLogged(signature)) return
    markSignatureLogged(signature)

    var cumulativeExpectedBottomPx = 0
    logReaderUiAndroidEpubCutoff(
        "cutoff_probe layer=android_page_block_bounds_summary page=${pageIndex + 1} " +
            "triggerBlock=${triggerBlock.blockIndex} blockCount=${sortedBounds.size} " +
            "contentPx=${bounds.widthPx}x${bounds.heightPx} pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} " +
            "contentBottomPx=${bounds.bottomPx - bounds.topPx} pageClipBottomPx=${bounds.pageClipBottomPx - bounds.topPx} " +
            diagnosticsContext
    )
    sortedBounds.forEach { blockBounds ->
        val gapFromExpectedPreviousPx = blockBounds.topPx - cumulativeExpectedBottomPx
        cumulativeExpectedBottomPx += blockBounds.expectedHeightPx
        val driftPx = blockBounds.bottomPx - cumulativeExpectedBottomPx
        logReaderUiAndroidEpubCutoff(
            "cutoff_probe layer=android_page_block_bounds page=${pageIndex + 1} " +
                "triggerBlock=${triggerBlock.blockIndex} block=${blockBounds.blockIndex} kind=${blockBounds.kind} " +
                "boxPx=${blockBounds.leftPx},${blockBounds.topPx},${blockBounds.widthPx}x${blockBounds.heightPx} " +
                "bottomPx=${blockBounds.bottomPx} expectedHeightPx=${blockBounds.expectedHeightPx} " +
                "expectedCumulativeBottomPx=$cumulativeExpectedBottomPx driftPx=$driftPx " +
                "gapFromExpectedPreviousPx=$gapFromExpectedPreviousPx " +
                "marginPx=${blockBounds.marginTopPx},${blockBounds.marginBottomPx} " +
                "paddingPx=${blockBounds.paddingTopPx},${blockBounds.paddingBottomPx} " +
                "sourceRange=${blockBounds.sourceRange} textChars=${blockBounds.textChars}"
        )
    }
}
internal fun logAndroidEpubRenderedTablePageIfNeeded(
    pageIndex: Int,
    renderedBounds: Collection<AndroidEpubRenderedBlockBounds>,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
) {
    val bounds = pageContentBounds ?: return
    val sortedBounds = renderedBounds.sortedBy { it.topPx }
    val tableBounds = sortedBounds.filter { it.kind == "table" }
    if (tableBounds.isEmpty()) return

    val lastBlock = sortedBounds.maxBy { it.bottomPx }
    val contentBottomRelativePx = bounds.bottomPx - bounds.topPx
    val contentBottomGapPx = contentBottomRelativePx - lastBlock.bottomPx
    val expectedTotalHeightPx = sortedBounds.sumOf { it.expectedHeightPx.coerceAtLeast(0) }
    val actualTotalHeightPx = sortedBounds.sumOf { it.heightPx.coerceAtLeast(0) }
    val signature = "rendered_table_page:$pageIndex:${tableBounds.joinToString(",") { it.blockIndex.toString() }}:${lastBlock.bottomPx}:${sortedBounds.size}"
    if (signatureAlreadyLogged(signature)) return
    markSignatureLogged(signature)

    val blockSummary = sortedBounds.joinToString(",") { block ->
        "${block.blockIndex}:${block.kind}:top=${block.topPx}:h=${block.heightPx}:expected=${block.expectedHeightPx}:gapAfter=${contentBottomRelativePx - block.bottomPx}:mt=${block.marginTopPx}:mb=${block.marginBottomPx}:pt=${block.paddingTopPx}:pb=${block.paddingBottomPx}:chars=${block.textChars}"
    }
    logReaderUiAndroidEpubPageGapDiag(
        "decision=rendered_table_page page=${pageIndex + 1} blockCount=${sortedBounds.size} " +
            "tableBlocks=${tableBounds.joinToString(",") { it.blockIndex.toString() }} " +
            "lastBlock=${lastBlock.blockIndex} lastKind=${lastBlock.kind} lastBottomPx=${lastBlock.bottomPx} " +
            "contentBottomPx=$contentBottomRelativePx contentBottomGapPx=$contentBottomGapPx " +
            "expectedTotalHeightPx=$expectedTotalHeightPx actualTotalHeightPx=$actualTotalHeightPx " +
            "expectedActualDeltaPx=${expectedTotalHeightPx - actualTotalHeightPx} " +
            "contentPx=${bounds.widthPx}x${bounds.heightPx} pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} " +
            "blocks=$blockSummary $diagnosticsContext"
    )
}
internal fun logAndroidEpubRenderedTablePartIfNeeded(
    pageIndex: Int,
    tableBlockIndex: Int,
    partKind: String,
    rowIndex: Int,
    cellIndex: Int?,
    coordinates: LayoutCoordinates,
    pageContentBounds: AndroidEpubPageContentBounds?,
    stackRows: Boolean,
    tableExpectedHeightPx: Int,
    rowCount: Int,
    textChars: Int,
    paddingTopPx: Int,
    paddingBottomPx: Int,
    isLikelySpeakerCell: Boolean,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
) {
    val bounds = pageContentBounds ?: return
    val topPx = coordinates.positionInWindow().y.roundToInt() - bounds.topPx
    val heightPx = coordinates.size.height
    val bottomPx = topPx + heightPx
    val contentBottomPx = bounds.bottomPx - bounds.topPx
    val contentBottomGapPx = contentBottomPx - bottomPx
    val cellLabel = cellIndex?.toString() ?: "none"
    val signature = "rendered_table_part:$pageIndex:$tableBlockIndex:$partKind:$rowIndex:$cellLabel:$topPx:$heightPx"
    if (signatureAlreadyLogged(signature)) return
    markSignatureLogged(signature)

    logReaderUiAndroidEpubPageGapDiag(
        "decision=rendered_table_part page=${pageIndex + 1} tableBlock=$tableBlockIndex " +
            "part=$partKind row=$rowIndex cell=$cellLabel stackRows=$stackRows " +
            "topPx=$topPx heightPx=$heightPx bottomPx=$bottomPx contentBottomPx=$contentBottomPx " +
            "gapAfterPx=$contentBottomGapPx widthPx=${coordinates.size.width} " +
            "tableExpectedHeightPx=$tableExpectedHeightPx rowCount=$rowCount textChars=$textChars " +
            "paddingTopPx=$paddingTopPx paddingBottomPx=$paddingBottomPx speakerLike=$isLikelySpeakerCell " +
            "contentPx=${bounds.widthPx}x${bounds.heightPx} pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} " +
            diagnosticsContext
    )
}
internal fun logAndroidEpubRenderedPageGapIfNeeded(
    pageIndex: Int,
    renderedBounds: Collection<AndroidEpubRenderedBlockBounds>,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    signatureAlreadyLogged: (String) -> Boolean,
    markSignatureLogged: (String) -> Unit
) {
    val bounds = pageContentBounds ?: return
    val sortedBounds = renderedBounds.sortedBy { it.topPx }
    if (sortedBounds.isEmpty()) return

    val lastBlock = sortedBounds.maxBy { it.bottomPx }
    val contentBottomRelativePx = bounds.bottomPx - bounds.topPx
    val pageClipBottomRelativePx = bounds.pageClipBottomPx - bounds.topPx
    val contentBottomGapPx = contentBottomRelativePx - lastBlock.bottomPx
    val pageClipBottomGapPx = pageClipBottomRelativePx - lastBlock.bottomPx
    val minGapPx = maxOf(
        AndroidEpubLargeBottomGapMinPx,
        (bounds.heightPx * AndroidEpubLargeBottomGapPageFraction).roundToInt()
    )
    if (contentBottomGapPx < minGapPx) return

    val expectedTotalHeightPx = sortedBounds.sumOf { it.expectedHeightPx.coerceAtLeast(0) }
    val actualTotalHeightPx = sortedBounds.sumOf { it.heightPx.coerceAtLeast(0) }
    val signature = "rendered_page_gap:$pageIndex:${sortedBounds.size}:${lastBlock.blockIndex}:${lastBlock.bottomPx}:$contentBottomGapPx"
    if (signatureAlreadyLogged(signature)) return
    markSignatureLogged(signature)

    val blockSummary = sortedBounds.joinToString(",") { block ->
        "${block.blockIndex}:${block.kind}:top=${block.topPx}:h=${block.heightPx}:expected=${block.expectedHeightPx}:mt=${block.marginTopPx}:mb=${block.marginBottomPx}"
    }
    logReaderUiAndroidEpubPageGapDiag(
        "decision=rendered_gap page=${pageIndex + 1} blockCount=${sortedBounds.size} " +
            "lastBlock=${lastBlock.blockIndex} lastKind=${lastBlock.kind} lastBottomPx=${lastBlock.bottomPx} " +
            "contentBottomPx=$contentBottomRelativePx contentBottomGapPx=$contentBottomGapPx " +
            "pageClipBottomGapPx=$pageClipBottomGapPx minGapPx=$minGapPx " +
            "expectedTotalHeightPx=$expectedTotalHeightPx actualTotalHeightPx=$actualTotalHeightPx " +
            "expectedActualDeltaPx=${expectedTotalHeightPx - actualTotalHeightPx} " +
            "contentPx=${bounds.widthPx}x${bounds.heightPx} pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} " +
            "blocks=$blockSummary $diagnosticsContext"
    )
    logReaderUiAndroidEpubCutoff(
        "cutoff_probe layer=android_rendered_page_gap page=${pageIndex + 1} " +
            "blockCount=${sortedBounds.size} lastBlock=${lastBlock.blockIndex} lastKind=${lastBlock.kind} " +
            "lastBottomPx=${lastBlock.bottomPx} contentBottomPx=$contentBottomRelativePx " +
            "contentBottomGapPx=$contentBottomGapPx pageClipBottomGapPx=$pageClipBottomGapPx minGapPx=$minGapPx " +
            "contentPx=${bounds.widthPx}x${bounds.heightPx} pagePx=${bounds.pageWidthPx}x${bounds.pageHeightPx} " +
            "expectedTotalHeightPx=$expectedTotalHeightPx actualTotalHeightPx=$actualTotalHeightPx " +
            "lastExpectedHeightPx=${lastBlock.expectedHeightPx} lastActualHeightPx=${lastBlock.heightPx} " +
            "lastSourceRange=${lastBlock.sourceRange} lastTextChars=${lastBlock.textChars} $diagnosticsContext"
    )
}

internal fun logAndroidEpubTextWrapIfNeeded(
    pageIndex: Int,
    block: TextContentBlock,
    layout: TextLayoutResult,
    coordinates: LayoutCoordinates,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    previousSignature: String?
): String? {
    val bounds = pageContentBounds ?: return previousSignature
    val boxWidthPx = coordinates.size.width
    if (boxWidthPx <= 0 || layout.lineCount <= 0) return previousSignature

    var maxLineVisualWidthPx = 0
    var maxLineRightPx = 0
    var minLineLeftPx = Int.MAX_VALUE
    var shortNonBlankLineCount = 0
    var nonBlankLineCount = 0
    val lineSamples = mutableListOf<String>()
    val sampleLimit = minOf(layout.lineCount, 8)
    for (line in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line, visibleEnd = true)
        val lineEndSafe = lineEnd.coerceIn(lineStart, layout.layoutInput.text.length)
        val lineText = layout.layoutInput.text.text.substring(lineStart, lineEndSafe)
        val lineLeft = layout.getLineLeft(line)
        val lineRight = layout.getLineRight(line)
        val lineVisualWidth = abs(lineRight - lineLeft).roundToInt()
        maxLineVisualWidthPx = maxOf(maxLineVisualWidthPx, lineVisualWidth)
        maxLineRightPx = maxOf(maxLineRightPx, maxOf(lineLeft, lineRight).roundToInt())
        minLineLeftPx = minOf(minLineLeftPx, minOf(lineLeft, lineRight).roundToInt())
        if (lineText.isNotBlank()) {
            nonBlankLineCount++
            if (lineVisualWidth < boxWidthPx * AndroidEpubWrapShortLineFraction && lineText.length >= 2) {
                shortNonBlankLineCount++
            }
        }
        if (line < sampleLimit) {
            lineSamples += "${line}:${lineStart}..$lineEnd:${lineVisualWidth}px:'${lineText.replace('\n', ' ').take(24)}'"
        }
    }

    val leftOverflowPx = (-minLineLeftPx).coerceAtLeast(0)
    val rightOverflowPx = (maxLineRightPx - boxWidthPx).coerceAtLeast(0)
    val lineOverflowPx = readerHorizontalOverflowPx(
        minLineLeftPx = minLineLeftPx,
        maxLineRightPx = maxLineRightPx,
        maxLineVisualWidthPx = maxLineVisualWidthPx,
        boxWidthPx = boxWidthPx
    )
    val narrowBox = boxWidthPx < (bounds.widthPx * AndroidEpubWrapNarrowWidthFraction).roundToInt()
    val manyShortLines = nonBlankLineCount >= 3 && shortNonBlankLineCount >= maxOf(2, nonBlankLineCount / 3)
    val textChars = block.content.text.length
    if (lineOverflowPx <= AndroidEpubCutoffTolerancePx && !narrowBox && !manyShortLines) {
        return previousSignature
    }

    val signature = "text_wrap:$pageIndex:${block.blockIndex}:$boxWidthPx:${layout.size.width}:${layout.lineCount}:$lineOverflowPx:$shortNonBlankLineCount"
    if (signature == previousSignature) return previousSignature

    val boxLeftRelativePx = coordinates.positionInWindow().x.roundToInt() - (bounds.pageWidthPx - bounds.widthPx) / 2
    logReaderUiAndroidEpubCutoff(
        "cutoff_probe layer=android_text_wrap page=${pageIndex + 1} block=${block.blockIndex} " +
            "kind=${block.androidEpubKindName()} boxWidthPx=$boxWidthPx layoutWidthPx=${layout.size.width} " +
            "pageContentWidthPx=${bounds.widthPx} lineCount=${layout.lineCount} textChars=$textChars " +
            "maxLineVisualWidthPx=$maxLineVisualWidthPx minLineLeftPx=$minLineLeftPx maxLineRightPx=$maxLineRightPx " +
            "leftOverflowPx=$leftOverflowPx rightOverflowPx=$rightOverflowPx lineOverflowPx=$lineOverflowPx " +
            "narrowBox=$narrowBox shortLines=$shortNonBlankLineCount/$nonBlankLineCount " +
            "boxLeftApproxPx=$boxLeftRelativePx textAlign=${layout.layoutInput.style.textAlign} " +
            "fontSize=${layout.layoutInput.style.fontSize} lineHeight=${layout.layoutInput.style.lineHeight} " +
            "sourceRange=${block.androidEpubSourceRangeLabel()} lines=${lineSamples.joinToString("|")} $diagnosticsContext"
    )
    logAndroidEpubEdgeDiag(
        "edge_probe page=${pageIndex + 1} block=${block.blockIndex} kind=${block.androidEpubKindName()} " +
            "boxWidthPx=$boxWidthPx layoutWidthPx=${layout.size.width} contentWidthPx=${bounds.widthPx} " +
            "minLineLeftPx=$minLineLeftPx maxLineRightPx=$maxLineRightPx maxLineVisualWidthPx=$maxLineVisualWidthPx " +
            "leftOverflowPx=$leftOverflowPx rightOverflowPx=$rightOverflowPx lineOverflowPx=$lineOverflowPx " +
            "lineBreak=${layout.layoutInput.style.lineBreak} textAlign=${layout.layoutInput.style.textAlign} " +
            "fontSize=${layout.layoutInput.style.fontSize} lineHeight=${layout.layoutInput.style.lineHeight} " +
            "sourceRange=${block.androidEpubSourceRangeLabel()} textChars=$textChars $diagnosticsContext"
    )
    return signature
}
internal fun logAndroidEpubTextCutoffIfNeeded(
    pageIndex: Int,
    block: TextContentBlock,
    layout: TextLayoutResult,
    coordinates: LayoutCoordinates,
    pageContentBounds: AndroidEpubPageContentBounds?,
    diagnosticsContext: String,
    previousSignature: String?
): String? {
    val boxTopPx = coordinates.positionInWindow().y.roundToInt()
    val boxHeightPx = coordinates.size.height
    val lastLine = layout.lineCount - 1
    val lastLineTopPx = if (lastLine >= 0) layout.getLineTop(lastLine).roundToInt() else 0
    val lastLineBottomPx = if (lastLine >= 0) layout.getLineBottom(lastLine).roundToInt() else layout.size.height
    val lastLineStart = if (lastLine >= 0) layout.getLineStart(lastLine) else 0
    val lastLineEnd = if (lastLine >= 0) layout.getLineEnd(lastLine, visibleEnd = true) else 0
    val overflowBottomInBoxPx = maxOf(layout.size.height, lastLineBottomPx)
    val boxClipPx = overflowBottomInBoxPx - boxHeightPx
    val bounds = pageContentBounds
    val lineBottomInPagePx = if (bounds != null) {
        boxTopPx + overflowBottomInBoxPx - bounds.topPx
    } else {
        overflowBottomInBoxPx
    }
    val contentOverflowPx = bounds?.let { boxTopPx + overflowBottomInBoxPx - it.bottomPx } ?: 0
    val pageClipOverflowPx = bounds?.let { boxTopPx + overflowBottomInBoxPx - it.pageClipBottomPx } ?: 0
    val contentBottomInsetPx = bounds?.let { it.bottomPx - (boxTopPx + overflowBottomInBoxPx) }
    val pageClipBottomInsetPx = bounds?.let { it.pageClipBottomPx - (boxTopPx + overflowBottomInBoxPx) }
    val bottomEdgeRisk = pageClipBottomInsetPx != null && pageClipBottomInsetPx in 0..AndroidEpubCutoffEdgeProbePx
    if (
        boxClipPx <= AndroidEpubCutoffTolerancePx &&
        pageClipOverflowPx <= AndroidEpubCutoffTolerancePx &&
        !bottomEdgeRisk
    ) {
        return previousSignature
    }

    val signature = buildString {
        append(pageIndex)
        append(':')
        append(block.blockIndex)
        append(':')
        append(coordinates.size.width)
        append('x')
        append(boxHeightPx)
        append(':')
        append(layout.size.width)
        append('x')
        append(layout.size.height)
        append(':')
        append(lastLineBottomPx)
        append(':')
        append(bounds?.pageClipBottomPx ?: -1)
    }
    if (signature == previousSignature) return previousSignature

    val layer = if (boxClipPx > AndroidEpubCutoffTolerancePx) {
        "android_text_clip"
    } else if (pageClipOverflowPx > AndroidEpubCutoffTolerancePx) {
        "android_text_page_overflow"
    } else if (bottomEdgeRisk) {
        "android_text_bottom_edge"
    } else {
        "android_text_page_overflow"
    }
    logReaderUiAndroidEpubCutoff(
        "cutoff_probe layer=$layer page=${pageIndex + 1} block=${block.blockIndex} " +
            "kind=${block.androidEpubKindName()} boxPx=${coordinates.size.width}x$boxHeightPx " +
            "layoutPx=${layout.size.width}x${layout.size.height} lines=${layout.lineCount} " +
            "lastLine=$lastLine lastLineTopPx=$lastLineTopPx lastLineBottomPx=$lastLineBottomPx " +
            "lastLineBottomInPagePx=$lineBottomInPagePx boxClipPx=$boxClipPx " +
            "contentOverflowPx=$contentOverflowPx pageClipOverflowPx=$pageClipOverflowPx " +
            "contentBottomInsetPx=${contentBottomInsetPx ?: "unknown"} " +
            "pageClipBottomInsetPx=${pageClipBottomInsetPx ?: "unknown"} " +
            "contentPx=${bounds?.let { "${it.widthPx}x${it.heightPx}" } ?: "unknown"} " +
            "pagePx=${bounds?.let { "${it.pageWidthPx}x${it.pageHeightPx}" } ?: "unknown"} " +
            "lineOffsets=$lastLineStart..$lastLineEnd sourceRange=${block.androidEpubSourceRangeLabel()} " +
            "textChars=${block.content.text.length} expectedHeightPx=${block.expectedHeight} $diagnosticsContext"
    )
    return signature
}

internal fun DrawScope.drawPaginatedHighlightLineStyle(
    layout: TextLayoutResult,
    range: IntRange,
    color: Color,
    style: HighlightStyle
) {
    val start = range.first.coerceIn(0, layout.layoutInput.text.length)
    val endExclusive = (range.last + 1).coerceIn(start, layout.layoutInput.text.length)
    if (endExclusive <= start) return
    val startLine = layout.getLineForOffset(start)
    val endLine = layout.getLineForOffset((endExclusive - 1).coerceAtLeast(start))
    for (line in startLine..endLine) {
        val lineStart = maxOf(start, layout.getLineStart(line))
        val lineEnd = minOf(endExclusive, layout.getLineEnd(line, visibleEnd = true))
        if (lineEnd <= lineStart) continue
        val bounds = layout.getPathForRange(lineStart, lineEnd).getBounds()
        if (bounds.width <= 0f || bounds.height <= 0f) continue
        val y = when (style) {
            HighlightStyle.STRIKETHROUGH -> bounds.top + bounds.height * 0.52f
            else -> bounds.bottom - bounds.height * 0.12f
        }
        if (style == HighlightStyle.WAVY_UNDERLINE) {
            val amplitude = (bounds.height * 0.08f).coerceIn(1.2f, 3.5f)
            val wavelength = (bounds.height * 0.62f).coerceIn(6f, 14f)
            val path = Path()
            var x = bounds.left
            path.moveTo(x, y)
            while (x < bounds.right) {
                val midX = (x + wavelength / 2f).coerceAtMost(bounds.right)
                val nextX = (x + wavelength).coerceAtMost(bounds.right)
                path.quadraticBezierTo(x + wavelength / 4f, y - amplitude, midX, y)
                path.quadraticBezierTo(x + wavelength * 0.75f, y + amplitude, nextX, y)
                x += wavelength
            }
            drawPath(path, color = color.copy(alpha = 0.92f), style = Stroke(width = (bounds.height * 0.06f).coerceIn(1.2f, 3f), cap = StrokeCap.Round))
        } else {
            drawLine(
                color = color.copy(alpha = 0.92f),
                start = Offset(bounds.left, y),
                end = Offset(bounds.right, y),
                strokeWidth = (bounds.height * 0.08f).coerceIn(1.5f, 4f),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun TextWithEmphasis(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle,
    pageIndex: Int,
    @Suppress("unused") textMeasurer: TextMeasurer,
    onLinkClick: (String) -> Unit,
    onGeneralTap: (Offset) -> Unit,
    block: TextContentBlock,
    userHighlights: List<UserHighlight>,
    activeSelection: PaginatedSelection?,
    @Suppress("unused") onSelectionChange: (PaginatedSelection?) -> Unit,
    onHighlightClick: (UserHighlight, Rect) -> Unit,
    isDarkTheme: Boolean,
    themeBackgroundColor: Color,
    themeTextColor: Color,
    pageContentBoundsProvider: (() -> AndroidEpubPageContentBounds?)? = null,
    cutoffDiagnosticsEnabled: Boolean = true,
    cutoffDiagnosticsContext: String = "",
    onRegisterLayout: ((TextLayoutResult, LayoutCoordinates) -> Unit)? = null
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var lastCutoffLogSignature by remember { mutableStateOf<String?>(null) }
    var lastWrapLogSignature by remember { mutableStateOf<String?>(null) }
    val viewConfiguration = LocalViewConfiguration.current
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    var pressedHighlightCfi by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val latestTextLayoutResult = rememberUpdatedState(textLayoutResult)
    val latestOnLinkClick = rememberUpdatedState(onLinkClick)
    val latestOnGeneralTap = rememberUpdatedState(onGeneralTap)
    val displayText = remember(text, isDarkTheme, themeBackgroundColor, themeTextColor, style.color) {
        text.withReaderLinkDisplayStyle(
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = style.color.takeIf { it.isSpecified } ?: themeTextColor
        )
    }
    // Foreground colors are paint-only. Removing them from the layout input
    // keeps contextual OpenType shaping continuous across color-only spans.
    // A second text input restores colors through native character-level paint
    // spans, which can color an attached mark without recoloring its base.
    val shapingDisplayText = remember(displayText) {
        displayText.withoutForegroundColorSpans()
    }
    val paintOnlyColorOverlayText = remember(displayText, style.color) {
        displayText.paintOnlyColorOverlayText(
            baseColor = style.color.takeIf { it.isSpecified } ?: Color.Unspecified
        )
    }

    data class EmphasisMarkInfo(val center: Offset, val radius: Float, val color: Color)
    data class HighlightDrawInfo(val path: Path, val color: Color, val style: HighlightStyle, val range: IntRange)
    data class UnderlineDrawInfo(val path: Path?, val effect: PathEffect?, val minX: Float, val maxX: Float, val y: Float, val decoStyle: String, val decoColor: Color)

    // --- CACHING DECORATIONS FOR PERFORMANCE ---
    val cachedHighlights = remember(block, userHighlights, textLayoutResult, pressedHighlightCfi) {
        val startTime = System.currentTimeMillis()
        val paths = mutableListOf<HighlightDrawInfo>()
        val layout = textLayoutResult
        if (layout != null && userHighlights.isNotEmpty()) {
            userHighlights.forEach { highlight ->
                val range = getHighlightOffsetsInBlock(block, highlight)
                if (range != null) {
                    try {
                        val blockStartAbs = getTextBlockCharOffset(block)
                        val blockEndAbs = block.endCharOffsetInSource
                            .takeIf { it > blockStartAbs }
                            ?: (blockStartAbs + block.content.text.length)
                        Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                            "draw_highlight page=$pageIndex blockCfi=${block.cfi} " +
                                "blockIndex=${block.blockIndex} blockAbs=$blockStartAbs..$blockEndAbs " +
                                "highlightId=${highlight.id} highlightChapter=${highlight.chapterIndex} " +
                                "highlightCfi=${highlight.cfi} range=$range " +
                                "blockText='${highlightDiagSnippet(block.content.text)}'"
                        )
                        Timber.tag(TAG_ANDROID_HIGHLIGHT_RENDER_DIAG).d(
                            "draw_highlight surface=native_or_paginated page=$pageIndex blockIndex=${block.blockIndex} " +
                                "blockCfi=${block.cfi} blockAbs=$blockStartAbs..$blockEndAbs range=$range " +
                                "blockText='${highlightDiagSnippet(block.content.text)}' " +
                                highlight.androidHighlightRenderLabel()
                        )
                        val path = layout.getPathForRange(range.first, range.last + 1)
                        paths.add(HighlightDrawInfo(path, highlight.renderColor(legacyAlpha = 0.4f), highlight.style, range))
                        if (highlight.cfi == pressedHighlightCfi) {
                            paths.add(HighlightDrawInfo(path, Color.Black.copy(alpha = 0.1f), HighlightStyle.BACKGROUND, range))
                        }
                    } catch (e: Exception) {
                        Timber.tag("DecorationsDiag").e(e, "Highlight path out of bounds")
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated highlight paths for block ${block.blockIndex} in ${duration}ms")
        }
        paths
    }

    val cachedEmphasisMarks = remember(textLayoutResult, text, style.color, density) {
        val startTime = System.currentTimeMillis()
        val marks = mutableListOf<EmphasisMarkInfo>()
        val layout = textLayoutResult
        if (layout != null) {
            val emphasisAnnotations = text.getStringAnnotations("TextEmphasis", 0, text.length)
            if (emphasisAnnotations.isNotEmpty()) {
                with(density) { // Provides the scope for .toPx()
                    emphasisAnnotations.forEach { annotation ->
                        val emphasis = parseEmphasisAnnotation(annotation.item, style.color)
                        val markColor = if (emphasis.color.isSpecified) emphasis.color else style.color
                        val markSize = layout.layoutInput.style.fontSize.toPx() * 0.3f
                        for (offset in annotation.start until annotation.end) {
                            if (offset >= text.text.length || text.text[offset].isWhitespace()) continue
                            try {
                                val boundingBox = layout.getBoundingBox(offset)
                                val center = Offset(
                                    boundingBox.center.x,
                                    if (emphasis.position == "under") boundingBox.bottom + markSize * 0.1f
                                    else boundingBox.top - markSize * 0.1f
                                )
                                marks.add(EmphasisMarkInfo(center, markSize / 2, markColor))
                            } catch (e: Exception) {
                                Timber.tag("DecorationsDiag").e(e, "Emphasis mark out of bounds")
                            }
                        }
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated emphasis marks for block ${block.blockIndex} in ${duration}ms")
        }
        marks
    }

    val cachedUnderlines = remember(textLayoutResult, text, style.color, density) {
        val startTime = System.currentTimeMillis()
        val lines = mutableListOf<UnderlineDrawInfo>()
        val layout = textLayoutResult
        if (layout != null) {
            val customUnderlines = text.getStringAnnotations("CustomUnderline", 0, text.length)
            if (customUnderlines.isNotEmpty()) {
                val maxIdx = maxOf(0, text.length - 1)
                val groupedUnderlines = customUnderlines.groupBy { it.item }
                val mergedUnderlines = mutableListOf<AnnotatedString.Range<String>>()

                groupedUnderlines.forEach { (item, annotations) ->
                    val sorted = annotations.sortedBy { it.start }
                    var currentStart = -1
                    var currentEnd = -1

                    for (ann in sorted) {
                        if (currentStart == -1) {
                            currentStart = ann.start
                            currentEnd = ann.end
                        } else if (ann.start <= currentEnd) {
                            currentEnd = maxOf(currentEnd, ann.end)
                        } else {
                            mergedUnderlines.add(AnnotatedString.Range(item, currentStart, currentEnd))
                            currentStart = ann.start
                            currentEnd = ann.end
                        }
                    }
                    if (currentStart != -1) {
                        mergedUnderlines.add(AnnotatedString.Range(item, currentStart, currentEnd))
                    }
                }

                with(density) {
                    mergedUnderlines.forEach { annotation ->
                        val parts = annotation.item.split('|')
                        val decoStyle = parts.getOrNull(0) ?: "solid"
                        val colorStr = parts.getOrNull(1) ?: "Unspecified"
                        val decoColor = if (colorStr != "Unspecified") Color(colorStr.toULong()) else style.color

                        val safeStart = annotation.start.coerceIn(0, text.length)
                        val safeEnd = annotation.end.coerceIn(0, text.length)
                        if (safeStart < safeEnd) {
                            val startLine = layout.getLineForOffset(safeStart.coerceIn(0, maxIdx))
                            val endLine = layout.getLineForOffset((safeEnd - 1).coerceIn(0, maxIdx))

                            for (line in startLine..endLine) {
                                val lineStart = layout.getLineStart(line)
                                val lineEnd = layout.getLineEnd(line, visibleEnd = true)

                                val intersectionStart = maxOf(safeStart, lineStart)
                                val intersectionEnd = minOf(safeEnd, lineEnd)

                                var actualStart = intersectionStart
                                while (actualStart < intersectionEnd && text[actualStart].isWhitespace()) {
                                    actualStart++
                                }

                                var actualEnd = intersectionEnd
                                while (actualEnd > actualStart && text[actualEnd - 1].isWhitespace()) {
                                    actualEnd--
                                }

                                if (actualStart < actualEnd) {
                                    var minX = Float.POSITIVE_INFINITY
                                    var maxX = Float.NEGATIVE_INFINITY
                                    for (i in actualStart until actualEnd) {
                                        try {
                                            val box = layout.getBoundingBox(i)
                                            minX = minOf(minX, box.left, box.right)
                                            maxX = maxOf(maxX, box.left, box.right)
                                        } catch (e: Exception) {
                                            Timber.tag("DecorationsDiag").e(e, "Underline box out of bounds")
                                        }
                                    }

                                    if (minX < maxX && !minX.isInfinite() && !maxX.isInfinite()) {
                                        val baseline = layout.getLineBaseline(line)
                                        val defaultOffset = layout.layoutInput.style.fontSize.toPx() * 0.1f
                                        val requestedOffset = parts.getOrNull(2)?.toFloatOrNull()?.dp?.toPx()
                                        val y = baseline + (requestedOffset ?: defaultOffset)

                                        var underlinePath: Path? = null
                                        var effect: PathEffect? = null

                                        when (decoStyle) {
                                            "wavy" -> {
                                                underlinePath = Path()
                                                underlinePath.moveTo(minX, y)
                                                val waveLength = 4.dp.toPx()
                                                val amplitude = 1.dp.toPx()
                                                var currentX = minX
                                                var isUp = true

                                                while (currentX < maxX) {
                                                    val nextX = minOf(currentX + waveLength / 2f, maxX)
                                                    val midX = currentX + (nextX - currentX) / 2f
                                                    val cpY = if (isUp) y - amplitude else y + amplitude
                                                    underlinePath.quadraticTo(midX, cpY, nextX, y)
                                                    currentX = nextX
                                                    isUp = !isUp
                                                }
                                            }
                                            "dashed" -> {
                                                effect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                                            }
                                            "dotted" -> {
                                                effect = PathEffect.dashPathEffect(floatArrayOf(1f, 4.dp.toPx()))
                                            }
                                        }

                                        lines.add(UnderlineDrawInfo(underlinePath, effect, minX, maxX, y, decoStyle, decoColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val duration = System.currentTimeMillis() - startTime
        if (duration > 5) {
            Timber.tag("DecorationsDiag").w("Calculated custom underlines for block ${block.blockIndex} in ${duration}ms")
        }
        lines
    }

    val customDrawer = Modifier.drawBehind {
        val drawStartTime = System.currentTimeMillis()

        textLayoutResult?.let { layoutResult ->
            if (activeSelection != null) {
                val currentBlockAbs = getTextBlockCharOffset(block)
                val isSelectedOnPage = isBlockSelectedOnPage(block, pageIndex, activeSelection)
                val isStart =
                    pageIndex == activeSelection.startPageIndex &&
                        block.blockIndex == activeSelection.startBlockIndex &&
                        currentBlockAbs == activeSelection.startBlockCharOffset
                val isEnd =
                    pageIndex == activeSelection.endPageIndex &&
                        block.blockIndex == activeSelection.endBlockIndex &&
                        currentBlockAbs == activeSelection.endBlockCharOffset

                if (isSelectedOnPage) {
                    val sOffset = if (isStart) activeSelection.startOffset else 0
                    val eOffset = if (isEnd) activeSelection.endOffset else layoutResult.layoutInput.text.length

                    if (sOffset < eOffset) {
                        try {
                            val path = layoutResult.getPathForRange(sOffset, eOffset)
                            drawPath(path, Color(0xFF1976D2).copy(alpha = 0.3f))
                        } catch (e: Exception) {
                            Timber.tag("DecorationsDiag").e(e, "Highlight path out of bounds")
                        }
                    }
                }
            }

            val layout = textLayoutResult
            cachedHighlights.forEach { highlight ->
                when (highlight.style) {
                    HighlightStyle.BACKGROUND -> drawPath(highlight.path, highlight.color, blendMode = BlendMode.SrcOver)
                    HighlightStyle.UNDERLINE,
                    HighlightStyle.WAVY_UNDERLINE,
                    HighlightStyle.STRIKETHROUGH -> if (layout != null) {
                        drawPaginatedHighlightLineStyle(layout, highlight.range, highlight.color, highlight.style)
                    }
                }
            }

            cachedEmphasisMarks.forEach { mark ->
                drawCircle(mark.color, mark.radius, mark.center, style = Stroke(1f))
            }

            cachedUnderlines.forEach { line ->
                when (line.decoStyle) {
                    "wavy" -> {
                        line.path?.let { p ->
                            drawPath(p, color = line.decoColor, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    "dashed", "dotted" -> {
                        drawLine(
                            color = line.decoColor,
                            start = Offset(line.minX, line.y),
                            end = Offset(line.maxX, line.y),
                            strokeWidth = if (line.decoStyle == "dotted") 2.dp.toPx() else 1.dp.toPx(),
                            cap = if (line.decoStyle == "dotted") StrokeCap.Round else StrokeCap.Butt,
                            pathEffect = line.effect
                        )
                    }
                    else -> { // Solid or Double
                        drawLine(
                            color = line.decoColor,
                            start = Offset(line.minX, line.y),
                            end = Offset(line.maxX, line.y),
                            strokeWidth = 1.dp.toPx()
                        )
                        if (line.decoStyle == "double") {
                            drawLine(
                                color = line.decoColor,
                                start = Offset(line.minX, line.y + 2.dp.toPx()),
                                end = Offset(line.maxX, line.y + 2.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
        val drawDuration = System.currentTimeMillis() - drawStartTime
        if (drawDuration > 5) {
            Timber.tag("DecorationsDiag").w("Modifier.drawBehind took ${drawDuration}ms for block ${block.blockIndex}")
        }
    }

    fun getHighlightAt(offset: Offset, layout: TextLayoutResult): Pair<UserHighlight, Rect>? {
        if (block.cfi == null) return null

        // Optimization: Quick bounds check
        val charOffset = layout.getOffsetForPosition(offset)
        val lineIndex = layout.getLineForOffset(charOffset)
        val lineLeft = layout.getLineLeft(lineIndex)
        val lineRight = layout.getLineRight(lineIndex)
        if (offset.x < minOf(lineLeft, lineRight) - 50 || offset.x > maxOf(
                lineLeft, lineRight
            ) + 50
        ) {
            return null
        }

        // Iterate highlights reversed (topmost first)
        for (highlight in userHighlights.reversed()) {
            val range = getHighlightOffsetsInBlock(block, highlight) ?: continue

            if (charOffset in range) {
                val blockStartAbs = getTextBlockCharOffset(block)
                Timber.tag(TAG_PAGINATED_HIGHLIGHT_DIAG).d(
                    "tap_highlight page=$pageIndex blockCfi=${block.cfi} " +
                        "blockIndex=${block.blockIndex} blockAbsStart=$blockStartAbs " +
                        "charOffset=$charOffset absoluteCharOffset=${blockStartAbs + charOffset} " +
                        "highlightId=${highlight.id} highlightCfi=${highlight.cfi} range=$range"
                )
                val path = layout.getPathForRange(range.first, range.last)
                val bounds = path.getBounds()
                return highlight to bounds
            }
        }
        return null
    }

    fun logCutoffIfNeeded(
        layout: TextLayoutResult?,
        coordinates: LayoutCoordinates?,
        pageContentBounds: AndroidEpubPageContentBounds? = pageContentBoundsProvider?.invoke()
    ) {
        if (!cutoffDiagnosticsEnabled) return
        if (layout == null || coordinates == null || !coordinates.isAttached) return
        lastCutoffLogSignature = logAndroidEpubTextCutoffIfNeeded(
            pageIndex = pageIndex,
            block = block,
            layout = layout,
            coordinates = coordinates,
            pageContentBounds = pageContentBounds,
            diagnosticsContext = cutoffDiagnosticsContext,
            previousSignature = lastCutoffLogSignature
        )
        lastWrapLogSignature = logAndroidEpubTextWrapIfNeeded(
            pageIndex = pageIndex,
            block = block,
            layout = layout,
            coordinates = coordinates,
            pageContentBounds = pageContentBounds,
            diagnosticsContext = cutoffDiagnosticsContext,
            previousSignature = lastWrapLogSignature
        )
    }

    val currentPageContentBounds = pageContentBoundsProvider?.invoke()
    LaunchedEffect(textLayoutResult, layoutCoordinates, currentPageContentBounds) {
        logCutoffIfNeeded(textLayoutResult, layoutCoordinates, currentPageContentBounds)
    }

    Box(modifier = modifier) {
        Text(text = shapingDisplayText, style = style, modifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned {
            layoutCoordinates = it
            logCutoffIfNeeded(textLayoutResult, it)
            if (textLayoutResult != null && block.cfi != null) {
                onRegisterLayout?.invoke(textLayoutResult!!, it)
            }
        }
        .then(customDrawer)
        .pointerInput(displayText, viewConfiguration.touchSlop) {
            awaitEachGesture {
                awaitReaderLinkTap(
                    source = "TextWithEmphasis:block=${block.blockIndex}",
                    urlAtPosition = { offset ->
                        latestTextLayoutResult.value?.let { layout ->
                            displayText.readerUrlAnnotationAtPosition(layout, offset)
                        }
                    },
                    touchSlop = viewConfiguration.touchSlop,
                    onLinkClick = { latestOnLinkClick.value(it) }
                )
            }
        }
        .pointerInput(userHighlights, displayText) {
            detectTapGestures(
                onLongPress = { offset ->
                    latestTextLayoutResult.value?.let { layout ->
                        val charOffset = layout.getOffsetForPosition(offset)
                        val wordBoundary = layout.getWordBoundary(charOffset)

                        var start = wordBoundary.start
                        var end = wordBoundary.end

                        val textStr = text.text
                        while (start < end && start < textStr.length && !textStr[start].isLetterOrDigit()) start++
                        while (end > start && end <= textStr.length && !textStr[end - 1].isLetterOrDigit()) end--

                        if (start < end && block.cfi != null) {
                            layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val maxIdx = maxOf(0, textStr.length - 1)
                                    val startBox = layout.getBoundingBox(start.coerceIn(0, maxIdx))
                                    val endBox = layout.getBoundingBox((end - 1).coerceIn(0, maxIdx))

                                    val topLeftWin = coords.localToWindow(startBox.topLeft)
                                    val bottomRightWin = coords.localToWindow(endBox.bottomRight)

                                    val selText = textStr.substring(start, end)

                                    val startBlockAbs = when (block) {
                                        is ParagraphBlock -> block.startCharOffsetInSource
                                        is HeaderBlock -> block.startCharOffsetInSource
                                        is QuoteBlock -> block.startCharOffsetInSource
                                        is ListItemBlock -> block.startCharOffsetInSource
                                    }

                                    onSelectionChange(
                                        PaginatedSelection(
                                            startBlockIndex = block.blockIndex,
                                            endBlockIndex = block.blockIndex,
                                            startBaseCfi = block.cfi!!,
                                            endBaseCfi = block.cfi!!,
                                            startOffset = start,
                                            endOffset = end,
                                            text = selText,
                                            rect = Rect(topLeftWin, bottomRightWin),
                                            startPageIndex = pageIndex,
                                            endPageIndex = pageIndex,
                                            startBlockCharOffset = startBlockAbs,
                                            endBlockCharOffset = startBlockAbs,
                                            textPerBlock = mapOf(
                                                buildSelectionBlockKey(
                                                    pageIndex = pageIndex,
                                                    blockIndex = block.blockIndex,
                                                    blockCharOffset = startBlockAbs
                                                ) to selText
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                onTap = { offset ->
                    latestTextLayoutResult.value?.let { layout ->
                        val hit = getHighlightAt(offset, layout)
                        if (hit != null) {
                            val (highlight, localRect) = hit
                            val globalRect = layoutCoordinates?.let { coords ->
                                if (coords.isAttached) {
                                    val topLeft = coords.localToWindow(localRect.topLeft)
                                    val bottomRight = coords.localToWindow(localRect.bottomRight)
                                    Rect(topLeft, bottomRight)
                                } else null
                            } ?: localRect
                            onHighlightClick(highlight, globalRect)
                            return@detectTapGestures
                        }

                        val charOffset = layout.getOffsetForPosition(offset)
                        val url = displayText.readerUrlAnnotationAtPosition(layout, offset)
                        if (url != null) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "detect_tap_link source=TextWithEmphasis:block=${block.blockIndex} " +
                                    "page=$pageIndex charOffset=$charOffset href=${url.readerLinkDiagPreview()}"
                            )
                            latestOnLinkClick.value(url)
                        } else {
                            latestOnGeneralTap.value(offset)
                        }
                    }
                }
            )
        }, onTextLayout = {
        textLayoutResult = it
        if (READER_LINK_DIAGNOSTICS_ENABLED && displayText.getStringAnnotations("URL", 0, displayText.length).isNotEmpty()) {
            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                "layout_text source=TextWithEmphasis page=$pageIndex block=${block.blockIndex} " +
                    "size=${it.size.width}x${it.size.height} lines=${it.lineCount} " +
                    displayText.readerAnnotatedLinkDiagSummary()
            )
        }
        if (layoutCoordinates != null && block.cfi != null) {
            onRegisterLayout?.invoke(it, layoutCoordinates!!)
        }
        logCutoffIfNeeded(it, layoutCoordinates)
        })
        if (paintOnlyColorOverlayText.isNotEmpty()) {
            Text(
                text = paintOnlyColorOverlayText,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {},
                style = style.copy(color = Color.Transparent)
            )
        }
    }
}

@SuppressLint("BinaryOperationInTimber")
internal fun checkLayoutMismatch(
    blockIndex: Int,
    blockType: String,
    expectedHeight: Int,
    actualHeight: Int,
    textSnippet: String,
    diagnostics: String = "",
    @Suppress("SameParameterValue") tolerance: Int = 2
) {
    if (expectedHeight == 0) {
        Timber.tag("PAGINATION_MISMATCH").w(
            "Block #$blockIndex ($blockType) has expectedHeight=0. Skipping check. Text: '$textSnippet'" +
                    if (diagnostics.isNotBlank()) "\n -> Diagnostics: $diagnostics" else ""
        )
        return
    }

    if (actualHeight > expectedHeight + tolerance) {
        val diff = actualHeight - expectedHeight
        Timber.tag("PAGINATION_MISMATCH").e(
            "OVERFLOW DETECTED! Block #$blockIndex ($blockType)\n" +
                    " -> Expected: ${expectedHeight}px\n" +
                    " -> Actual:   ${actualHeight}px\n" +
                    " -> Diff:     +${diff}px\n" +
                    " -> Content:  '$textSnippet'" +
                    if (diagnostics.isNotBlank()) "\n -> Diagnostics: $diagnostics" else ""
        )
    }
}
