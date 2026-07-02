package com.aryan.reader.pdf

import com.aryan.reader.shared.pdf.NoOpPdfiumBridge
import com.aryan.reader.shared.pdf.PdfiumBridge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal object PdfiumEngineProvider {
    private val pdfiumMutex = Mutex()

    val bridge: PdfiumBridge
        get() = AndroidPdfiumBridge

    val lock: Any = this

    suspend fun <T> withPdfium(block: suspend () -> T): T =
        pdfiumMutex.withLock { block() }

    fun <T> withPdfiumBlocking(block: () -> T): T =
        runBlocking {
            pdfiumMutex.withLock { block() }
        }
}

private inline fun <T> withNativePdfiumOrDefault(defaultValue: T, block: () -> T): T {
    if (!NativePdfiumBridge.ensureLoaded()) return defaultValue
    return try {
        block()
    } catch (e: UnsatisfiedLinkError) {
        Timber.tag("NativePdfiumBridge").e(e, "Native PDF bridge call failed")
        defaultValue
    }
}

private object AndroidPdfiumBridge : PdfiumBridge {
    override fun getFontSize(textPagePtr: Long, index: Int): Double =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getFontSize(textPagePtr, index)) { NativePdfiumBridge.getFontSize(textPagePtr, index) }

    override fun getFontWeight(textPagePtr: Long, index: Int): Int =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getFontWeight(textPagePtr, index)) { NativePdfiumBridge.getFontWeight(textPagePtr, index) }

    override fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageFontSizes(textPagePtr, count)) { NativePdfiumBridge.getPageFontSizes(textPagePtr, count) }

    override fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageFontWeights(textPagePtr, count)) { NativePdfiumBridge.getPageFontWeights(textPagePtr, count) }

    override fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageFontFlags(textPagePtr, count)) { NativePdfiumBridge.getPageFontFlags(textPagePtr, count) }

    override fun getPageCharBoxes(textPagePtr: Long, count: Int): FloatArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageCharBoxes(textPagePtr, count)) { NativePdfiumBridge.getPageCharBoxes(textPagePtr, count) }

    override fun getAnnotCount(pagePtr: Long): Int =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getAnnotCount(pagePtr)) { NativePdfiumBridge.getAnnotCount(pagePtr) }

    override fun getAnnotSubtype(pagePtr: Long, index: Int): Int =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getAnnotSubtype(pagePtr, index)) { NativePdfiumBridge.getAnnotSubtype(pagePtr, index) }

    override fun getAnnotRect(pagePtr: Long, index: Int): FloatArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getAnnotRect(pagePtr, index)) { NativePdfiumBridge.getAnnotRect(pagePtr, index) }

    override fun getAnnotString(pagePtr: Long, index: Int, key: String): String? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getAnnotString(pagePtr, index, key)) { NativePdfiumBridge.getAnnotString(pagePtr, index, key) }

    override fun getPageObjectCount(pagePtr: Long): Int =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageObjectCount(pagePtr)) { NativePdfiumBridge.getPageObjectCount(pagePtr) }

    override fun getPageObjectType(pagePtr: Long, index: Int): Int =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageObjectType(pagePtr, index)) { NativePdfiumBridge.getPageObjectType(pagePtr, index) }

    override fun getPageObjectBoundingBox(pagePtr: Long, index: Int, outRect: FloatArray): Boolean =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getPageObjectBoundingBox(pagePtr, index, outRect)) { NativePdfiumBridge.getPageObjectBoundingBox(pagePtr, index, outRect) }

    override fun extractImagePixels(pagePtr: Long, index: Int, dimens: IntArray): IntArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.extractImagePixels(pagePtr, index, dimens)) { NativePdfiumBridge.extractImagePixels(pagePtr, index, dimens) }

    override fun performClick(pagePtr: Long, x: Double, y: Double): Boolean =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.performClick(pagePtr, x, y)) { NativePdfiumBridge.performClick(pagePtr, x, y) }

    override fun getLinkInfoAtPoint(docPtr: Long, pagePtr: Long, x: Double, y: Double): String? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getLinkInfoAtPoint(docPtr, pagePtr, x, y)) { NativePdfiumBridge.getLinkInfoAtPoint(docPtr, pagePtr, x, y) }

    override fun getAnnotSubtypeAtPoint(pagePtr: Long, x: Double, y: Double): Int =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getAnnotSubtypeAtPoint(pagePtr, x, y)) { NativePdfiumBridge.getAnnotSubtypeAtPoint(pagePtr, x, y) }

    override fun getAnnotRectAtPoint(pagePtr: Long, x: Double, y: Double): FloatArray? =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.getAnnotRectAtPoint(pagePtr, x, y)) { NativePdfiumBridge.getAnnotRectAtPoint(pagePtr, x, y) }

    override fun checkActionSupport(): Boolean =
        withNativePdfiumOrDefault(NoOpPdfiumBridge.checkActionSupport()) { NativePdfiumBridge.checkActionSupport() }
}
