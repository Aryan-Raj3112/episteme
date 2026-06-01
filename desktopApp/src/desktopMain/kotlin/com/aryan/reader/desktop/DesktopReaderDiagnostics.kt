package com.aryan.reader.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aryan.reader.shared.ReaderLocator

private const val PdfZoomPerfLogTag = "EpistemePdfZoomPerf"
private const val PdfZoomSettleLogTag = "EpistemePdfZoomSettle"
private const val PdfLinkLogTag = "EpistemePdfLink"
private const val PdfChromeTapLogTag = "EpistemePdfChromeTap"
private const val EpubLinkLogTag = "EpistemeEpubLink"
private const val EpubPaginationLogTag = "EpistemeEpubPagination"
private const val EpubCutoffLogTag = "EpistemeEpubCutoff"
private const val ReaderGapLogTag = "EpistemeReaderGap"
private const val EpubSelectionDebugLogTag = "EPUB_SELECTION_DEBUG"
private const val EpubHighlightFlowLogTag = "EpistemeEpubHighlightFlow"
private const val DesktopHighlightMapLogTag = "EpistemeDesktopHighlightMap"
private const val DesktopPositionTraceLogTag = "EpistemeDesktopPositionTrace"
private const val DesktopReaderCloseLogTag = "EpistemeDesktopReaderClose"
private const val DesktopNativeWebViewLogTag = "EpistemeNativeWebView"
private const val WebViewLayoutLogTag = "EpistemeWebViewLayout"
private const val ReaderModeSwitchLogTag = "EpistemeReaderModeSwitch"

internal fun logPdfSelection(message: String) {
}

internal fun logPdfZoomPerf(message: String) {
    logDesktopDiagnostic(PdfZoomPerfLogTag) { message }
}

internal fun logPdfZoomPerf(message: () -> String) {
    logDesktopDiagnostic(PdfZoomPerfLogTag, message)
}

internal fun logPdfZoomSettle(message: String) {
    logDesktopDiagnostic(PdfZoomSettleLogTag) { message }
}

internal fun logPdfZoomSettle(message: () -> String) {
    logDesktopDiagnostic(PdfZoomSettleLogTag, message)
}

internal fun logPdfLink(message: String) {
    logDesktopDiagnostic(PdfLinkLogTag) { message }
}

internal fun logPdfChromeTap(message: String) {
    logDesktopDiagnostic(PdfChromeTapLogTag) { message }
}

internal fun logPdfChromeTap(message: () -> String) {
    logDesktopDiagnostic(PdfChromeTapLogTag, message)
}

internal fun logEpubLink(message: String) {
    logDesktopDiagnostic(EpubLinkLogTag) { message }
}

internal fun logEpubPagination(message: String) {
    logDesktopDiagnostic(EpubPaginationLogTag) { message }
}

internal fun logEpubCutoff(message: String) {
    logDesktopDiagnostic(EpubCutoffLogTag) { message }
}

internal fun logReaderGap(message: String) {
    logDesktopDiagnostic(ReaderGapLogTag) { message }
}

internal fun logEpubSelectionDebug(message: String) {
    logDesktopDiagnostic(EpubSelectionDebugLogTag) { message }
}

internal fun logEpubHighlightFlow(message: String) {
    logDesktopDiagnostic(EpubHighlightFlowLogTag) { message }
}

internal fun logDesktopHighlightMap(message: String) {
    logDesktopDiagnostic(DesktopHighlightMapLogTag) { message }
}

internal fun logDesktopPositionTrace(message: String) {
    logDesktopDiagnostic(DesktopPositionTraceLogTag) { message }
}

internal fun logDesktopPositionTrace(message: () -> String) {
    logDesktopDiagnostic(DesktopPositionTraceLogTag, message)
}

internal fun logDesktopReaderClose(message: String) {
    logDesktopDiagnostic(DesktopReaderCloseLogTag) { message }
}

internal fun logDesktopWebView2(message: String) {
    logDesktopDiagnostic(DesktopNativeWebViewLogTag) { message }
}

internal fun logWebViewLayoutDiag(message: String) {
    logDesktopDiagnostic(WebViewLayoutLogTag) { message }
}

internal fun logReaderModeSwitch(message: String) {
    logDesktopDiagnostic(ReaderModeSwitchLogTag) { message }
}

internal fun DesktopPdfLinkTarget.formatLogTarget(): String {
    return "dest=${destPageIndex?.let { it + 1 } ?: "null"} uri=\"${uri.orEmpty().logPreview()}\""
}

internal fun Float.formatLogFloat(): String {
    return String.format("%.3f", this)
}

internal fun Offset?.formatLogOffset(): String {
    if (this == null) return "none"
    return "${x.formatLogFloat()},${y.formatLogFloat()}"
}

internal fun IntOffset?.formatLogIntOffset(): String {
    if (this == null) return "none"
    return "${this.x},${this.y}"
}

internal fun IntSize.formatLogSize(): String {
    return "${width}x${height}"
}

internal fun DesktopPdfCharHit?.formatLogHit(prefix: String): String {
    if (this == null) {
        return "${prefix}Index=null ${prefix}Source=none ${prefix}X=null ${prefix}Y=null ${prefix}Nx=null ${prefix}Ny=null"
    }
    return "${prefix}Index=$index ${prefix}Source=$source " +
        "${prefix}X=${point.x.formatLogFloat()} ${prefix}Y=${point.y.formatLogFloat()} " +
        "${prefix}Nx=${normalized.x.formatLogFloat()} ${prefix}Ny=${normalized.y.formatLogFloat()}"
}

internal fun ReaderLocator?.desktopPositionTraceSummary(maxTextLength: Int = 90): String {
    if (this == null) return "null"
    return "chapter=${chapterIndex ?: "null"} page=${pageIndex ?: "null"} " +
        "offsets=${startOffset ?: "null"}..${endOffset ?: "null"} " +
        "block=${blockIndex ?: "null"} char=${charOffset ?: "null"} " +
        "chapterId=\"${chapterId.orEmpty().logPreview(80)}\" href=\"${href.orEmpty().logPreview(120)}\" " +
        "cfi=\"${cfi.orEmpty().logPreview(180)}\" text=\"${textQuote.orEmpty().logPreview(maxTextLength)}\""
}
