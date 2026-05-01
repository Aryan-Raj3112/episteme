package com.aryan.reader.shared.pdf

/**
 * Platform-neutral surface for Pdfium functions that are not exposed by the
 * higher-level document/page API. Platform implementations can back this with
 * Android JNI, Windows Pdfium binaries, or another native binding.
 */
interface PdfiumBridge {
    fun getFontSize(textPagePtr: Long, index: Int): Double
    fun getFontWeight(textPagePtr: Long, index: Int): Int

    fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray?
    fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray?
    fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray?
    fun getPageCharBoxes(textPagePtr: Long, count: Int): FloatArray?

    fun getAnnotCount(pagePtr: Long): Int
    fun getAnnotSubtype(pagePtr: Long, index: Int): Int
    fun getAnnotRect(pagePtr: Long, index: Int): FloatArray?
    fun getAnnotString(pagePtr: Long, index: Int, key: String): String?

    fun getPageObjectCount(pagePtr: Long): Int
    fun getPageObjectType(pagePtr: Long, index: Int): Int
    fun getPageObjectBoundingBox(pagePtr: Long, index: Int, outRect: FloatArray): Boolean
    fun extractImagePixels(pagePtr: Long, index: Int, dimens: IntArray): IntArray?

    fun performClick(pagePtr: Long, x: Double, y: Double): Boolean
    fun getLinkInfoAtPoint(docPtr: Long, pagePtr: Long, x: Double, y: Double): String?

    fun getAnnotSubtypeAtPoint(pagePtr: Long, x: Double, y: Double): Int
    fun getAnnotRectAtPoint(pagePtr: Long, x: Double, y: Double): FloatArray?
    fun checkActionSupport(): Boolean
}

object PdfiumAnnotationSubtype {
    const val TEXT = 1
    const val LINK = 2
    const val HIGHLIGHT = 8
    const val INK = 12
    const val WIDGET = 19
}

object NoOpPdfiumBridge : PdfiumBridge {
    override fun getFontSize(textPagePtr: Long, index: Int): Double = 0.0
    override fun getFontWeight(textPagePtr: Long, index: Int): Int = 0
    override fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray? = null
    override fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray? = null
    override fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray? = null
    override fun getPageCharBoxes(textPagePtr: Long, count: Int): FloatArray? = null
    override fun getAnnotCount(pagePtr: Long): Int = 0
    override fun getAnnotSubtype(pagePtr: Long, index: Int): Int = 0
    override fun getAnnotRect(pagePtr: Long, index: Int): FloatArray? = null
    override fun getAnnotString(pagePtr: Long, index: Int, key: String): String? = null
    override fun getPageObjectCount(pagePtr: Long): Int = 0
    override fun getPageObjectType(pagePtr: Long, index: Int): Int = 0
    override fun getPageObjectBoundingBox(pagePtr: Long, index: Int, outRect: FloatArray): Boolean = false
    override fun extractImagePixels(pagePtr: Long, index: Int, dimens: IntArray): IntArray? = null
    override fun performClick(pagePtr: Long, x: Double, y: Double): Boolean = false
    override fun getLinkInfoAtPoint(docPtr: Long, pagePtr: Long, x: Double, y: Double): String? = null
    override fun getAnnotSubtypeAtPoint(pagePtr: Long, x: Double, y: Double): Int = -1
    override fun getAnnotRectAtPoint(pagePtr: Long, x: Double, y: Double): FloatArray? = null
    override fun checkActionSupport(): Boolean = false
}
