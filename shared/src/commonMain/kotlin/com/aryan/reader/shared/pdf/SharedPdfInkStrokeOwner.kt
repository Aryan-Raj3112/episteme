package com.aryan.reader.shared.pdf

/**
 * Resolves single-stroke ownership for free drawing.
 *
 * Like Android's single drawing state, one ink stroke runs at a time, but it
 * may start on ANY visible page. Returns [pageIndex] when the stroke list is
 * free (or already owned by [pageIndex]), otherwise keeps [currentOwnerPdfPage]
 * so the second concurrent claim is rejected and points from two pages can
 * never interleave in one stroke.
 */
fun sharedPdfResolveInkStrokeOwner(currentOwnerPdfPage: Int?, pageIndex: Int): Int {
    if (currentOwnerPdfPage != null && currentOwnerPdfPage != pageIndex) {
        return currentOwnerPdfPage
    }
    return pageIndex
}
