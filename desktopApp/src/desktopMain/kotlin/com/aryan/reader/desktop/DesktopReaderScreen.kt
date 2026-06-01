package com.aryan.reader.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderAiFeature
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderExternalLookupAction
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderLinkTarget
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubPaginationCache
import com.aryan.reader.shared.reader.SharedMeasuredEpubPaginator
import com.aryan.reader.shared.reader.isRightToLeftPaginationEnabled
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.DesktopEpubNativeImage
import com.aryan.reader.shared.ui.ReaderContentRenderPlan
import com.aryan.reader.shared.ui.SharedNativePaginatedReader
import com.aryan.reader.shared.ui.SharedNativeReaderSelectionAction
import com.aryan.reader.shared.ui.SharedReaderScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.KeyEvent as AwtKeyEvent

@Composable
internal fun DesktopReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onReturnToLibrary: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
    readerAwtWindow: Window? = null,
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    appThemeControls: (@Composable () -> Unit)? = null,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    highlightPalette: ReaderHighlightPalette,
    onHighlightPaletteChange: (ReaderHighlightPalette) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String?,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onPickCustomFont: () -> String?,
    customFonts: List<CustomFontItem>,
    readerExtrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    externalLookupAvailable: Boolean,
    cloudTtsControlsAvailable: Boolean,
    onExternalLookup: (ReaderExternalLookupAction, String) -> Unit,
    onAiAction: (ReaderAiFeature, String) -> Unit,
    onAiResultDismiss: () -> Unit,
    onCloudTtsToggle: (String, ReaderLocator?) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    onOpenAiHub: (() -> Unit)? = null,
    onDownloadReaderImage: (ReaderImageReference) -> Unit,
    readerTextureDataUri: (String) -> String?,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?,
    bottomChromeExtraContent: @Composable ColumnScope.() -> Unit = {},
    webViewRuntimeState: DesktopWebViewRuntimeState,
    webViewNetworkAccessEnabled: Boolean,
    epubPaginationCache: SharedEpubPaginationCache,
    epubPaginationCacheGeneration: Int,
    useDetachedChromeLayer: Boolean = true,
    useDetachedPanelLayer: Boolean = true
) {
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val paginationCacheWriteScope = rememberCoroutineScope()
    val measuredPaginator = remember(
        textMeasurer,
        density,
        session.reader.settings.fontFamily,
        session.reader.settings.customFontPath,
        epubPaginationCache,
        paginationCacheWriteScope
    ) {
        SharedMeasuredEpubPaginator(
            textMeasurer = textMeasurer,
            density = density,
            fontFamily = session.reader.settings.toDesktopReaderFontFamily(),
            pageCache = epubPaginationCache,
            cacheWriteScope = paginationCacheWriteScope
        )
    }
    LaunchedEffect(session.reader.book.id) {
        logDesktopReaderOpenTrace {
            "event=desktop_text_reader_screen_composed bookId=\"${session.reader.book.id.logPreview(120)}\" " +
                "title=\"${session.reader.book.title.logPreview(120)}\" mode=${session.reader.settings.readingMode} " +
                "chapters=${session.reader.book.chapters.size} pages=${session.reader.pages.size} " +
                "currentPage=${session.reader.currentPageIndex + 1} " +
                "textChars=${session.reader.book.chapters.sumOf { it.plainText.length }} " +
                "htmlChars=${session.reader.book.chapters.sumOf { it.htmlContent.length }} " +
                "semanticBlocks=${session.reader.book.chapters.sumOf { it.semanticBlocks.size }} " +
                "bookmarks=${session.bookmarks.size} highlights=${session.highlights.size}"
        }
    }
    var readerViewport by remember(session.reader.book.id) { mutableStateOf(ReaderViewportSpec(0, 0)) }
    val paginationLayoutSignature = session.reader.settings.layoutSignature()
    val paginationContentSignature = remember(session.reader.book) {
        session.reader.book.desktopPaginationContentSignature()
    }
    val paginationDensitySignature = DesktopEpubPaginationDensity(
        density = density.density,
        fontScale = density.fontScale
    )
    val measuredPaginationRequest = remember(
        session.reader.book.id,
        paginationContentSignature,
        paginationLayoutSignature,
        readerViewport,
        paginationDensitySignature,
        epubPaginationCacheGeneration
    ) {
        if (session.reader.settings.readingMode == ReaderReadingMode.PAGINATED && readerViewport.isSpecified) {
            DesktopEpubPaginationRequest(
                bookId = session.reader.book.id,
                chapterSignature = paginationContentSignature,
                layoutSignature = paginationLayoutSignature,
                viewport = readerViewport,
                density = paginationDensitySignature,
                cacheGeneration = epubPaginationCacheGeneration
            )
        } else {
            null
        }
    }
    var completedMeasuredPaginationRequest by remember(session.reader.book.id) {
        mutableStateOf<DesktopEpubPaginationRequest?>(null)
    }
    var completedMeasuredPaginationPages by remember(session.reader.book.id) {
        mutableStateOf(emptyList<ReaderPage>())
    }
    var warmMeasuredPaginationRequest by remember(session.reader.book.id) {
        mutableStateOf<DesktopEpubPaginationRequest?>(null)
    }
    var warmMeasuredPaginationPages by remember(session.reader.book.id) {
        mutableStateOf(emptyList<ReaderPage>())
    }
    var runningMeasuredPaginationRequest by remember(session.reader.book.id) {
        mutableStateOf<DesktopEpubPaginationRequest?>(null)
    }
    val measuredPaginationPagesApplied = desktopMeasuredPaginationReady(
        request = measuredPaginationRequest,
        completedRequest = completedMeasuredPaginationRequest,
        currentPages = session.reader.pages,
        measuredPages = completedMeasuredPaginationPages
    )
    val warmMeasuredPaginationPagesApplied = desktopMeasuredPaginationReady(
        request = measuredPaginationRequest,
        completedRequest = warmMeasuredPaginationRequest,
        currentPages = session.reader.pages,
        measuredPages = warmMeasuredPaginationPages
    )
    val paginatedLayoutReady = desktopPaginatedLayoutReadyForDisplay(
        readingMode = session.reader.settings.readingMode,
        measuredPagesApplied = measuredPaginationPagesApplied
    )
    val latestSession by rememberUpdatedState(session)
    val latestOnSessionChange by rememberUpdatedState(onSessionChange)
    var externalLinkDialogUrl by remember { mutableStateOf<String?>(null) }
    var lastHandledLink by remember { mutableStateOf<DesktopEpubHandledLink?>(null) }
    var isFullscreen by remember(session.reader.book.id) { mutableStateOf(false) }
    val desktopReaderExtrasState = readerExtrasState.copy(autoScroll = ReaderAutoScrollState())
    val currentReaderFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnReaderFullscreenChange by rememberUpdatedState(onFullscreenChange)

    fun setReaderFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        onFullscreenChange(enabled)
    }

    DesktopExternalLinkDialog(
        url = externalLinkDialogUrl,
        onDismiss = { externalLinkDialogUrl = null }
    )

    fun handleReaderAwtKeyEvent(event: AwtKeyEvent): Boolean {
        val currentSession = latestSession
        val action = event.desktopReaderKeyNavigationOrNull(
            fullscreen = isFullscreen,
            rightToLeftPagination = currentSession.reader.settings.isRightToLeftPaginationEnabled()
        ) ?: return false
        val nextSession = currentSession.reduceDesktopReaderKeyNavigation(action, readerEngine)
        if (nextSession == null) {
            if (action == DesktopReaderKeyNavigation.EXIT_FULLSCREEN && isFullscreen) {
                setReaderFullscreen(false)
            }
        } else {
            latestOnSessionChange(nextSession)
        }
        return true
    }

    fun handleReaderFullscreenAwtKeyEvent(event: AwtKeyEvent): Boolean {
        if (latestSession.isSearchActive) {
            if (event.id == AwtKeyEvent.KEY_PRESSED && isFullscreen && event.keyCode == AwtKeyEvent.VK_ESCAPE) {
                setReaderFullscreen(false)
                return true
            }
            return false
        }
        return handleReaderAwtKeyEvent(event)
    }

    fun handleReaderGlobalShortcutAwtKeyEvent(event: AwtKeyEvent): Boolean {
        if (event.id != AwtKeyEvent.KEY_PRESSED || !event.isControlDown) return false
        val action = when (event.keyCode) {
            AwtKeyEvent.VK_F -> DesktopReaderKeyNavigation.SEARCH
            AwtKeyEvent.VK_G -> DesktopReaderKeyNavigation.NEXT_SEARCH
            else -> return false
        }
        val nextSession = latestSession.reduceDesktopReaderKeyNavigation(action, readerEngine) ?: return false
        latestOnSessionChange(nextSession)
        return true
    }

    DesktopReaderKeyDispatcherEffect(
        enabled = externalLinkDialogUrl == null,
        allowChromeModalWindows = true,
        onKeyPressed = { event -> handleReaderGlobalShortcutAwtKeyEvent(event) }
    )

    DesktopReaderKeyDispatcherEffect(
        enabled = externalLinkDialogUrl == null && !session.isSearchActive,
        allowPanelModalWindows = true,
        dispatchWhenOwnerWindowActive = false,
        onKeyPressed = { event -> handleReaderAwtKeyEvent(event) }
    )

    DesktopReaderFullscreenKeyEffect(
        enabled = isFullscreen && externalLinkDialogUrl == null,
        onKeyPressed = { event -> handleReaderFullscreenAwtKeyEvent(event) }
    )

    LaunchedEffect(session.reader.settings.readingMode) {
        if (session.reader.settings.readingMode != ReaderReadingMode.PAGINATED) {
            completedMeasuredPaginationRequest = null
            completedMeasuredPaginationPages = emptyList()
            warmMeasuredPaginationRequest = null
            warmMeasuredPaginationPages = emptyList()
            runningMeasuredPaginationRequest = null
        }
    }

    DisposableEffect(session.reader.book.id) {
        onDispose {
            if (currentReaderFullscreen) {
                currentOnReaderFullscreenChange(false)
            }
        }
    }

    LaunchedEffect(
        measuredPaginationRequest,
        measuredPaginator
    ) {
        val request = measuredPaginationRequest ?: return@LaunchedEffect
        if (completedMeasuredPaginationRequest == request) {
            logEpubPagination(
                "reflow_skip reason=request_already_measured book=\"${session.reader.book.title.logPreview()}\" " +
                    "viewport=${request.viewport.widthPx}x${request.viewport.heightPx}"
            )
            return@LaunchedEffect
        }
        runningMeasuredPaginationRequest = request
        try {
            val cacheProbeStartedAt = System.nanoTime()
            val cacheProbeSettings = latestSession.reader.settings
            val cachedPages = if (
                cacheProbeSettings.readingMode == ReaderReadingMode.PAGINATED &&
                cacheProbeSettings.layoutSignature() == request.layoutSignature
            ) {
                withContext(Dispatchers.Default) {
                    epubPaginationCache.loadMemory(
                        book = session.reader.book,
                        settings = cacheProbeSettings,
                        viewport = request.viewport,
                        density = request.density.density,
                        fontScale = request.density.fontScale
                    )
                }
            } else {
                null
            }
            val settingsAfterCacheProbe = latestSession.reader.settings
            if (settingsAfterCacheProbe.readingMode != ReaderReadingMode.PAGINATED) return@LaunchedEffect
            if (settingsAfterCacheProbe.layoutSignature() != request.layoutSignature) return@LaunchedEffect
            if (cachedPages != null) {
                val cacheLayoutChanged = !latestSession.reader.pages.samePageLayoutAs(cachedPages)
                logEpubPagination(
                    "cache_warm_result book=\"${session.reader.book.title.logPreview()}\" pages=${cachedPages.size} " +
                        "layoutChanged=$cacheLayoutChanged viewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                        "elapsedMs=${cacheProbeStartedAt.elapsedMillis()}"
                )
                if (cacheLayoutChanged) {
                    val cacheApplySession = latestSession
                    latestOnSessionChange(
                        readerEngine.replacePages(
                            state = cacheApplySession,
                            pages = cachedPages,
                            reflowAnchor = readerEngine.reflowAnchorFor(cacheApplySession)
                        )
                    )
                }
                completedMeasuredPaginationPages = cachedPages
                completedMeasuredPaginationRequest = request
                return@LaunchedEffect
            }

            val warmStartSession = latestSession
            val warmAnchor = readerEngine.reflowAnchorFor(warmStartSession)
            val warmChapterIndex = warmAnchor?.chapterIndex
                ?: warmStartSession.reader.currentPage?.chapterIndex
                ?: 0
            val warmFirstPageIndex = warmStartSession.reader.pages.firstPageIndexForChapter(warmChapterIndex) ?: 0
            val warmStartedAt = System.nanoTime()
            logEpubPagination(
                "chapter_warm_start book=\"${session.reader.book.title.logPreview()}\" chapter=$warmChapterIndex " +
                    "firstPage=${warmFirstPageIndex + 1} viewport=${request.viewport.widthPx}x${request.viewport.heightPx}"
            )
            val cachedWarmChapterPages = epubPaginationCache.loadChapter(
                book = session.reader.book,
                settings = settingsAfterCacheProbe,
                viewport = request.viewport,
                chapterIndex = warmChapterIndex,
                density = request.density.density,
                fontScale = request.density.fontScale
            )
            val warmChapterPages = cachedWarmChapterPages ?: withContext(Dispatchers.Default) {
                measuredPaginator.paginateChapterWindow(
                    book = session.reader.book,
                    settings = settingsAfterCacheProbe,
                    viewport = request.viewport,
                    chapterIndex = warmChapterIndex,
                    firstPageIndex = warmFirstPageIndex
                )
            }
            val warmPages = desktopPagesWithMeasuredChapter(
                currentPages = warmStartSession.reader.pages,
                chapterIndex = warmChapterIndex,
                measuredChapterPages = warmChapterPages
            )
            val warmLayoutChanged = warmPages.isNotEmpty() && !warmStartSession.reader.pages.samePageLayoutAs(warmPages)
            logEpubPagination(
                "chapter_warm_result book=\"${session.reader.book.title.logPreview()}\" chapter=$warmChapterIndex " +
                    "source=${if (cachedWarmChapterPages != null) "cache" else "measured"} " +
                    "chapterPages=${warmChapterPages.size} pages=${warmPages.size} layoutChanged=$warmLayoutChanged " +
                    "elapsedMs=${warmStartedAt.elapsedMillis()}"
            )
            if (warmLayoutChanged) {
                logReaderModeSwitch(
                    "pagination_warm_apply_dispatch requestViewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                        "chapter=$warmChapterIndex chapterPages=${warmChapterPages.size} currentPages=${warmStartSession.reader.pages.size}"
                )
                latestOnSessionChange(
                    readerEngine.replacePages(
                        state = warmStartSession,
                        pages = warmPages,
                        reflowAnchor = warmAnchor
                    )
                )
                warmMeasuredPaginationPages = warmPages
                warmMeasuredPaginationRequest = request
            }

            val reflowStartSession = latestSession
            val reflowStartRequestId = reflowStartSession.navigationRequestId
            val reflowAnchor = readerEngine.reflowAnchorFor(reflowStartSession)
            val settings = reflowStartSession.reader.settings
            if (settings.readingMode != ReaderReadingMode.PAGINATED) return@LaunchedEffect
            if (settings.layoutSignature() != request.layoutSignature) return@LaunchedEffect
            logEpubPagination(
                "reflow_start book=\"${session.reader.book.title.logPreview()}\" " +
                    "viewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                    "spread=${settings.pageSpreadMode} font=${settings.fontSize} lineSpacing=${settings.lineSpacing} " +
                    "margins=${settings.resolvedHorizontalMargin}x${settings.resolvedVerticalMargin} " +
                    "pageWidthSetting=${settings.pageWidth} oldPages=${reflowStartSession.reader.pages.size} " +
                    "anchorPage=${reflowAnchor?.pageIndex} anchorOffsets=${reflowAnchor?.startOffset}..${reflowAnchor?.endOffset}"
            )
            val pages = withContext(Dispatchers.Default) {
                measuredPaginator.paginate(
                    book = session.reader.book,
                    settings = settings,
                    viewport = request.viewport,
                    readCache = true
                )
            }
            val layoutChanged = pages.isNotEmpty() && !latestSession.reader.pages.samePageLayoutAs(pages)
            logEpubPagination(
                "reflow_result book=\"${session.reader.book.title.logPreview()}\" pages=${pages.size} " +
                    "layoutChanged=$layoutChanged currentPages=${latestSession.reader.pages.size}"
            )
            val currentVisiblePageDetails = latestSession.reader.visiblePages.map { page ->
                "${page.pageIndex + 1}:text=${page.text.length}:blocks=${page.semanticBlocks.size}"
            }
            val measuredCurrentPageDetails = pages.getOrNull(latestSession.reader.currentPageIndex)
                ?.let { page -> "${page.pageIndex + 1}:text=${page.text.length}:blocks=${page.semanticBlocks.size}" }
                ?: "none"
            logReaderModeSwitch(
                "pagination_result requestViewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                    "measuredPages=${pages.size} currentPages=${latestSession.reader.pages.size} layoutChanged=$layoutChanged " +
                    "currentVisible=$currentVisiblePageDetails measuredAtCurrent=$measuredCurrentPageDetails"
            )
            if (layoutChanged) {
                logReaderModeSwitch(
                    "pagination_apply_dispatch requestViewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                        "measuredPages=${pages.size} currentPages=${latestSession.reader.pages.size}"
                )
                latestOnSessionChange(
                    readerEngine.replacePages(
                        state = latestSession,
                        pages = pages,
                        reflowAnchor = reflowAnchor,
                        navigationRequestIdAtReflowStart = reflowStartRequestId
                    )
                )
            }
            if (pages.isNotEmpty()) {
                completedMeasuredPaginationPages = pages
                completedMeasuredPaginationRequest = request
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            logEpubPagination(
                "reflow_failed book=\"${session.reader.book.title.logPreview()}\" " +
                    "viewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                    "error=\"${error.message.orEmpty().logPreview(300)}\""
            )
        } finally {
            if (runningMeasuredPaginationRequest == request) {
                runningMeasuredPaginationRequest = null
            }
        }
    }

    LaunchedEffect(
        measuredPaginationRequest,
        completedMeasuredPaginationRequest,
        completedMeasuredPaginationPages,
        session.reader.pages
    ) {
        val request = measuredPaginationRequest ?: return@LaunchedEffect
        val measuredPages = completedMeasuredPaginationPages
        if (session.reader.settings.readingMode != ReaderReadingMode.PAGINATED) return@LaunchedEffect
        if (completedMeasuredPaginationRequest != request || measuredPages.isEmpty()) return@LaunchedEffect
        if (session.reader.pages.samePageLayoutAs(measuredPages)) return@LaunchedEffect
        val currentVisiblePageDetails = session.reader.visiblePages.map { page ->
            "${page.pageIndex + 1}:text=${page.text.length}:blocks=${page.semanticBlocks.size}"
        }
        logReaderModeSwitch(
            "pagination_apply_pending requestViewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                "currentPages=${session.reader.pages.size} measuredPages=${measuredPages.size} " +
                "currentVisible=$currentVisiblePageDetails"
        )
        onSessionChange(
            readerEngine.replacePages(
                state = session,
                pages = measuredPages,
                reflowAnchor = readerEngine.reflowAnchorFor(session)
            )
        )
    }

    val handleDesktopSelectionAction: (DesktopReaderSelectionAction, String, ReaderLocator?) -> Unit = { action, text, locator ->
        val settings = aiByokSettings.sanitized()
        when (action) {
            DesktopReaderSelectionAction.DEFINE -> {
                if (settings.areReaderAiFeaturesAvailable) onAiAction(ReaderAiFeature.DEFINE, text)
            }
            DesktopReaderSelectionAction.SPEAK -> {
                if (settings.isCloudTtsAvailable) onCloudTtsToggle(text, locator)
            }
            DesktopReaderSelectionAction.SEARCH -> onExternalLookup(ReaderExternalLookupAction.SEARCH, text)
            DesktopReaderSelectionAction.PALETTE -> Unit
        }
    }
    val nativeSelectionActions = buildSet {
        val settings = aiByokSettings.sanitized()
        if (settings.areReaderAiFeaturesAvailable) add(SharedNativeReaderSelectionAction.DEFINE)
        if (externalLookupAvailable) add(SharedNativeReaderSelectionAction.SEARCH)
        if (settings.isCloudTtsAvailable) add(SharedNativeReaderSelectionAction.SPEAK)
    }
    val handleNativeSelectionAction: (SharedNativeReaderSelectionAction, String, ReaderLocator?) -> Unit = { action, text, locator ->
        when (action) {
            SharedNativeReaderSelectionAction.DEFINE ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.DEFINE, text, locator)
            SharedNativeReaderSelectionAction.SPEAK ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.SPEAK, text, locator)
            SharedNativeReaderSelectionAction.SEARCH ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.SEARCH, text, locator)
        }
    }
    val handleDesktopEpubLinkClicked: (DesktopEpubLinkClick) -> Unit = { link ->
        val now = System.currentTimeMillis()
        val last = lastHandledLink
        if (last != null && last.href == link.href && now - last.handledAtMs < 900L) {
            logEpubLink(
                "click_duplicate_ignored source=${link.source} href=\"${link.href.logPreview()}\" " +
                    "ageMs=${now - last.handledAtMs}"
            )
        } else {
            lastHandledLink = DesktopEpubHandledLink(link.href, now)
            logEpubLink(
                "click source=${link.source} href=\"${link.href.logPreview()}\" " +
                    "chapterIndex=${link.chapterIndex} chapterHref=\"${link.chapterHref.orEmpty().logPreview()}\" " +
                    "text=\"${link.text.orEmpty().logPreview()}\""
            )
            when (val target = readerEngine.resolveLink(session, link.href, link.chapterIndex)) {
                is ReaderLinkTarget.External -> {
                    logEpubLink("resolved_external url=\"${target.url.logPreview()}\"")
                    if (externalLookupAvailable) {
                        externalLinkDialogUrl = target.url
                    }
                }
                is ReaderLinkTarget.Internal -> {
                    logEpubLink(
                        "resolved_internal chapter=${target.locator.chapterIndex} " +
                            "page=${target.locator.pageIndex} offset=${target.locator.startOffset}"
                    )
                    onSessionChange(readerEngine.jumpToLocator(session, target.locator))
                }
                ReaderLinkTarget.Ignored -> {
                    logEpubLink("resolved_ignored href=\"${link.href.logPreview()}\"")
                }
            }
        }
    }

    SharedReaderScreen(
        session = session,
        readerEngine = readerEngine,
        onSessionChange = onSessionChange,
        onReturnToLibrary = onReturnToLibrary,
        isFullscreen = isFullscreen,
        onFullscreenChange = ::setReaderFullscreen,
        toolbarPreferences = toolbarPreferences,
        onToolbarPreferencesChange = onToolbarPreferencesChange,
        appThemeControls = appThemeControls,
        customReaderThemes = customReaderThemes,
        onCustomReaderThemesChange = onCustomReaderThemesChange,
        highlightPalette = highlightPalette,
        onHighlightPaletteChange = onHighlightPaletteChange,
        ttsReplacementPreferences = ttsReplacementPreferences,
        ttsReplacementBookId = ttsReplacementBookId,
        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
        onPickCustomFont = onPickCustomFont,
        customFonts = customFonts,
        readerExtrasState = desktopReaderExtrasState,
        aiByokSettings = aiByokSettings,
        externalLookupAvailable = externalLookupAvailable,
        cloudTtsControlsAvailable = cloudTtsControlsAvailable,
        onExternalLookup = onExternalLookup,
        onAiAction = onAiAction,
        onAiResultDismiss = onAiResultDismiss,
        onCopyText = { text -> clipboardManager.setText(AnnotatedString(text)) },
        onCloudTtsStart = onCloudTtsStart,
        onCloudTtsPauseResume = onCloudTtsPauseResume,
        onCloudTtsStop = onCloudTtsStop,
        onCloudTtsClearCache = onCloudTtsClearCache,
        onCloudTtsVoiceChange = onCloudTtsVoiceChange,
        onOpenAiHub = onOpenAiHub,
        onDownloadReaderImage = onDownloadReaderImage,
        readerImagePreviewContent = { image, previewModifier ->
            DesktopEpubNativeImage(
                image = image.toDesktopPreviewSemanticImage(),
                modifier = previewModifier.clip(RoundedCornerShape(3.dp))
            )
        },
        readerTextureDataUri = readerTextureDataUri,
        readerTexturePreviewContent = { textureId, previewModifier ->
            DesktopReaderTexturePreview(textureId = textureId, modifier = previewModifier)
        },
        readerCustomTextureIds = readerCustomTextureIds,
        onImportReaderTexture = onImportReaderTexture,
        bottomChromeExtraContent = bottomChromeExtraContent,
        useDetachedChromeLayer = useDetachedChromeLayer,
        useDetachedPanelLayer = useDetachedPanelLayer
    ) { renderPlan, onVisiblePageChanged, onHighlightSelected, onOpenHighlightPaletteManager, onChromeActivity ->
        val renderPlanModeKey = renderPlan.desktopReaderSurfaceModeKey()
        val readerSurfaceKey = renderPlan.desktopReaderSurfaceContentKey(paginatedLayoutReady)
        val readerModeSwitchLayoutModifier =
            if (renderPlan is ReaderContentRenderPlan.NativePaginatedPages) {
                Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    logReaderModeSwitch(
                        "native_surface_layout modeKey=$renderPlanModeKey surfaceKey=$readerSurfaceKey " +
                            "paginatedReady=$paginatedLayoutReady size=${coordinates.size.width}x${coordinates.size.height} " +
                            "windowBounds=${bounds.left.formatLogFloat()},${bounds.top.formatLogFloat()} " +
                            "${bounds.width.formatLogFloat()}x${bounds.height.formatLogFloat()}"
                    )
                }
            } else {
                Modifier
            }
        val readerSurfaceModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .onSizeChanged { size ->
                val next = ReaderViewportSpec(size.width, size.height)
                logReaderGap(
                    "desktop_epub_reader_surface size=${size.width}x${size.height} " +
                        "mode=${session.reader.settings.readingMode} " +
                        "page=${session.reader.currentPageIndex + 1}/${session.reader.pages.size.coerceAtLeast(1)}"
                )
                logEpubCutoff(
                    "cutoff_probe layer=desktop_surface size=${size.width}x${size.height} " +
                        "mode=${session.reader.settings.readingMode} spread=${session.reader.settings.pageSpreadMode} " +
                        "page=${session.reader.currentPageIndex + 1}/${session.reader.pages.size.coerceAtLeast(1)} " +
                        "margins=${session.reader.settings.resolvedHorizontalMargin}x${session.reader.settings.resolvedVerticalMargin} " +
                        "pageWidthSetting=${session.reader.settings.pageWidth}"
                )
                logWebViewLayoutDiag(
                    "compose_reader_surface size=${size.width}x${size.height} " +
                        "renderPlan=${if (renderPlan is ReaderContentRenderPlan.WebDocument) "web" else "native"} " +
                        "mode=${session.reader.settings.readingMode} " +
                        "fullscreen=$isFullscreen margins=${session.reader.settings.resolvedHorizontalMargin}x${session.reader.settings.resolvedVerticalMargin} " +
                        "pageWidth=${session.reader.settings.pageWidth} fontSize=${session.reader.settings.fontSize} " +
                        "lineSpacing=${session.reader.settings.lineSpacing} textAlign=${session.reader.settings.textAlign} " +
                        "paragraphSpacing=${session.reader.settings.paragraphSpacing} imageScale=${session.reader.settings.imageScale}"
                )
                logDesktopReaderOpenTrace {
                    "event=desktop_reader_surface_size bookId=\"${session.reader.book.id.logPreview(120)}\" " +
                        "title=\"${session.reader.book.title.logPreview(120)}\" size=${size.width}x${size.height} " +
                        "renderPlan=${renderPlan.desktopReaderSurfaceModeLabel()} mode=${session.reader.settings.readingMode} " +
                        "page=${session.reader.currentPageIndex + 1}/${session.reader.pages.size.coerceAtLeast(1)}"
                }
                if (next != readerViewport) {
                    logEpubPagination(
                        "viewport_changed width=${next.widthPx} height=${next.heightPx} " +
                            "previous=${readerViewport.widthPx}x${readerViewport.heightPx}"
                    )
                    readerViewport = next
                }
            }
        LaunchedEffect(
            renderPlanModeKey,
            session.reader.settings.readingMode,
            paginatedLayoutReady
        ) {
            logReaderModeSwitch(
                "surface_state modeKey=$renderPlanModeKey readingMode=${session.reader.settings.readingMode} " +
                    "renderPlan=${renderPlan.desktopReaderSurfaceModeLabel()} viewport=${readerViewport.widthPx}x${readerViewport.heightPx} " +
                    "paginatedReady=$paginatedLayoutReady runningPagination=${runningMeasuredPaginationRequest != null} " +
                    "completedPagination=${completedMeasuredPaginationRequest != null} measuredApplied=$measuredPaginationPagesApplied " +
                    "warmApplied=$warmMeasuredPaginationPagesApplied warmPageCount=${warmMeasuredPaginationPages.size} " +
                    "completedMatchesRequest=${completedMeasuredPaginationRequest == measuredPaginationRequest} " +
                    "measuredPageCount=${completedMeasuredPaginationPages.size} " +
                    "currentPage=${session.reader.currentPageIndex + 1} " +
                    "pageCount=${session.reader.pages.size} visiblePages=${session.reader.visiblePages.map { it.pageIndex + 1 }} " +
                    "fullscreen=$isFullscreen surfaceKey=$readerSurfaceKey"
            )
            logDesktopReaderOpenTrace {
                "event=desktop_render_plan_ready bookId=\"${session.reader.book.id.logPreview(120)}\" " +
                    "title=\"${session.reader.book.title.logPreview(120)}\" " +
                    "renderPlan=${renderPlan.desktopReaderSurfaceModeLabel()} mode=${session.reader.settings.readingMode} " +
                    "viewport=${readerViewport.widthPx}x${readerViewport.heightPx} " +
                    "page=${session.reader.currentPageIndex + 1}/${session.reader.pages.size.coerceAtLeast(1)} " +
                    "htmlChars=${(renderPlan as? ReaderContentRenderPlan.WebDocument)?.html?.length ?: 0} " +
                    "paginatedReady=$paginatedLayoutReady"
            }
            if (renderPlan is ReaderContentRenderPlan.NativePaginatedPages) {
                cleanupRetiredDesktopWebView2InteropHosts(
                    readerAwtWindow,
                    "native_surface_state_ready_$paginatedLayoutReady"
                )
                logDesktopWebView2ModeSwitchSnapshot("surface_state_${renderPlanModeKey}_after_sweep_request")
                readerAwtWindow.requestDesktopReaderModeSwitchRepaint(
                    "native_surface_state_ready_$paginatedLayoutReady"
                )
                DesktopReaderModeSwitchProbeDelaysMillis.forEach { delayMillis ->
                    delay(delayMillis)
                    cleanupRetiredDesktopWebView2InteropHosts(
                        readerAwtWindow,
                        "native_probe_after_${delayMillis}ms_ready_$paginatedLayoutReady"
                    )
                    readerAwtWindow.requestDesktopReaderModeSwitchRepaint(
                        "native_probe_after_${delayMillis}ms_ready_$paginatedLayoutReady"
                    )
                    logReaderModeSwitch(
                        "native_probe_after delayMs=$delayMillis modeKey=$renderPlanModeKey " +
                            "viewport=${readerViewport.widthPx}x${readerViewport.heightPx} " +
                            "paginatedReady=$paginatedLayoutReady currentPage=${session.reader.currentPageIndex + 1} " +
                            "visiblePages=${session.reader.visiblePages.map { it.pageIndex + 1 }}"
                    )
                    logDesktopWebView2ModeSwitchSnapshot("native_probe_after_${delayMillis}ms")
                }
            } else {
                logDesktopWebView2ModeSwitchSnapshot("surface_state_$renderPlanModeKey")
            }
        }
        DisposableEffect(renderPlanModeKey) {
            logReaderModeSwitch(
                "surface_enter modeKey=$renderPlanModeKey readingMode=${session.reader.settings.readingMode} " +
                    "renderPlan=${renderPlan.desktopReaderSurfaceModeLabel()}"
            )
            logDesktopWebView2ModeSwitchSnapshot("surface_enter_$renderPlanModeKey")
            onDispose {
                logReaderModeSwitch(
                    "surface_exit modeKey=$renderPlanModeKey readingMode=${session.reader.settings.readingMode} " +
                        "renderPlan=${renderPlan.desktopReaderSurfaceModeLabel()}"
                )
                logDesktopWebView2ModeSwitchSnapshot("surface_exit_$renderPlanModeKey")
            }
        }
        @Composable
        fun ReaderSurfaceContent() {
            if (renderPlan is ReaderContentRenderPlan.NativePaginatedPages && !paginatedLayoutReady) {
                DesktopEpubPaginationPreparing(
                    active = runningMeasuredPaginationRequest != null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                when (renderPlan) {
                    is ReaderContentRenderPlan.WebDocument -> {
                        val canRenderWebDocument = desktopEpubWebViewCanRender(webViewRuntimeState)
                        LaunchedEffect(
                            renderPlan.html,
                            canRenderWebDocument,
                            webViewRuntimeState,
                            webViewNetworkAccessEnabled
                        ) {
                            logDesktopWebView2(
                                "reader_screen_web_document canRender=$canRenderWebDocument " +
                                    "backend=${desktopEpubWebViewBackend().logName} " +
                                    "runtimeInitialized=${webViewRuntimeState.initialized} restart=${webViewRuntimeState.restartRequired} " +
                                    "error=${webViewRuntimeState.errorMessage != null} network=$webViewNetworkAccessEnabled " +
                                    "htmlChars=${renderPlan.html.length} htmlHash=${renderPlan.html.hashCode()}"
                            )
                        }
                        if (canRenderWebDocument) {
                            DesktopEpubWebView(
                                html = renderPlan.html,
                                appearanceScript = renderPlan.appearanceScript,
                                highlightPaletteScript = renderPlan.highlightPaletteScript,
                                navigationTarget = renderPlan.navigationTarget,
                                highlights = renderPlan.highlights,
                                onHighlightCreated = { highlight ->
                                    logEpubHighlightFlow(
                                        "state_reduce_start id=${highlight.id} before=${session.highlights.size} " +
                                            "color=${highlight.color.id} chapter=${highlight.chapterIndex} " +
                                            "offsets=${highlight.locator.startOffset}..${highlight.locator.endOffset} " +
                                            "page=${highlight.locator.pageIndex} textChars=${highlight.text.length}"
                                    )
                                    val nextSession = session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine)
                                    logEpubHighlightFlow(
                                        "state_reduce_done id=${highlight.id} after=${nextSession.highlights.size} " +
                                            "contains=${nextSession.highlights.any { it.id == highlight.id }}"
                                    )
                                    onSessionChange(nextSession)
                                },
                                onHighlightSelected = onHighlightSelected,
                                isFullscreen = isFullscreen,
                                onKeyboardNavigation = { action ->
                                    val nextSession = session.reduceDesktopReaderKeyNavigation(action, readerEngine)
                                    if (nextSession == null) {
                                        if (action == DesktopReaderKeyNavigation.EXIT_FULLSCREEN && isFullscreen) {
                                            setReaderFullscreen(false)
                                        }
                                    } else {
                                        onSessionChange(nextSession)
                                    }
                                },
                                onSelectionAction = { payload ->
                                    if (payload.action == DesktopReaderSelectionAction.PALETTE) {
                                        onOpenHighlightPaletteManager()
                                    } else {
                                        handleDesktopSelectionAction(payload.action, payload.text, payload.locator)
                                    }
                                },
                                onLinkClicked = handleDesktopEpubLinkClicked,
                                onVisiblePageChanged = onVisiblePageChanged,
                                onPointerActivity = onChromeActivity,
                                networkAccessEnabled = webViewNetworkAccessEnabled,
                                backgroundColor = renderPlan.background,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            DesktopWebViewRuntimeIndicator(
                                state = webViewRuntimeState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    is ReaderContentRenderPlan.NativePaginatedPages -> {
                        LaunchedEffect(renderPlan.visiblePages, paginatedLayoutReady) {
                            val pageDetails = renderPlan.visiblePages.joinToString(prefix = "[", postfix = "]") { page ->
                                "${page.pageIndex + 1}:text=${page.text.length}:blocks=${page.semanticBlocks.size}"
                            }
                            logReaderModeSwitch(
                                "native_reader_render paginatedReady=$paginatedLayoutReady " +
                                    "visiblePages=${renderPlan.visiblePages.map { it.pageIndex + 1 }} " +
                                    "pageDetails=$pageDetails " +
                                    "background=${renderPlan.background} foreground=${renderPlan.foreground}"
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    logReaderModeSwitch(
                                        "native_reader_content_layout size=${coordinates.size.width}x${coordinates.size.height} " +
                                            "windowBounds=${bounds.left.formatLogFloat()},${bounds.top.formatLogFloat()} " +
                                            "${bounds.width.formatLogFloat()}x${bounds.height.formatLogFloat()} " +
                                            "visiblePages=${renderPlan.visiblePages.map { it.pageIndex + 1 }}"
                                    )
                                }
                        ) {
                            SharedNativePaginatedReader(
                                renderPlan = renderPlan,
                                readerFontFamily = renderPlan.settings.toDesktopReaderFontFamily(),
                                searchHighlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                                onVisiblePageChanged = onVisiblePageChanged,
                                enabledSelectionActions = nativeSelectionActions,
                                onCopyText = { text -> clipboardManager.setText(AnnotatedString(text)) },
                                onSelectionAction = handleNativeSelectionAction,
                                onOpenHighlightPaletteManager = onOpenHighlightPaletteManager,
                                onHighlightCreated = { highlight ->
                                    logDesktopHighlightMap(
                                        "native_state_reduce_start id=${highlight.id.logPreview(80)} before=${session.highlights.size} " +
                                            "color=${highlight.color.id} chapter=${highlight.chapterIndex} " +
                                            "page=${highlight.locator.pageIndex} offsets=${highlight.locator.startOffset}..${highlight.locator.endOffset} " +
                                            "block=${highlight.locator.blockIndex} char=${highlight.locator.charOffset} " +
                                            "textChars=${highlight.text.length} cfi=\"${highlight.cfi.logPreview(160)}\""
                                    )
                                    val nextSession = session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine)
                                    logDesktopHighlightMap(
                                        "native_state_reduce_done id=${highlight.id.logPreview(80)} after=${nextSession.highlights.size} " +
                                            "contains=${nextSession.highlights.any { it.id == highlight.id }}"
                                    )
                                    onSessionChange(nextSession)
                                },
                                onHighlightSelected = onHighlightSelected,
                                onLinkClicked = { link ->
                                    handleDesktopEpubLinkClicked(link.toDesktopEpubLinkClick())
                                },
                                onReaderTap = onChromeActivity,
                                imageContent = { image, imageModifier ->
                                    DesktopEpubNativeImage(
                                        image = image,
                                        modifier = imageModifier
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        key(readerSurfaceKey) {
            if (renderPlan is ReaderContentRenderPlan.WebDocument) {
                Box(
                    modifier = readerSurfaceModifier
                        .fillMaxSize()
                        .background(renderPlan.background)
                ) {
                    ReaderSurfaceContent()
                }
            } else {
                Surface(
                    color = renderPlan.background,
                    shape = RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp),
                    modifier = readerSurfaceModifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp))
                        .then(readerModeSwitchLayoutModifier)
                ) {
                    ReaderSurfaceContent()
                }
            }
        }
    }
}

private fun ReaderContentRenderPlan.desktopReaderSurfaceModeKey(): String {
    return when (this) {
        is ReaderContentRenderPlan.WebDocument -> "desktop-reader-web"
        is ReaderContentRenderPlan.NativePaginatedPages -> "desktop-reader-native"
    }
}

private fun ReaderContentRenderPlan.desktopReaderSurfaceModeLabel(): String {
    return when (this) {
        is ReaderContentRenderPlan.WebDocument -> "web"
        is ReaderContentRenderPlan.NativePaginatedPages -> "native"
    }
}

private fun ReaderContentRenderPlan.desktopReaderSurfaceContentKey(paginatedLayoutReady: Boolean): String {
    return when (this) {
        is ReaderContentRenderPlan.WebDocument -> "desktop-reader-web"
        is ReaderContentRenderPlan.NativePaginatedPages ->
            "desktop-reader-native-${if (paginatedLayoutReady) "ready" else "preparing"}"
    }
}

private fun Window?.requestDesktopReaderModeSwitchRepaint(reason: String) {
    val targetWindow = this
    EventQueue.invokeLater {
        if (targetWindow == null) {
            logReaderModeSwitch("awt_repaint_skip reason=$reason window=null")
            return@invokeLater
        }
        if (!targetWindow.isDisplayable) {
            logReaderModeSwitch(
                "awt_repaint_skip reason=$reason window=${targetWindow.javaClass.simpleName} " +
                    "displayable=false visible=${targetWindow.isVisible} showing=${targetWindow.isShowing} " +
                    "size=${targetWindow.width}x${targetWindow.height}"
            )
            return@invokeLater
        }
        targetWindow.invalidate()
        targetWindow.validate()
        targetWindow.repaint()
        (targetWindow as? javax.swing.RootPaneContainer)?.contentPane?.let { contentPane ->
            contentPane.invalidate()
            contentPane.validate()
            contentPane.repaint()
        }
        logReaderModeSwitch(
            "awt_repaint reason=$reason window=${targetWindow.javaClass.simpleName} " +
                "visible=${targetWindow.isVisible} displayable=${targetWindow.isDisplayable} " +
                "showing=${targetWindow.isShowing} size=${targetWindow.width}x${targetWindow.height}"
        )
    }
}

private val DesktopReaderModeSwitchProbeDelaysMillis = longArrayOf(120L, 350L, 900L)

private fun Long.elapsedMillis(): Long {
    return ((System.nanoTime() - this) / 1_000_000L).coerceAtLeast(0L)
}

private fun ReaderImageReference.toDesktopPreviewSemanticImage(): SemanticImage {
    return SemanticImage(
        path = source,
        altText = altText,
        intrinsicWidth = intrinsicWidth,
        intrinsicHeight = intrinsicHeight,
        style = CssStyle(),
        elementId = null,
        cfi = cfi,
        blockIndex = blockIndex
    )
}

private fun ReaderSessionState.reduceDesktopReaderKeyNavigation(
    action: DesktopReaderKeyNavigation,
    readerEngine: ReaderEngine
): ReaderSessionState? {
    return when (action) {
        DesktopReaderKeyNavigation.NEXT -> reduce(ReaderAction.NextPage, readerEngine)
        DesktopReaderKeyNavigation.PREVIOUS -> reduce(ReaderAction.PreviousPage, readerEngine)
        DesktopReaderKeyNavigation.FIRST -> reduce(ReaderAction.JumpToPage(0), readerEngine)
        DesktopReaderKeyNavigation.LAST -> reduce(ReaderAction.JumpToPage(reader.pages.lastIndex), readerEngine)
        DesktopReaderKeyNavigation.SEARCH -> reduce(ReaderAction.SearchOpened, readerEngine)
        DesktopReaderKeyNavigation.NEXT_SEARCH -> reduce(ReaderAction.JumpToNextSearchResult, readerEngine)
        DesktopReaderKeyNavigation.EXIT_FULLSCREEN -> null
    }
}
