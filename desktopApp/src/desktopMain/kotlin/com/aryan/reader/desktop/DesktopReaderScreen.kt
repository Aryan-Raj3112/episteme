package com.aryan.reader.desktop

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsChunk
import com.aryan.reader.shared.ReaderTtsReadScope
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.ReaderImageReference
import com.aryan.reader.shared.reader.ReaderLinkTarget
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.ReaderViewportSpec
import com.aryan.reader.shared.reader.SharedEpubPaginationCache
import com.aryan.reader.shared.reader.SharedMeasuredEpubPaginator
import com.aryan.reader.shared.reader.layoutSignature
import com.aryan.reader.shared.reduce
import com.aryan.reader.shared.ui.DesktopEpubNativeImage
import com.aryan.reader.shared.ui.ReaderContentRenderPlan
import com.aryan.reader.shared.ui.SharedNativePaginatedReader
import com.aryan.reader.shared.ui.SharedNativeReaderSelectionAction
import com.aryan.reader.shared.ui.SharedReaderScreen
import kotlinx.coroutines.delay
import java.awt.event.KeyEvent as AwtKeyEvent

@Composable
internal fun DesktopReaderScreen(
    session: ReaderSessionState,
    readerEngine: ReaderEngine,
    onSessionChange: (ReaderSessionState) -> Unit,
    onReturnToLibrary: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
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
    onCloudTtsToggle: (String) -> Unit,
    onCloudTtsStart: (ReaderTtsReadScope, List<ReaderTtsChunk>) -> Unit,
    onCloudTtsPauseResume: () -> Unit,
    onCloudTtsStop: () -> Unit,
    onCloudTtsClearCache: () -> Unit,
    onOpenAiHub: (() -> Unit)? = null,
    onAutoScrollChange: (ReaderAutoScrollState) -> Unit,
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
    var runningMeasuredPaginationRequest by remember(session.reader.book.id) {
        mutableStateOf<DesktopEpubPaginationRequest?>(null)
    }
    val paginatedLayoutReady = session.reader.settings.readingMode != ReaderReadingMode.PAGINATED ||
        (measuredPaginationRequest != null && completedMeasuredPaginationRequest == measuredPaginationRequest)
    val latestSession by rememberUpdatedState(session)
    val latestOnSessionChange by rememberUpdatedState(onSessionChange)
    var externalLinkDialogUrl by remember { mutableStateOf<String?>(null) }
    var lastHandledLink by remember { mutableStateOf<DesktopEpubHandledLink?>(null) }
    var isFullscreen by remember(session.reader.book.id) { mutableStateOf(false) }
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

    fun handleReaderFullscreenAwtKeyEvent(event: AwtKeyEvent): Boolean {
        val action = event.desktopReaderKeyNavigationOrNull(fullscreen = isFullscreen) ?: return false
        val currentSession = latestSession
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
    DesktopReaderFullscreenKeyEffect(
        enabled = isFullscreen && externalLinkDialogUrl == null,
        onKeyPressed = { event -> handleReaderFullscreenAwtKeyEvent(event) }
    )

    LaunchedEffect(session.reader.settings.readingMode) {
        if (session.reader.settings.readingMode != ReaderReadingMode.PAGINATED) {
            completedMeasuredPaginationRequest = null
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
        delay(280L)
        val settings = latestSession.reader.settings
        if (settings.readingMode != ReaderReadingMode.PAGINATED) return@LaunchedEffect
        if (settings.layoutSignature() != request.layoutSignature) return@LaunchedEffect
        runningMeasuredPaginationRequest = request
        try {
            val reflowStartSession = latestSession
            val reflowStartRequestId = reflowStartSession.navigationRequestId
            val reflowAnchor = readerEngine.reflowAnchorFor(reflowStartSession)
            logEpubPagination(
                "reflow_start book=\"${session.reader.book.title.logPreview()}\" " +
                    "viewport=${request.viewport.widthPx}x${request.viewport.heightPx} " +
                    "spread=${settings.pageSpreadMode} font=${settings.fontSize} lineSpacing=${settings.lineSpacing} " +
                    "margins=${settings.resolvedHorizontalMargin}x${settings.resolvedVerticalMargin} " +
                    "pageWidthSetting=${settings.pageWidth} oldPages=${reflowStartSession.reader.pages.size} " +
                    "anchorPage=${reflowAnchor?.pageIndex} anchorOffsets=${reflowAnchor?.startOffset}..${reflowAnchor?.endOffset}"
            )
            val pages = measuredPaginator.paginate(
                book = session.reader.book,
                settings = settings,
                viewport = request.viewport
            )
            val layoutChanged = pages.isNotEmpty() && !latestSession.reader.pages.samePageLayoutAs(pages)
            logEpubPagination(
                "reflow_result book=\"${session.reader.book.title.logPreview()}\" pages=${pages.size} " +
                    "layoutChanged=$layoutChanged currentPages=${latestSession.reader.pages.size}"
            )
            if (layoutChanged) {
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
                completedMeasuredPaginationRequest = request
            }
        } finally {
            if (runningMeasuredPaginationRequest == request) {
                runningMeasuredPaginationRequest = null
            }
        }
    }

    val handleDesktopSelectionAction: (DesktopReaderSelectionAction, String) -> Unit = { action, text ->
        val settings = aiByokSettings.sanitized()
        when (action) {
            DesktopReaderSelectionAction.DEFINE -> {
                if (settings.areReaderAiFeaturesAvailable) onAiAction(ReaderAiFeature.DEFINE, text)
            }
            DesktopReaderSelectionAction.SPEAK -> {
                if (settings.isCloudTtsAvailable) onCloudTtsToggle(text)
            }
            DesktopReaderSelectionAction.SEARCH -> onExternalLookup(ReaderExternalLookupAction.SEARCH, text)
        }
    }
    val nativeSelectionActions = buildSet {
        val settings = aiByokSettings.sanitized()
        if (settings.areReaderAiFeaturesAvailable) add(SharedNativeReaderSelectionAction.DEFINE)
        if (externalLookupAvailable) add(SharedNativeReaderSelectionAction.SEARCH)
        if (settings.isCloudTtsAvailable) add(SharedNativeReaderSelectionAction.SPEAK)
    }
    val handleNativeSelectionAction: (SharedNativeReaderSelectionAction, String) -> Unit = { action, text ->
        when (action) {
            SharedNativeReaderSelectionAction.DEFINE ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.DEFINE, text)
            SharedNativeReaderSelectionAction.SPEAK ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.SPEAK, text)
            SharedNativeReaderSelectionAction.SEARCH ->
                handleDesktopSelectionAction(DesktopReaderSelectionAction.SEARCH, text)
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
        highlightPalette = highlightPalette,
        onHighlightPaletteChange = onHighlightPaletteChange,
        ttsReplacementPreferences = ttsReplacementPreferences,
        ttsReplacementBookId = ttsReplacementBookId,
        onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
        onPickCustomFont = onPickCustomFont,
        customFonts = customFonts,
        readerExtrasState = readerExtrasState,
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
        onOpenAiHub = onOpenAiHub,
        onAutoScrollChange = onAutoScrollChange,
        onDownloadReaderImage = onDownloadReaderImage,
        readerImagePreviewContent = { image, previewModifier ->
            DesktopEpubNativeImage(
                image = image.toDesktopPreviewSemanticImage(),
                modifier = previewModifier.clip(RoundedCornerShape(3.dp))
            )
        },
        readerTextureDataUri = readerTextureDataUri,
        readerCustomTextureIds = readerCustomTextureIds,
        onImportReaderTexture = onImportReaderTexture,
        bottomChromeExtraContent = bottomChromeExtraContent,
        useDetachedChromeLayer = useDetachedChromeLayer,
        useDetachedPanelLayer = useDetachedPanelLayer
    ) { renderPlan, onVisiblePageChanged, onHighlightSelected, onChromeActivity ->
        Surface(
            color = renderPlan.background,
            shape = RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 4.dp))
                .onSizeChanged { size ->
                    val next = ReaderViewportSpec(size.width, size.height)
                    logReaderGap(
                        "desktop_epub_reader_surface size=${size.width}x${size.height} " +
                            "mode=${session.reader.settings.readingMode} " +
                            "page=${session.reader.currentPageIndex + 1}/${session.reader.pages.size.coerceAtLeast(1)}"
                    )
                    if (next != readerViewport) {
                        logEpubPagination(
                            "viewport_changed width=${next.widthPx} height=${next.heightPx} " +
                                "previous=${readerViewport.widthPx}x${readerViewport.heightPx}"
                        )
                        readerViewport = next
                    }
                }
        ) {
            if (renderPlan is ReaderContentRenderPlan.NativePaginatedPages && !paginatedLayoutReady) {
                DesktopEpubPaginationPreparing(
                    active = runningMeasuredPaginationRequest != null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                when (renderPlan) {
                    is ReaderContentRenderPlan.WebDocument -> {
                        if (webViewRuntimeState.initialized) {
                            DesktopEpubWebView(
                                html = renderPlan.html,
                                appearanceScript = renderPlan.appearanceScript,
                                navigationTarget = renderPlan.navigationTarget,
                                highlights = renderPlan.highlights,
                                onHighlightCreated = { highlight ->
                                    onSessionChange(session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine))
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
                                onSelectionAction = handleDesktopSelectionAction,
                                onLinkClicked = handleDesktopEpubLinkClicked,
                                onVisiblePageChanged = onVisiblePageChanged,
                                onPointerActivity = onChromeActivity,
                                networkAccessEnabled = webViewNetworkAccessEnabled,
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
                        SharedNativePaginatedReader(
                            renderPlan = renderPlan,
                            readerFontFamily = renderPlan.settings.toDesktopReaderFontFamily(),
                            searchHighlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                            onVisiblePageChanged = onVisiblePageChanged,
                            enabledSelectionActions = nativeSelectionActions,
                            onCopyText = { text -> clipboardManager.setText(AnnotatedString(text)) },
                            onSelectionAction = handleNativeSelectionAction,
                            onHighlightCreated = { highlight ->
                                onSessionChange(session.reduce(ReaderAction.HighlightCreated(highlight), readerEngine))
                            },
                            onHighlightSelected = onHighlightSelected,
                            onLinkClicked = { link ->
                                handleDesktopEpubLinkClicked(link.toDesktopEpubLinkClick())
                            },
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
