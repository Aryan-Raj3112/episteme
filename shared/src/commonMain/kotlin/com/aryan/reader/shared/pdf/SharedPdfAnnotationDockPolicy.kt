package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.DockLocation
import kotlin.math.roundToInt

/**
 * Android-parity policy for the PDF annotation dock.
 *
 * Benchmark: `app/.../pdf/AnnotationDock.kt` + `PdfViewerScreen.kt` dock chrome.
 * - Full bar when sticky (TOP/BOTTOM, not dragging) or when not minimized;
 *   only a floating + minimized dock collapses to the 48dp circle.
 * - Minimized dims/disables tools and stops drawing
 *   (`isDrawingActive = isEditMode && !isDockMinimized`).
 * - Tool-settings popup opens on the opposite side of the dock (dock in bottom
 *   half -> popup above, else below).
 * - Pen/highlighter groups recall the last tool in that group, matching
 *   AnnotationDock's onClick resolution.
 */

val SharedPdfAnnotationPenTools: Set<PdfInkTool> = setOf(
    PdfInkTool.PEN,
    PdfInkTool.FOUNTAIN_PEN,
    PdfInkTool.PENCIL,
)

val SharedPdfAnnotationHighlighterTools: Set<PdfInkTool> = setOf(
    PdfInkTool.HIGHLIGHTER,
    PdfInkTool.HIGHLIGHTER_ROUND,
)

fun isSharedPdfAnnotationDockFullBar(
    isSticky: Boolean,
    isMinimized: Boolean,
): Boolean = isSticky || !isMinimized

fun isSharedPdfAnnotationDockSticky(
    dockLocation: DockLocation,
    isDragging: Boolean,
): Boolean = (dockLocation == DockLocation.TOP || dockLocation == DockLocation.BOTTOM) && !isDragging

fun isSharedPdfAnnotationDrawingActive(
    selectedTool: PdfInkTool,
    isDockMinimized: Boolean,
): Boolean = selectedTool != PdfInkTool.NONE && selectedTool != PdfInkTool.TEXT &&
    selectedTool != PdfInkTool.SELECT && !isDockMinimized

fun isSharedPdfTextDockDrawingActive(
    selectedTool: PdfInkTool,
): Boolean = selectedTool == PdfInkTool.TEXT

fun isSharedPdfAnnotationPenActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool in SharedPdfAnnotationPenTools

fun isSharedPdfAnnotationHighlighterActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool in SharedPdfAnnotationHighlighterTools

fun isSharedPdfAnnotationTextActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool == PdfInkTool.TEXT

fun isSharedPdfAnnotationEraserActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool == PdfInkTool.ERASER

fun isSharedPdfAnnotationSelectActive(
    selectedTool: PdfInkTool,
    isMinimized: Boolean,
): Boolean = !isMinimized && selectedTool == PdfInkTool.SELECT

/**
 * Resolves which tool a dock tap should activate, mirroring
 * `AnnotationDock.kt` pen/highlighter group recall:
 * tapping the pen icon when another pen is active keeps it (caller toggles
 * settings); tapping it from another group recalls the last pen tool.
 */
fun resolveSharedPdfAnnotationDockToolClick(
    selectedTool: PdfInkTool,
    clickedGroup: PdfInkTool,
    lastPenTool: PdfInkTool,
    lastHighlighterTool: PdfInkTool,
): PdfInkTool = when (clickedGroup) {
    PdfInkTool.TEXT, PdfInkTool.ERASER -> clickedGroup
    in SharedPdfAnnotationPenTools -> if (selectedTool in SharedPdfAnnotationPenTools) {
        selectedTool
    } else {
        lastPenTool.takeIf { it in SharedPdfAnnotationPenTools } ?: PdfInkTool.PEN
    }
    in SharedPdfAnnotationHighlighterTools -> if (selectedTool in SharedPdfAnnotationHighlighterTools) {
        selectedTool
    } else {
        lastHighlighterTool.takeIf { it in SharedPdfAnnotationHighlighterTools } ?: PdfInkTool.HIGHLIGHTER
    }
    else -> clickedGroup
}

/**
 * Mirrors `PdfViewerScreen.kt` popup placement: dock in the bottom half ->
 * popup above the dock, else below it.
 */
fun isSharedPdfAnnotationDockInBottomHalf(
    dockTopYPx: Float,
    dockHeightPx: Float,
    boxHeightPx: Float,
): Boolean {
    if (boxHeightPx <= 0f) return true
    val dockCenterY = dockTopYPx + (dockHeightPx / 2f)
    return dockCenterY > (boxHeightPx / 2f)
}

fun sharedPdfAnnotationDockTopYPx(
    dockLocation: DockLocation,
    dockOffsetYPx: Float,
    boxHeightPx: Float,
    dockHeightPx: Float,
): Float = when (dockLocation) {
    DockLocation.TOP -> 0f
    DockLocation.BOTTOM -> boxHeightPx - dockHeightPx
    DockLocation.FLOATING -> dockOffsetYPx
}

/**
 * Android parity (`ReaderPopupSizing.readerModalMaxHeightDp` as called from
 * `ToolSettingsPopup.kt`): caps the tool-settings popup height to a fraction
 * of the available height so it scrolls instead of overflowing on small
 * screens. Pure math, dp-in/dp-out.
 */
fun sharedPdfPopupMaxHeightDp(
    availableHeightDp: Int,
    fraction: Float = 0.8f,
    verticalMarginDp: Int = 64,
    preferredMinHeightDp: Int = 240,
): Int {
    val usableHeight = (availableHeightDp - verticalMarginDp).coerceAtLeast(1)
    val proportionalHeight = (availableHeightDp * fraction).roundToInt().coerceAtLeast(1)
    val cappedHeight = minOf(usableHeight, proportionalHeight)
    return if (usableHeight >= preferredMinHeightDp) {
        cappedHeight.coerceAtLeast(preferredMinHeightDp)
    } else {
        usableHeight
    }
}
