// PaginatedReader.kt
@file:Suppress("VariableNeverRead")

package com.aryan.reader.paginatedreader

import android.os.Build
import androidx.compose.ui.unit.isSpecified
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.aryan.reader.loadReaderTextureBitmap
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.plainTextCharacterCount
import com.aryan.reader.epubreader.ReaderTextAlign
import com.aryan.reader.epubreader.TtsHighlightInfo
import com.aryan.reader.epubreader.UserHighlight
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderLocator as SharedReaderLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalSerializationApi::class, FlowPreview::class)
@Composable
fun PaginatedReaderScreen(
    modifier: Modifier = Modifier,
    book: EpubBook,
    bookId: String? = null,
    isDarkTheme: Boolean,
    effectiveBg: Color,
    effectiveText: Color,
    pagerState: PagerState,
    isPageTurnAnimationEnabled: Boolean,
    isRightToLeftPagination: Boolean = false,
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
    initialChapterIndexInBook: Int?,
    fallbackLocatorForReconfiguration: Locator? = null,
    explicitNavigationAnchor: Locator? = null,
    explicitNavigationEpoch: Long = 0L,
    isExternalNavigationInProgress: Boolean = false,
    onReconfigurationAnchorCaptured: (Locator) -> Unit = {},
    onReconfigurationRestoreActiveChanged: (Boolean) -> Unit = {},
    onPaginatorReady: (IPaginator) -> Unit,
    onTap: (Offset?) -> Unit,
    isProUser: Boolean,
    isOss: Boolean = false,
    onShowDictionaryUpsellDialog: () -> Unit,
    onWordSelectedForAiDefinition: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStartTtsFromSelection: (String, Int) -> Unit,
    onNoteRequested: (String?) -> Unit,
    onFootnoteRequested: (String) -> Unit,
    onInternalLinkNavigated: (Int, Locator?) -> Unit = { _, _ -> },
    userHighlights: List<UserHighlight>,
    onHighlightCreated: (String, String, String, SharedReaderLocator, HighlightStyle) -> Unit,
    onHighlightDeleted: (String) -> Unit,
    activeHighlightPalette: List<Int>,
    onUpdatePalette: (Int, Int) -> Unit,
    activeTextureId: String? = null,
    activeTextureAlpha: Float = 0.55f
) {
    LaunchedEffect(userHighlights) {
        Timber.d("PaginatedReaderScreen: Received ${userHighlights.size} highlights.")
        userHighlights.forEach {
            Timber.d(" -> Received Highlight: CFI=${it.cfi}, Text='${it.text.take(20)}...'")
        }
    }

    val context = LocalContext.current
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
    } else Modifier

    var isNavigatingByLink by remember { mutableStateOf(false) }
    var localExplicitNavigationAnchor by remember { mutableStateOf<Locator?>(null) }
    var localExplicitNavigationEpoch by remember { mutableLongStateOf(0L) }
    val latestExternalNavigationAnchor by rememberUpdatedState(explicitNavigationAnchor)
    val latestExternalNavigationEpoch by rememberUpdatedState(explicitNavigationEpoch)
    val latestIsExternalNavigationInProgress by rememberUpdatedState(isExternalNavigationInProgress)
    val bookReplacementSignature = remember(bookReplacementPreferences, bookReplacementFileId) {
        bookReplacementPreferences.signatureForFile(bookReplacementFileId)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(effectiveBg)) {
        val textMeasurer = rememberTextMeasurer()
        val baseTextStyle = MaterialTheme.typography.bodyLarge

        var debouncedFontSizeMult by remember { mutableFloatStateOf(fontSizeMultiplier) }
        var debouncedLineHeightMult by remember { mutableFloatStateOf(lineHeightMultiplier) }
        var debouncedParagraphGapMult by remember { mutableFloatStateOf(paragraphGapMultiplier) }
        var debouncedImageSizeMult by remember { mutableFloatStateOf(imageSizeMultiplier) }
        var debouncedHideImages by remember { mutableStateOf(hideImages) }
        var debouncedHorizontalMarginMult by remember { mutableFloatStateOf(horizontalMarginMultiplier) }
        var debouncedVerticalMarginMult by remember { mutableFloatStateOf(verticalMarginMultiplier) }
        var debouncedFontFamily by remember { mutableStateOf(fontFamily) }
        var debouncedFontWeight by remember { mutableIntStateOf(fontWeight) }
        var debouncedLetterSpacing by remember { mutableFloatStateOf(letterSpacing) }
        var debouncedTextAlign by remember { mutableStateOf(textAlign) }
        var debouncedBookReplacementSignature by remember { mutableStateOf(bookReplacementSignature) }
        var debouncedBookReplacementPreferences by remember { mutableStateOf(bookReplacementPreferences) }
        var debouncedBookReplacementFileId by remember { mutableStateOf(bookReplacementFileId) }

        var anchorLocatorForReconfig by remember { mutableStateOf<Locator?>(null) }
        val currentPaginatorRef = remember { mutableStateOf<IPaginator?>(null) }
        val latestFallbackLocatorForReconfiguration by rememberUpdatedState(fallbackLocatorForReconfiguration)

        var previousConstraints by remember {
            mutableStateOf(this.constraints)
        }

        if (previousConstraints != this.constraints) {
            val activePaginator = currentPaginatorRef.value
            val currentPage = pagerState.currentPage
            val locator = resolvePaginatedReconfigurationAnchor(
                currentPageLocator = (activePaginator as? BookPaginator)?.getLocatorForPage(currentPage),
                fallbackLocator = fallbackLocatorForReconfiguration
            )
            anchorLocatorForReconfig = locator

            Timber.tag("ThemeReconfig").d("""
            RECONFIG DETECTED
            - Reason: Constraints
            - Current Page: $currentPage
            - Saved Locator: $locator
        """.trimIndent())
            previousConstraints = this.constraints
        }

        val layoutTextStyle = remember(
            baseTextStyle,
            debouncedFontSizeMult,
            debouncedLineHeightMult,
            debouncedFontFamily,
            debouncedFontWeight,
            debouncedLetterSpacing
        ) {
            val adjustedFontSize = baseTextStyle.fontSize * debouncedFontSizeMult
            val adjustedLineHeight = adjustedFontSize * paginationLineHeightMultiplierForWebViewSetting(debouncedLineHeightMult)

            baseTextStyle.copy(
                color = Color.Unspecified,
                fontSize = adjustedFontSize,
                lineHeight = adjustedLineHeight,
                fontFamily = debouncedFontFamily,
                fontWeight = debouncedFontWeight.takeIf { it > 0 }?.let(::FontWeight),
                lineBreak = LineBreak.Simple,
                letterSpacing = debouncedLetterSpacing.em,
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

        if (DEBUG_PAGE_TURN_DIAG) {
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    Timber.tag("PageTurnDiag").i("Pager Settled: Now on page $page at ${System.currentTimeMillis()}")
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
                    Timber.tag("PageTurnDiag").d("Pager Scroll State: isScrolling=$isScrolling")
                }
            }
        }

        LaunchedEffect(fontSizeMultiplier, lineHeightMultiplier, paragraphGapMultiplier, imageSizeMultiplier, hideImages, horizontalMarginMultiplier, verticalMarginMultiplier, fontFamily, fontWeight, letterSpacing, textAlign, bookReplacementSignature, bookReplacementFileId) {
            if (fontSizeMultiplier != debouncedFontSizeMult ||
                lineHeightMultiplier != debouncedLineHeightMult ||
                paragraphGapMultiplier != debouncedParagraphGapMult ||
                imageSizeMultiplier != debouncedImageSizeMult ||
                hideImages != debouncedHideImages ||
                horizontalMarginMultiplier != debouncedHorizontalMarginMult ||
                verticalMarginMultiplier != debouncedVerticalMarginMult ||
                fontFamily != debouncedFontFamily ||
                fontWeight != debouncedFontWeight ||
                letterSpacing != debouncedLetterSpacing ||
                textAlign != debouncedTextAlign ||
                bookReplacementSignature != debouncedBookReplacementSignature ||
                bookReplacementFileId != debouncedBookReplacementFileId
            ) {
                Timber.d("Formatting changed. Waiting for debounce.")
                delay(400L)

                val activePaginator = currentPaginatorRef.value
                val currentPage = pagerState.currentPage
                val locator = resolvePaginatedReconfigurationAnchor(
                    currentPageLocator = (activePaginator as? BookPaginator)?.getLocatorForPage(currentPage),
                    fallbackLocator = fallbackLocatorForReconfiguration
                )
                if (locator != null) {
                    anchorLocatorForReconfig = locator
                }

                debouncedFontSizeMult = fontSizeMultiplier
                debouncedLineHeightMult = lineHeightMultiplier
                debouncedParagraphGapMult = paragraphGapMultiplier
                debouncedImageSizeMult = imageSizeMultiplier
                debouncedHideImages = hideImages
                debouncedHorizontalMarginMult = horizontalMarginMultiplier
                debouncedVerticalMarginMult = verticalMarginMultiplier
                debouncedFontFamily = fontFamily
                debouncedFontWeight = fontWeight
                debouncedLetterSpacing = letterSpacing
                debouncedTextAlign = textAlign
                debouncedBookReplacementSignature = bookReplacementSignature
                debouncedBookReplacementPreferences = bookReplacementPreferences
                debouncedBookReplacementFileId = bookReplacementFileId
                Timber.d("Debounce complete. Applying new format settings.")
            }
        }

        val userTextAlign = remember(debouncedTextAlign) {
            when (debouncedTextAlign) {
                ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                ReaderTextAlign.LEFT -> TextAlign.Left
                ReaderTextAlign.RIGHT -> TextAlign.Right
                ReaderTextAlign.DEFAULT -> null
            }
        }

        val density = LocalDensity.current
        val requestedHorizontalPadding = 16.dp * debouncedHorizontalMarginMult
        val requestedVerticalPadding = 16.dp * debouncedVerticalMarginMult
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
                val finalConstraints = this.constraints.copy(
                    minWidth = 0,
                    maxWidth = (this.constraints.maxWidth - (2 * horizontalPaddingPx)).coerceAtLeast(1),
                    minHeight = 0,
                    maxHeight = (this.constraints.maxHeight - (2 * verticalPaddingPx)).coerceAtLeast(1)
                )
                finalConstraints
            }

        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val mathMLRenderer = remember { MathMLRenderer(context.applicationContext) }

        DisposableEffect(Unit) {
            onDispose {
                mathMLRenderer.destroy()
                Timber.d("PaginatedReaderScreen disposed, MathMLRenderer destroyed.")
            }
        }

        val effectiveInitialChapter =
            remember(initialChapterIndexInBook, anchorLocatorForReconfig) {
                anchorLocatorForReconfig?.chapterIndex ?: initialChapterIndexInBook ?: 0
            }

        LaunchedEffect(anchorLocatorForReconfig) {
            anchorLocatorForReconfig?.let { locator ->
                onReconfigurationAnchorCaptured(locator)
                onReconfigurationRestoreActiveChanged(true)
            }
        }

        val paginator = remember(book, bookId, textConstraints, layoutTextStyle, userTextAlign, debouncedLineHeightMult, debouncedParagraphGapMult, debouncedImageSizeMult, debouncedHideImages, debouncedVerticalMarginMult, debouncedBookReplacementSignature, debouncedBookReplacementFileId) {
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
                fontFaces = allFontFaces, extractionPath = book.extractionBasePath
            )
            val bookCacheDao =
                BookCacheDatabase.getDatabase(context.applicationContext).bookCacheDao()
            val proto = ProtoBuf { serializersModule = semanticBlockModule }

            val uniqueBookId = bookId ?: if (book.fileName.length > 20) book.fileName else book.title

            Timber.d("Recreating BookPaginator for ID: $uniqueBookId. TextAlign: $userTextAlign")
            Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: Instantiating BookPaginator. book.chaptersForPagination.size=${book.chaptersForPagination.size}, initialChapter=$effectiveInitialChapter")

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
                initialChapterToPaginate = effectiveInitialChapter,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = userTextAlign,
                paragraphGapMultiplier = debouncedParagraphGapMult,
                userLineHeightMultiplier = debouncedLineHeightMult,
                imageSizeMultiplier = debouncedImageSizeMult,
                hideImages = debouncedHideImages,
                verticalMarginMultiplier = debouncedVerticalMarginMult,
                bookReplacementPreferences = debouncedBookReplacementPreferences,
                bookReplacementFileId = debouncedBookReplacementFileId
            )
        }

        LaunchedEffect(paginator) {
            onPaginatorReady(paginator)
            currentPaginatorRef.value = paginator
        }

        DisposableEffect(paginator) {
            onDispose {
                if (currentPaginatorRef.value === paginator) {
                    currentPaginatorRef.value = null
                }
                paginator.dispose()
            }
        }

        LaunchedEffect(paginator) {
            if (anchorLocatorForReconfig != null) {
                Timber.tag("POS_DIAG").d("Restoration Triggered. Anchor Locator: $anchorLocatorForReconfig")

                try {
                    onReconfigurationRestoreActiveChanged(true)
                    snapshotFlow { paginator.isLoading }.filter { !it }.first()

                    val targetLocator = anchorLocatorForReconfig
                    if (targetLocator != null) {
                        val page = paginator.findPageForLocator(targetLocator)

                        Timber.tag("POS_DIAG").d("Restoration Result: Paginator resolved locator to page: $page")

                        if (page != null) {
                            pagerState.scrollToPage(page)
                            paginator.onUserScrolledTo(page)
                            Timber.tag("POS_DIAG").i("Restoration: Pager scrolled to $page")
                        } else {
                            val startPage = paginator.chapterStartPageIndices[targetLocator.chapterIndex]
                            if (startPage != null) {
                                Timber.tag("POS_DIAG").w("Restoration: Precise page not found, falling back to chapter start: $startPage")
                                pagerState.scrollToPage(startPage)
                                paginator.onUserScrolledTo(startPage)
                            }
                        }
                        anchorLocatorForReconfig = null
                    }
                } finally {
                    onReconfigurationRestoreActiveChanged(false)
                }
            }
        }

        var isLoading by remember { mutableStateOf(true) }
        var totalPageCount by remember { mutableIntStateOf(0) }
        var generation by remember { mutableIntStateOf(0) }

        LaunchedEffect(paginator) {
            launch { snapshotFlow { paginator.isLoading }.collect {
                Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.isLoading=$it")
                isLoading = it
            } }
            launch {
                snapshotFlow { paginator.totalPageCount }.collect { newTotalPageCount ->
                    Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.totalPageCount=$newTotalPageCount")
                    totalPageCount = newTotalPageCount
                }
            }
            launch { snapshotFlow { paginator.generation }.collect {
                Timber.tag("ReflowPaginationDiag").d("PaginatedReaderScreen: paginator.generation=$it")
                generation = it
            } }
        }

        LaunchedEffect(pagerState, paginator) {
            snapshotFlow { pagerState.currentPage }.debounce(500)
                .collectLatest { page ->
                    if (anchorLocatorForReconfig == null) {
                        paginator.onUserScrolledTo(page)
                    }
                }
        }

        LaunchedEffect(paginator, pagerState) {
            paginator.pageShiftRequest.collect { shiftAmount ->
                if (pagerState.pageCount <= 0) {
                    Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG)
                        .w("shift_drop reason=emptyPager shift=$shiftAmount")
                    return@collect
                }

                val bookPaginator = paginator as? BookPaginator
                val currentPageBeforeShift = pagerState.currentPage
                val now = System.currentTimeMillis()
                val externalAgeMs = if (latestExternalNavigationEpoch > 0L) {
                    now - latestExternalNavigationEpoch
                } else {
                    -1L
                }
                val localAgeMs = if (localExplicitNavigationEpoch > 0L) {
                    now - localExplicitNavigationEpoch
                } else {
                    -1L
                }
                val recentExternalNavigation =
                    externalAgeMs in 0L..EXPLICIT_NAVIGATION_SHIFT_ANCHOR_WINDOW_MS
                val recentLocalNavigation =
                    localAgeMs in 0L..EXPLICIT_NAVIGATION_SHIFT_ANCHOR_WINDOW_MS
                val activeExplicitAnchor = when {
                    latestIsExternalNavigationInProgress -> latestExternalNavigationAnchor
                    isNavigatingByLink -> localExplicitNavigationAnchor
                    else -> null
                }
                val recentExplicitAnchor = when {
                    recentExternalNavigation -> latestExternalNavigationAnchor
                    recentLocalNavigation -> localExplicitNavigationAnchor
                    else -> null
                }
                val activeExplicitAnchorSource = when {
                    activeExplicitAnchor == null -> null
                    latestIsExternalNavigationInProgress -> "explicit_external_active"
                    else -> "explicit_link"
                }
                val recentExplicitAnchorSource = when {
                    recentExplicitAnchor == null -> null
                    recentExternalNavigation -> "explicit_external_recent"
                    else -> "explicit_link_recent"
                }
                val currentPageLocator = bookPaginator?.getLocatorForPage(currentPageBeforeShift)
                val fallbackLocator = latestFallbackLocatorForReconfiguration
                var anchorSource = "none"
                val anchor = when {
                    anchorLocatorForReconfig != null -> {
                        anchorSource = "reconfiguration"
                        anchorLocatorForReconfig
                    }
                    activeExplicitAnchor != null -> {
                        anchorSource = activeExplicitAnchorSource ?: "explicit_active"
                        activeExplicitAnchor
                    }
                    fallbackLocator != null -> {
                        anchorSource = "last_known"
                        fallbackLocator
                    }
                    recentExplicitAnchor != null -> {
                        anchorSource = recentExplicitAnchorSource ?: "explicit_recent"
                        recentExplicitAnchor
                    }
                    currentPageLocator != null -> {
                        anchorSource = "current_page"
                        currentPageLocator
                    }
                    else -> null
                }

                Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG).d(
                    "shift_received shift=$shiftAmount currentPage=$currentPageBeforeShift anchorSource=$anchorSource anchor=$anchor currentLocator=$currentPageLocator fallback=$fallbackLocator externalInProgress=$latestIsExternalNavigationInProgress linkInProgress=$isNavigatingByLink externalAgeMs=$externalAgeMs localAgeMs=$localAgeMs"
                )

                val resolvedPage = anchor?.let { locator ->
                    bookPaginator?.findStablePageForLocator(locator)
                }

                if (resolvedPage != null) {
                    Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG).d(
                        "shift_apply_stable shift=$shiftAmount from=$currentPageBeforeShift to=$resolvedPage anchorSource=$anchorSource anchor=$anchor"
                    )
                    pagerState.scrollToPage(resolvedPage)
                    paginator.onUserScrolledTo(resolvedPage)
                } else {
                    val maxPage = (pagerState.pageCount - 1).coerceAtLeast(0)
                    val newPage = (currentPageBeforeShift + shiftAmount).coerceIn(0, maxPage)
                    Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG).w(
                        "shift_apply_relative shift=$shiftAmount from=$currentPageBeforeShift to=$newPage anchorSource=$anchorSource anchor=$anchor"
                    )
                    pagerState.scrollToPage(newPage)
                    paginator.onUserScrolledTo(newPage)
                }
            }
        }

        val uiState = PaginatedReaderUiState(
            isLoading = isLoading, totalPageCount = totalPageCount, generation = generation
        )

        PaginatedReaderContent(
            uiState = uiState,
            pagerState = pagerState,
            isPageTurnAnimationEnabled = isPageTurnAnimationEnabled,
            isRightToLeftPagination = isRightToLeftPagination,
            effectiveBg = effectiveBg,
            searchQuery = searchQuery,
            ttsHighlightInfo = ttsHighlightInfo,
            textStyle = textStyle,
            imageSizeMultiplier = debouncedImageSizeMult,
            hideImages = debouncedHideImages,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            onGetPage = { pageIndex ->
                val startTime = System.currentTimeMillis()
                val result = paginator.getPageContent(pageIndex)
                val duration = System.currentTimeMillis() - startTime
                if (DEBUG_PAGE_TURN_DIAG && duration > 16) {
                    Timber.tag("PageTurnDiag")
                        .w("HEAVY TASK: paginator.getPageContent($pageIndex) took ${duration}ms on Thread ${Thread.currentThread().name}")
                }
                result
            },
            onGetChapterIndex = { pageIndex -> paginator.findChapterIndexForPage(pageIndex) },
            onGetChapterPath = { pageIndex -> paginator.getChapterPathForPage(pageIndex) },
            onGetChapterInfo = { pageIndex ->
                paginator.findChapterIndexForPage(pageIndex)?.let { chapterIndex ->
                    val chapter = book.chaptersForPagination.getOrNull(chapterIndex)
                    val estimatedPages = paginator.chapterPageCounts[chapterIndex]
                    if (chapter != null) {
                        Pair(chapter.title, estimatedPages)
                    } else {
                        null
                    }
                }
            },
            onInternalLinkNavigated = onInternalLinkNavigated,
            onLinkClick = { currentChapterPath, href, onNavComplete ->
                coroutineScope.launch(Dispatchers.IO) {
                    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                        "nav_request currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                            "href=${href.readerLinkDiagPreview()}"
                    )
                    withContext(Dispatchers.Main) { isNavigatingByLink = true }
                    try {
                        var isFootnote = false
                        var footnoteHtml: String? = null
                        var sourceHtmlForLink = ""

                        val sourceChapter =
                            book.chaptersForPagination.find { it.absPath == currentChapterPath }
                        if (sourceChapter == null) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).w(
                                "nav_source_chapter_miss currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                                    "href=${href.readerLinkDiagPreview()}"
                            )
                        }
                        if (sourceChapter != null) {
                            val sourceHtml = sourceChapter.htmlContent.ifEmpty {
                                try {
                                    File(book.extractionBasePath, sourceChapter.htmlFilePath)
                                        .readText()
                                } catch (_: Exception) {
                                    ""
                                }
                            }
                            if (sourceHtml.isNotEmpty()) {
                                sourceHtmlForLink = sourceHtml
                                val doc = Jsoup.parse(sourceHtml)
                                val safeHref = href.replace("\"", "\\\"")
                                val aTag = doc.select("a[href=\"$safeHref\"]").first()

                                val linkType = aTag?.attr("epub:type").orEmpty()
                                val linkRole = aTag?.attr("role").orEmpty()
                                if (
                                    linkType.contains("noteref", ignoreCase = true) ||
                                    linkRole.contains("doc-noteref", ignoreCase = true)
                                ) {
                                    isFootnote = true
                                }
                            }
                        }

                        run {
                            val decodedHref = try {
                                URLDecoder.decode(href, "UTF-8")
                            } catch (_: Exception) {
                                href
                            }
                            val parts = decodedHref.split('#', limit = 2)
                            val pathPart = parts[0]
                            val anchor = if (parts.size > 1) parts[1] else null

                            if (anchor != null) {
                                val targetPath = if (pathPart.isBlank()) currentChapterPath else {
                                    try {
                                        URI(currentChapterPath).resolve(pathPart)
                                            .normalize().path
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                if (targetPath != null) {
                                    val targetChapter = book.chaptersForPagination.find {
                                        try {
                                            URI(it.absPath).normalize().path == targetPath
                                        } catch (_: Exception) {
                                            false
                                        }
                                    }

                                    if (targetChapter != null) {
                                        val targetHtml = targetChapter.htmlContent.ifEmpty {
                                            try {
                                                File(
                                                    book.extractionBasePath,
                                                    targetChapter.htmlFilePath
                                                ).readText()
                                            } catch (_: Exception) {
                                                ""
                                            }
                                        }
                                        if (targetHtml.isNotEmpty()) {
                                            val doc = Jsoup.parse(targetHtml)
                                            val noteEl = doc.getElementById(anchor)
                                            if (noteEl != null) {
                                                footnoteHtml = resolveEpubNoteHtml(
                                                    sourceHtml = sourceHtmlForLink,
                                                    targetHtml = targetHtml,
                                                    href = href,
                                                    anchor = anchor,
                                                    sourceIsNoteref = isFootnote,
                                                    targetBaseUri = targetChapter.absPath
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!footnoteHtml.isNullOrBlank()) {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "nav_footnote_open href=${href.readerLinkDiagPreview()} htmlChars=${footnoteHtml?.length ?: 0}"
                            )
                            withContext(Dispatchers.Main) { onFootnoteRequested(footnoteHtml) }
                        } else {
                            Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                "nav_resolve_start currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                                    "href=${href.readerLinkDiagPreview()}"
                            )
                            val targetPage = (paginator as? BookPaginator)?.findStablePageForHref(currentChapterPath, href)
                            withContext(Dispatchers.Main) {
                                if (targetPage != null) {
                                    val targetAnchor = (paginator as? BookPaginator)?.getLocatorForPage(targetPage)
                                    val navigationEpoch = System.currentTimeMillis()
                                    localExplicitNavigationAnchor = targetAnchor
                                    localExplicitNavigationEpoch = navigationEpoch
                                    Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG).d(
                                        "link_resolved href=$href targetPage=$targetPage anchor=$targetAnchor epoch=$navigationEpoch"
                                    )
                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).d(
                                        "nav_resolve_success href=${href.readerLinkDiagPreview()} targetPage=$targetPage " +
                                            "targetAnchor=$targetAnchor"
                                    )
                                    paginator.onUserScrolledTo(targetPage)
                                    onNavComplete(targetPage)
                                } else {
                                    Timber.tag(READER_UI_STABLE_PAGE_NAV_TAG).w(
                                        "link_failed href=$href currentChapterPath=$currentChapterPath"
                                    )
                                    Timber.tag(TAG_PAGINATED_LINK_DIAG).w(
                                        "nav_resolve_failed currentChapterPath=${currentChapterPath.readerLinkDiagPreview()} " +
                                            "href=${href.readerLinkDiagPreview()}"
                                    )
                                }
                            }
                        }
                    } finally {
                        withContext(Dispatchers.Main) { isNavigatingByLink = false }
                    }
                }
            },
            onTap = onTap,
            isProUser = isProUser,
            isOss = isOss,
            onShowDictionaryUpsellDialog = onShowDictionaryUpsellDialog,
            onWordSelectedForAiDefinition = onWordSelectedForAiDefinition,
            onTranslate = onTranslate,
            onSearch = onSearch,
            onStartTtsFromSelection = onStartTtsFromSelection,
            onNoteRequested = onNoteRequested,
            userHighlights = userHighlights,
            onHighlightCreated = onHighlightCreated,
            onHighlightDeleted = onHighlightDeleted,
            isDarkTheme = isDarkTheme,
            activeHighlightPalette = activeHighlightPalette,
            onUpdatePalette = onUpdatePalette,
            effectiveText = effectiveText,
            pageTextureModifier = if (isPageTurnAnimationEnabled) Modifier else textureModifier,
            pageTextureBitmap = textureBitmap,
            pageTextureAlpha = activeTextureAlpha.coerceIn(0f, 1f)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isNavigatingByLink,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
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
                        "Navigating...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
