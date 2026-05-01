package com.aryan.reader.pdf

import com.aryan.reader.shared.pdf.PdfiumAnnotationSubtype

object NativePdfiumBridge {
    init {
        System.loadLibrary("native-lib")
    }

    @JvmStatic external fun getFontSize(textPagePtr: Long, index: Int): Double
    @JvmStatic external fun getFontWeight(textPagePtr: Long, index: Int): Int

    @JvmStatic external fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray?
    @JvmStatic external fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray?
    @JvmStatic external fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray?
    @JvmStatic external fun getPageCharBoxes(textPagePtr: Long, count: Int): FloatArray?

    @JvmStatic external fun getAnnotCount(pagePtr: Long): Int
    @JvmStatic external fun getAnnotSubtype(pagePtr: Long, index: Int): Int
    @JvmStatic external fun getAnnotRect(pagePtr: Long, index: Int): FloatArray?
    @JvmStatic external fun getAnnotString(pagePtr: Long, index: Int, key: String): String?

    // Image/Object extraction
    @JvmStatic external fun getPageObjectCount(pagePtr: Long): Int
    @JvmStatic external fun getPageObjectType(pagePtr: Long, index: Int): Int
    @JvmStatic external fun getPageObjectBoundingBox(pagePtr: Long, index: Int, outRect: FloatArray): Boolean
    @JvmStatic external fun extractImagePixels(pagePtr: Long, index: Int, dimens: IntArray): IntArray?

    @JvmStatic external fun performClick(pagePtr: Long, x: Double, y: Double): Boolean
    @JvmStatic external fun getLinkInfoAtPoint(docPtr: Long, pagePtr: Long, x: Double, y: Double): String?

    @JvmStatic external fun getAnnotSubtypeAtPoint(pagePtr: Long, x: Double, y: Double): Int
    @JvmStatic external fun getAnnotRectAtPoint(pagePtr: Long, x: Double, y: Double): FloatArray?
    @JvmStatic external fun checkActionSupport(): Boolean

    const val ANNOT_TEXT = PdfiumAnnotationSubtype.TEXT
    const val ANNOT_LINK = PdfiumAnnotationSubtype.LINK
    const val ANNOT_HIGHLIGHT = PdfiumAnnotationSubtype.HIGHLIGHT
    const val ANNOT_INK = PdfiumAnnotationSubtype.INK
    const val ANNOT_WIDGET = PdfiumAnnotationSubtype.WIDGET
}
