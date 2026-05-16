package com.aryan.reader.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

private const val PdfZoomPerfLogTag = "EpistemePdfZoomPerf"
private const val PdfLinkLogTag = "EpistemePdfLink"
private const val EpubLinkLogTag = "EpistemeEpubLink"
private const val EpubPaginationLogTag = "EpistemeEpubPagination"
private const val ReaderGapLogTag = "EpistemeReaderGap"
private const val EpubSelectionDebugLogTag = "EPUB_SELECTION_DEBUG"

internal fun logPdfSelection(message: String) {
}

internal fun logPdfZoomPerf(message: String) {
    logDesktopDiagnostic(PdfZoomPerfLogTag) { message }
}

internal fun logPdfZoomPerf(message: () -> String) {
    logDesktopDiagnostic(PdfZoomPerfLogTag, message)
}

internal fun logPdfLink(message: String) {
    logDesktopDiagnostic(PdfLinkLogTag) { message }
}

internal fun logEpubLink(message: String) {
    logDesktopDiagnostic(EpubLinkLogTag) { message }
}

internal fun logEpubPagination(message: String) {
    logDesktopDiagnostic(EpubPaginationLogTag) { message }
}

internal fun logReaderGap(message: String) {
    logDesktopDiagnostic(ReaderGapLogTag) { message }
}

internal fun logEpubSelectionDebug(message: String) {
    logDesktopDiagnostic(EpubSelectionDebugLogTag) { message }
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
