package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect

/**
 * Outer handle size for the text-box content frame (Android benchmark
 * ResizableTextBox): the frame pads half a handle around the bounds, and the
 * field pads [SharedPdfTextBoxInnerPaddingDp] inside it. The committed
 * (deselected) rendering must use this exact frame, otherwise the text visibly
 * jumps when selection toggles.
 */
const val SharedPdfTextBoxHandleSizeDp = 10f

/** Inner field padding inside the padded content frame (same benchmark). */
const val SharedPdfTextBoxInnerPaddingDp = 8f

data class TextBoxChromeLayout(
    val containerWidthPx: Float,
    val containerHeightPx: Float,
    val contentWidthPx: Float,
    val contentHeightPx: Float,
    val contentOffsetX: Float,
    val contentOffsetY: Float,
    val outerTranslationX: Float,
    val outerTranslationY: Float,
    val dragPillLeftPx: Float,
    val dragPillTopPx: Float,
    /**
     * Reserved track for the compact action menu on the OPPOSITE end of the
     * box from the drag pill, so the two never share touch space.
     */
    val actionMenuLeftPx: Float = 0f,
    val actionMenuTopPx: Float = 0f,
)

fun calculateTextBoxChromeLayout(
    textBoundsPx: Rect,
    isSelected: Boolean,
    isHandleAtTop: Boolean,
    handleSizePx: Float,
    dragPillWidthPx: Float,
    dragPillHeightPx: Float,
    dragPillGapPx: Float,
    hasActionMenu: Boolean = false,
    actionMenuWidthPx: Float = 0f,
    actionMenuHeightPx: Float = 0f,
): TextBoxChromeLayout {
    val halfHandlePx = handleSizePx / 2f
    val contentWidthPx = textBoundsPx.width + handleSizePx
    val contentHeightPx = textBoundsPx.height + handleSizePx
    // The drag pill takes one end of the box; the action menu takes the
    // opposite end. Each reserves its own track so neither overlays the
    // other's touch area.
    val dragPillTrackHeightPx = if (isSelected) dragPillHeightPx + dragPillGapPx else 0f
    val actionMenuTrackHeightPx = if (isSelected && hasActionMenu) actionMenuHeightPx + dragPillGapPx else 0f
    val pillAtTop = isSelected && isHandleAtTop
    val menuAtTop = isSelected && hasActionMenu && !isHandleAtTop
    val containerWidthPx = maxOf(
        contentWidthPx,
        if (isSelected) maxOf(dragPillWidthPx, actionMenuWidthPx) else contentWidthPx,
    )
    val containerHeightPx = contentHeightPx + dragPillTrackHeightPx + actionMenuTrackHeightPx
    // Content shifts down only for tracks that sit ABOVE it.
    val contentOffsetY = (if (pillAtTop) dragPillTrackHeightPx else 0f) +
        (if (menuAtTop) actionMenuTrackHeightPx else 0f)
    val contentOffsetX = (containerWidthPx - contentWidthPx) / 2f
    val dragPillLeftPx = (containerWidthPx - dragPillWidthPx) / 2f
    val actionMenuLeftPx = (containerWidthPx - actionMenuWidthPx) / 2f
    val actionMenuTopPx = if (menuAtTop) {
        0f
    } else {
        containerHeightPx - actionMenuHeightPx
    }
    val dragPillTopPx = if (pillAtTop) {
        0f
    } else {
        containerHeightPx - dragPillHeightPx
    }

    return TextBoxChromeLayout(
        containerWidthPx = containerWidthPx,
        containerHeightPx = containerHeightPx,
        contentWidthPx = contentWidthPx,
        contentHeightPx = contentHeightPx,
        contentOffsetX = contentOffsetX,
        contentOffsetY = contentOffsetY,
        outerTranslationX = textBoundsPx.left - halfHandlePx - contentOffsetX,
        outerTranslationY = textBoundsPx.top - halfHandlePx - contentOffsetY,
        dragPillLeftPx = dragPillLeftPx,
        dragPillTopPx = dragPillTopPx,
        actionMenuLeftPx = actionMenuLeftPx,
        actionMenuTopPx = actionMenuTopPx,
    )
}
