package com.aryan.reader.pdf

import com.aryan.reader.shared.pdf.PdfiumBridge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

private object AndroidPdfiumBridge : PdfiumBridge {
    override fun getFontSize(textPagePtr: Long, index: Int): Double =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getFontSize(textPagePtr, index)
        }

    override fun getFontWeight(textPagePtr: Long, index: Int): Int =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getFontWeight(textPagePtr, index)
        }

    override fun getPageFontSizes(textPagePtr: Long, count: Int): FloatArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageFontSizes(textPagePtr, count)
        }

    override fun getPageFontWeights(textPagePtr: Long, count: Int): IntArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageFontWeights(textPagePtr, count)
        }

    override fun getPageFontFlags(textPagePtr: Long, count: Int): IntArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageFontFlags(textPagePtr, count)
        }

    override fun getPageCharBoxes(textPagePtr: Long, count: Int): FloatArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageCharBoxes(textPagePtr, count)
        }

    override fun getAnnotCount(pagePtr: Long): Int =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getAnnotCount(pagePtr)
        }

    override fun getAnnotSubtype(pagePtr: Long, index: Int): Int =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getAnnotSubtype(pagePtr, index)
        }

    override fun getAnnotRect(pagePtr: Long, index: Int): FloatArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getAnnotRect(pagePtr, index)
        }

    override fun getAnnotString(pagePtr: Long, index: Int, key: String): String? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getAnnotString(pagePtr, index, key)
        }

    override fun getPageObjectCount(pagePtr: Long): Int =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageObjectCount(pagePtr)
        }

    override fun getPageObjectType(pagePtr: Long, index: Int): Int =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageObjectType(pagePtr, index)
        }

    override fun getPageObjectBoundingBox(pagePtr: Long, index: Int, outRect: FloatArray): Boolean =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getPageObjectBoundingBox(pagePtr, index, outRect)
        }

    override fun extractImagePixels(pagePtr: Long, index: Int, dimens: IntArray): IntArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.extractImagePixels(pagePtr, index, dimens)
        }

    override fun performClick(pagePtr: Long, x: Double, y: Double): Boolean =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.performClick(pagePtr, x, y)
        }

    override fun getLinkInfoAtPoint(docPtr: Long, pagePtr: Long, x: Double, y: Double): String? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getLinkInfoAtPoint(docPtr, pagePtr, x, y)
        }

    override fun getAnnotSubtypeAtPoint(pagePtr: Long, x: Double, y: Double): Int =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getAnnotSubtypeAtPoint(pagePtr, x, y)
        }

    override fun getAnnotRectAtPoint(pagePtr: Long, x: Double, y: Double): FloatArray? =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.getAnnotRectAtPoint(pagePtr, x, y)
        }

    override fun checkActionSupport(): Boolean =
        PdfiumEngineProvider.withPdfiumBlocking {
            NativePdfiumBridge.checkActionSupport()
        }
}
