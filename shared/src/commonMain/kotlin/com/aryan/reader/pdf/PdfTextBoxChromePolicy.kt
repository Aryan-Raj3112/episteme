package com.aryan.reader.pdf

import androidx.compose.ui.geometry.Rect

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
)

fun calculateTextBoxChromeLayout(
    textBoundsPx: Rect,
    isSelected: Boolean,
    isHandleAtTop: Boolean,
    handleSizePx: Float,
    dragPillWidthPx: Float,
    dragPillHeightPx: Float,
    dragPillGapPx: Float,
): TextBoxChromeLayout {
    val halfHandlePx = handleSizePx / 2f
    val contentWidthPx = textBoundsPx.width + handleSizePx
    val contentHeightPx = textBoundsPx.height + handleSizePx
    val dragPillTrackHeightPx = if (isSelected) dragPillHeightPx + dragPillGapPx else 0f
    val containerWidthPx = maxOf(contentWidthPx, if (isSelected) dragPillWidthPx else contentWidthPx)
    val containerHeightPx = contentHeightPx + dragPillTrackHeightPx
    val contentOffsetX = (containerWidthPx - contentWidthPx) / 2f
    val contentOffsetY = if (isSelected && isHandleAtTop) dragPillTrackHeightPx else 0f
    val dragPillLeftPx = (containerWidthPx - dragPillWidthPx) / 2f
    val dragPillTopPx = if (isSelected && isHandleAtTop) {
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
    )
}
