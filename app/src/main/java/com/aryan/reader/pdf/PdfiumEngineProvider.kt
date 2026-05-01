package com.aryan.reader.pdf

import com.aryan.reader.shared.pdf.PdfiumBridge

internal object PdfiumEngineProvider {
    val bridge: PdfiumBridge
        get() = AndroidPdfiumBridge

    val lock: Any = this
}

private object AndroidPdfiumBridge : PdfiumBridge {
    override fun getFontSize(textPagePtr: Long, index: Int): Double =
        NativePdfiumBridge.getFontSize(textPagePtr, index)

    override fun getFontWeight(textPagePtr: Long, index: Int): Int =
        NativePdfiumBridge.getFontWeight(textPagePtr, index)

    override fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray? =
        NativePdfiumBridge.getPageFontSizes(textPagePtr, count)

    override fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray? =
        NativePdfiumBridge.getPageFontWeights(textPagePtr, count)

    override fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray? =
        NativePdfiumBridge.getPageFontFlags(textPagePtr, count)

    override fun getPageCharBoxes(textPagePtr: Long, count: Int): FloatArray? =
        NativePdfiumBridge.getPageCharBoxes(textPagePtr, count)

    override fun getAnnotCount(pagePtr: Long): Int =
        NativePdfiumBridge.getAnnotCount(pagePtr)

    override fun getAnnotSubtype(pagePtr: Long, index: Int): Int =
        NativePdfiumBridge.getAnnotSubtype(pagePtr, index)

    override fun getAnnotRect(pagePtr: Long, index: Int): FloatArray? =
        NativePdfiumBridge.getAnnotRect(pagePtr, index)

    override fun getAnnotString(pagePtr: Long, index: Int, key: String): String? =
        NativePdfiumBridge.getAnnotString(pagePtr, index, key)

    override fun getPageObjectCount(pagePtr: Long): Int =
        NativePdfiumBridge.getPageObjectCount(pagePtr)

    override fun getPageObjectType(pagePtr: Long, index: Int): Int =
        NativePdfiumBridge.getPageObjectType(pagePtr, index)

    override fun getPageObjectBoundingBox(pagePtr: Long, index: Int, outRect: FloatArray): Boolean =
        NativePdfiumBridge.getPageObjectBoundingBox(pagePtr, index, outRect)

    override fun extractImagePixels(pagePtr: Long, index: Int, dimens: IntArray): IntArray? =
        NativePdfiumBridge.extractImagePixels(pagePtr, index, dimens)

    override fun performClick(pagePtr: Long, x: Double, y: Double): Boolean =
        NativePdfiumBridge.performClick(pagePtr, x, y)

    override fun getLinkInfoAtPoint(docPtr: Long, pagePtr: Long, x: Double, y: Double): String? =
        NativePdfiumBridge.getLinkInfoAtPoint(docPtr, pagePtr, x, y)

    override fun getAnnotSubtypeAtPoint(pagePtr: Long, x: Double, y: Double): Int =
        NativePdfiumBridge.getAnnotSubtypeAtPoint(pagePtr, x, y)

    override fun getAnnotRectAtPoint(pagePtr: Long, x: Double, y: Double): FloatArray? =
        NativePdfiumBridge.getAnnotRectAtPoint(pagePtr, x, y)

    override fun checkActionSupport(): Boolean =
        NativePdfiumBridge.checkActionSupport()
}
