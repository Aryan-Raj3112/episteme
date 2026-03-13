package com.aryan.reader.pdf

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

    const val ANNOT_TEXT = 1         // Sticky Note
    const val ANNOT_LINK = 2         // Link
    const val ANNOT_HIGHLIGHT = 8    // Highlight
    const val ANNOT_INK = 12         // Freehand drawing
}