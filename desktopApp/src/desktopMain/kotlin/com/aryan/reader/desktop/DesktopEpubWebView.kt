package com.aryan.reader.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.EpubAnnotationSerializer
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import com.aryan.reader.shared.ui.ReaderContentNavigationTarget
import kotlinx.coroutines.launch

@Composable
internal fun DesktopEpubWebView(
    html: String,
    appearanceScript: String,
    highlightPaletteScript: String,
    navigationTarget: ReaderContentNavigationTarget,
    highlights: List<UserHighlight>,
    onHighlightCreated: (UserHighlight) -> Unit,
    onHighlightSelected: (String) -> Unit,
    isFullscreen: Boolean,
    onKeyboardNavigation: (DesktopReaderKeyNavigation) -> Unit,
    onSelectionAction: (DesktopReaderSelectionActionPayload) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    onPointerActivity: () -> Unit = {},
    networkAccessEnabled: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val backend = desktopEpubWebViewBackend()
    LaunchedEffect(html, networkAccessEnabled, highlights.size, navigationTarget.readingMode, backend) {
        logDesktopWebView2(
            "backend_selected backend=${backend.logName} htmlChars=${html.length} htmlHash=${html.hashCode()} " +
                "network=$networkAccessEnabled highlights=${highlights.size} navMode=${navigationTarget.readingMode}"
        )
        logDesktopReaderOpenTrace {
            "event=desktop_webview_selected backend=${backend.logName} htmlChars=${html.length} " +
                "htmlHash=${html.hashCode()} network=$networkAccessEnabled highlights=${highlights.size} " +
                "navMode=${navigationTarget.readingMode}"
        }
    }
    DesktopNativeSwtEpubWebView(
        html = html,
        appearanceScript = appearanceScript,
        highlightPaletteScript = highlightPaletteScript,
        navigationTarget = navigationTarget,
        highlights = highlights,
        onHighlightCreated = onHighlightCreated,
        onHighlightSelected = onHighlightSelected,
        isFullscreen = isFullscreen,
        onKeyboardNavigation = onKeyboardNavigation,
        onSelectionAction = onSelectionAction,
        onLinkClicked = onLinkClicked,
        onVisiblePageChanged = onVisiblePageChanged,
        onPointerActivity = onPointerActivity,
        networkAccessEnabled = networkAccessEnabled,
        backgroundColor = backgroundColor,
        modifier = modifier
    )
}

internal data class DesktopEpubBridgeHandler(
    val methodName: String,
    val onMessage: (String) -> Unit
)

@Composable
internal fun rememberDesktopEpubBridgeHandlers(
    onHighlightCreated: (UserHighlight) -> Unit,
    onHighlightSelected: (String) -> Unit,
    onKeyboardNavigation: (DesktopReaderKeyNavigation) -> Unit,
    onSelectionAction: (DesktopReaderSelectionActionPayload) -> Unit,
    onLinkClicked: (DesktopEpubLinkClick) -> Unit,
    onVisiblePageChanged: (Int, ReaderLocator?) -> Unit,
    onPointerActivity: () -> Unit
): List<DesktopEpubBridgeHandler> {
    val latestOnHighlightCreated by rememberUpdatedState(onHighlightCreated)
    val latestOnHighlightSelected by rememberUpdatedState(onHighlightSelected)
    val latestOnKeyboardNavigation by rememberUpdatedState(onKeyboardNavigation)
    val latestOnSelectionAction by rememberUpdatedState(onSelectionAction)
    val latestOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val latestOnVisiblePageChanged by rememberUpdatedState(onVisiblePageChanged)
    val latestOnPointerActivity by rememberUpdatedState(onPointerActivity)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        listOf(
            DesktopEpubBridgeHandler("readerHighlightCreated") { params ->
                logEpubHighlightFlow("bridge_received method=readerHighlightCreated params=\"${params.logPreview(900)}\"")
                val highlight = EpubAnnotationSerializer.parseHighlightJsonLenient(params)
                if (highlight == null) {
                    logEpubHighlightFlow("bridge_parse_failed method=readerHighlightCreated")
                    logEpubSelectionDebug("highlight_parse_failed params=${params.logPreview(900)}")
                } else {
                    logEpubHighlightFlow(
                        "bridge_parse_success id=${highlight.id} color=${highlight.color.id} " +
                            "chapter=${highlight.chapterIndex} offsets=${highlight.locator.startOffset}..${highlight.locator.endOffset} " +
                            "page=${highlight.locator.pageIndex} textChars=${highlight.text.length} cfi=\"${highlight.cfi.logPreview()}\""
                    )
                    logDesktopHighlightMap(
                        "bridge_highlight_created id=${highlight.id} color=${highlight.color.id} " +
                            "chapter=${highlight.chapterIndex} locatorChapter=${highlight.locator.chapterIndex} " +
                            "page=${highlight.locator.pageIndex} offsets=${highlight.locator.startOffset}..${highlight.locator.endOffset} " +
                            "block=${highlight.locator.blockIndex} char=${highlight.locator.charOffset} " +
                            "chapterId=${highlight.locator.chapterId.orEmpty().logPreview()} href=${highlight.locator.href.orEmpty().logPreview()} " +
                            "textChars=${highlight.text.length} cfi=\"${highlight.cfi.logPreview()}\""
                    )
                    scope.launch { latestOnHighlightCreated(highlight) }
                }
            },
            DesktopEpubBridgeHandler("readerHighlightClicked") { params ->
                params.readerHighlightClickOrNull()?.let { highlightClick ->
                    scope.launch { latestOnHighlightSelected(highlightClick.highlightId) }
                }
            },
            DesktopEpubBridgeHandler("readerPositionChanged") { params ->
                params.readerPositionOrNull()?.let { position ->
                    logDesktopPositionTrace(
                        "event=bridge_position_changed page=${position.pageIndex} " +
                            "locator=${position.locator.desktopPositionTraceSummary()}"
                    )
                    logDesktopHighlightMap(
                        "bridge_position_changed page=${position.pageIndex} chapter=${position.locator?.chapterIndex} " +
                            "offsets=${position.locator?.startOffset}..${position.locator?.endOffset} " +
                            "block=${position.locator?.blockIndex} char=${position.locator?.charOffset} " +
                            "chapterId=${position.locator?.chapterId.orEmpty().logPreview()} href=${position.locator?.href.orEmpty().logPreview()} " +
                            "text=\"${position.locator?.textQuote.orEmpty().logPreview(120)}\" " +
                            "cfi=\"${position.locator?.cfi.orEmpty().logPreview(160)}\""
                    )
                    logDesktopTtsStartTrace {
                        "event=bridge_position_changed page=${position.pageIndex} " +
                            "locator=${position.locator.desktopPositionTraceSummary(160)}"
                    }
                    scope.launch { latestOnVisiblePageChanged(position.pageIndex, position.locator) }
                }
            },
            DesktopEpubBridgeHandler("readerDesktopPositionTraceLog") { params ->
                logDesktopPositionTrace(params.readerSelectionDebugMessageOrNull() ?: params.logPreview(900))
            },
            DesktopEpubBridgeHandler("readerTtsStartTraceLog") { params ->
                logDesktopTtsStartTrace { params.readerSelectionDebugMessageOrNull() ?: params.logPreview(900) }
            },
            DesktopEpubBridgeHandler("readerSelectionAction") { params ->
                val selectionAction = params.readerSelectionActionOrNull()
                if (selectionAction != null) {
                    scope.launch { latestOnSelectionAction(selectionAction) }
                }
            },
            DesktopEpubBridgeHandler("readerKeyNavigation") { params ->
                params.readerKeyNavigationOrNull()?.let { action ->
                    scope.launch { latestOnKeyboardNavigation(action) }
                }
            },
            DesktopEpubBridgeHandler("readerPointerActivity") {
                scope.launch { latestOnPointerActivity() }
            },
            DesktopEpubBridgeHandler("readerTtsHighlightLog") { params ->
                logDesktopTts("epub_highlight_js ${params.logPreview(500)}")
            },
            DesktopEpubBridgeHandler("readerSelectionDebugLog") { params ->
                logEpubSelectionDebug(params.readerSelectionDebugMessageOrNull() ?: params.logPreview(900))
            },
            DesktopEpubBridgeHandler("readerHighlightFlowLog") { params ->
                logEpubHighlightFlow(params.readerSelectionDebugMessageOrNull() ?: params.logPreview(900))
            },
            DesktopEpubBridgeHandler("readerDesktopHighlightMapLog") { params ->
                logDesktopHighlightMap(params.readerSelectionDebugMessageOrNull() ?: params.logPreview(900))
            },
            DesktopEpubBridgeHandler("readerPaginationLayoutLog") { params ->
                logEpubPagination(params.readerPaginationLogMessageOrNull() ?: params.logPreview(900))
            },
            DesktopEpubBridgeHandler("readerGapLayoutLog") { params ->
                logReaderGap(params.readerPaginationLogMessageOrNull() ?: params.logPreview(900))
            },
            DesktopEpubBridgeHandler("readerLinkClicked") { params ->
                logEpubLink("bridge_message params=\"${params.logPreview()}\"")
                val link = params.readerLinkClickOrNull()
                if (link == null) {
                    logEpubLink("bridge_message_ignored reason=parse_failed")
                } else {
                    logEpubLink(
                        "bridge_message_parsed href=\"${link.href.logPreview()}\" " +
                            "chapterIndex=${link.chapterIndex} chapterHref=\"${link.chapterHref.orEmpty().logPreview()}\""
                    )
                    scope.launch { latestOnLinkClicked(link) }
                }
            }
        )
    }
}

internal val DesktopEpubKeyNavigationScript = """
    (function () {
      if (!window.readerDesktopChromeTapInstalled) {
        window.readerDesktopChromeTapInstalled = true;
        var chromeTapStart = null;
        var lastChromeTapNotifiedAt = 0;
        function notifyChromeTap() {
          if (!window.kmpJsBridge || !window.kmpJsBridge.callNative) return;
          window.kmpJsBridge.callNative('readerPointerActivity', '{}');
          lastChromeTapNotifiedAt = Date.now();
        }
        function chromeTapIgnored(target) {
          if (!target || !target.closest) return false;
          return !!target.closest(
            'a[href], button, input, textarea, select, [contenteditable="true"], #reader-selection-menu, .reader-selection-handle'
          );
        }
        function hasActiveReaderSelection() {
          var selection = window.getSelection && window.getSelection();
          return !!selection && selection.toString().trim().length > 0;
        }
        function beginChromeTap(event) {
          if (event.button !== undefined && event.button !== 0) return;
          if (chromeTapIgnored(event.target)) {
            chromeTapStart = null;
            return;
          }
          chromeTapStart = {
            pointerId: event.pointerId,
            x: event.clientX || 0,
            y: event.clientY || 0,
            at: Date.now()
          };
        }
        function finishChromeTap(event) {
          if (!chromeTapStart) return;
          if (event.pointerId !== undefined && chromeTapStart.pointerId !== undefined && event.pointerId !== chromeTapStart.pointerId) return;
          var dx = (event.clientX || 0) - chromeTapStart.x;
          var dy = (event.clientY || 0) - chromeTapStart.y;
          var elapsed = Date.now() - chromeTapStart.at;
          chromeTapStart = null;
          if ((dx * dx + dy * dy) > 64 || elapsed > 650) return;
          if (chromeTapIgnored(event.target) || hasActiveReaderSelection()) return;
          notifyChromeTap();
        }
        function maybeNotifyChromeTapFromClick(event) {
          if (Date.now() - lastChromeTapNotifiedAt < 250) return;
          if (chromeTapIgnored(event.target) || hasActiveReaderSelection()) return;
          notifyChromeTap();
        }
        document.addEventListener('pointerdown', beginChromeTap, true);
        document.addEventListener('pointerup', finishChromeTap, true);
        document.addEventListener('pointercancel', function () { chromeTapStart = null; }, true);
        document.addEventListener('click', function (event) {
          if (window.PointerEvent) {
            maybeNotifyChromeTapFromClick(event);
            return;
          }
          beginChromeTap(event);
          finishChromeTap(event);
        }, true);
      }
      if (window.readerDesktopKeyNavigationInstalled) return;
      window.readerDesktopKeyNavigationInstalled = true;
      document.addEventListener('keydown', function (event) {
        var target = event.target;
        var tag = target && target.tagName ? target.tagName.toLowerCase() : '';
        if (target && (target.isContentEditable || tag === 'input' || tag === 'textarea' || tag === 'select')) return;
        var action = null;
        if (event.ctrlKey && (event.key === 'f' || event.key === 'F')) action = 'search';
        else if (event.ctrlKey && (event.key === 'g' || event.key === 'G')) action = 'nextSearch';
        else if (event.key === 'ArrowRight' || event.key === 'PageDown') action = 'next';
        else if (event.key === 'ArrowLeft' || event.key === 'PageUp') action = 'previous';
        else if (event.key === 'Home') action = 'first';
        else if (event.key === 'End') action = 'last';
        else if (event.key === 'Escape' && window.readerDesktopFullscreen) action = 'exitFullscreen';
        if (!action || !window.kmpJsBridge || !window.kmpJsBridge.callNative) return;
        event.preventDefault();
        event.stopPropagation();
        window.kmpJsBridge.callNative('readerKeyNavigation', JSON.stringify({ action: action }));
      }, true);
    })();
""".trimIndent()
