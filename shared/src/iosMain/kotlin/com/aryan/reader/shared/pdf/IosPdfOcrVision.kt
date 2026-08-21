@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Create
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_Destroy
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_FillRect
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetBuffer
import com.aryan.reader.shared.pdfium.c.FPDFBitmap_GetStride
import com.aryan.reader.shared.pdfium.c.FPDF_CloseDocument
import com.aryan.reader.shared.pdfium.c.FPDF_ClosePage
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageCount
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageHeightF
import com.aryan.reader.shared.pdfium.c.FPDF_GetPageWidthF
import com.aryan.reader.shared.pdfium.c.FPDF_LoadDocument
import com.aryan.reader.shared.pdfium.c.FPDF_LoadPage
import com.aryan.reader.shared.pdfium.c.FPDF_RenderPageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRequest
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.posix.memcpy
import kotlin.math.roundToInt

/** A Vision result expressed in the same top-left normalized coordinate space as PDF overlays. */
internal data class IosPdfOcrWord(
    val text: String,
    val bounds: PdfPageBounds,
)

/** Platform implementation backed by Vision and PDFium page rendering. */
internal suspend fun recognizeIosPdfPageWords(
    path: String?,
    pageIndex: Int,
    password: String?,
    recognitionLanguages: List<String>,
): List<IosPdfOcrWord> = withContext(Dispatchers.Default) {
    val rawPath = path?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
    val resolvedPath = if (rawPath.startsWith("file://")) {
        NSURL.URLWithString(rawPath)?.path ?: rawPath.removePrefix("file://")
    } else {
        rawPath
    }
    if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return@withContext emptyList()

    IosPdfiumRuntime.mutex.withLock {
        IosPdfiumRuntime.ensureInitialized()
        val document = FPDF_LoadDocument(resolvedPath, password) ?: return@withLock emptyList()
        try {
            val pageCount = FPDF_GetPageCount(document).coerceAtLeast(0)
            if (pageIndex !in 0 until pageCount) return@withLock emptyList()
            val page = FPDF_LoadPage(document, pageIndex) ?: return@withLock emptyList()
            try {
                val pageWidth = FPDF_GetPageWidthF(page).coerceAtLeast(1f)
                val pageHeight = FPDF_GetPageHeightF(page).coerceAtLeast(1f)
                val scale = (2048f / pageHeight).coerceIn(0.5f, 3f)
                val width = (pageWidth * scale).roundToInt().coerceAtLeast(1)
                val height = (pageHeight * scale).roundToInt().coerceAtLeast(1)
                val bitmap = FPDFBitmap_Create(width, height, 1) ?: return@withLock emptyList()
                try {
                    FPDFBitmap_FillRect(bitmap, 0, 0, width, height, 0xFFFFFFFFu)
                    FPDF_RenderPageBitmap(bitmap, page, 0, 0, width, height, 0, 0)
                    val buffer = FPDFBitmap_GetBuffer(bitmap) ?: return@withLock emptyList()
                    val stride = FPDFBitmap_GetStride(bitmap).coerceAtLeast(width * 4)
                    val pixels = ByteArray(stride * height)
                    pixels.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), buffer, pixels.size.convert())
                    }
                    val image = autoreleasepool { pixels.toVisionImage(width, height, stride) }
                        ?: return@withLock emptyList()
                    autoreleasepool {
                        recognizeIosPdfWords(image, recognitionLanguages)
                    }
                } finally {
                    FPDFBitmap_Destroy(bitmap)
                }
            } finally {
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }
}

private data class IosPdfOcrCacheKey(
    val path: String,
    val pageIndex: Int,
    val passwordHash: Int,
    val languages: List<String>,
)

/** Bounded OCR cache shared by selection, TTS, and search so scanned pages are recognized once. */
internal object IosPdfOcrPageCache {
    private const val MaxEntries = 24
    private val mutex = Mutex()
    private val entries = LinkedHashMap<IosPdfOcrCacheKey, List<IosPdfOcrWord>>()

    suspend fun getOrRecognize(
        path: String?,
        pageIndex: Int,
        password: String?,
        languages: List<String>,
    ): List<IosPdfOcrWord> {
        val rawPath = path?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val key = IosPdfOcrCacheKey(rawPath, pageIndex, password?.hashCode() ?: 0, languages.distinct())
        mutex.withLock {
            entries[key]?.let {
                IosPdfOcrMetrics.cacheHits += 1
                return it
            }
        }
        val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
        val recognized = recognizeIosPdfPageWords(rawPath, pageIndex, password, languages)
        IosPdfOcrMetrics.recordRecognition(startedAt.elapsedNow().inWholeMilliseconds)
        mutex.withLock {
            entries[key] = recognized
            while (entries.size > MaxEntries) entries.remove(entries.keys.first())
        }
        return recognized
    }

    suspend fun clear() = mutex.withLock { entries.clear() }
}

/** Lightweight diagnostics for simulator/device profiling without per-frame log spam. */
internal object IosPdfOcrMetrics {
    var cacheHits: Int = 0
        private set
    var recognitionCount: Int = 0
        private set
    var lastRecognitionDurationMillis: Long = 0L
        private set
    var maxRecognitionDurationMillis: Long = 0L
        private set

    fun recordRecognition(durationMillis: Long) {
        recognitionCount += 1
        lastRecognitionDurationMillis = durationMillis.coerceAtLeast(0L)
        maxRecognitionDurationMillis = maxOf(maxRecognitionDurationMillis, lastRecognitionDurationMillis)
    }

    fun reset() {
        cacheHits = 0
        recognitionCount = 0
        lastRecognitionDurationMillis = 0L
        maxRecognitionDurationMillis = 0L
    }
}

private fun ByteArray.toVisionImage(width: Int, height: Int, stride: Int): CGImageRef? {
    val uBytes = toUByteArray()
    val data = uBytes.usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0), size.toLong())
    } ?: return null
    val provider = CGDataProviderCreateWithCFData(data) ?: return null
    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
    return CGImageCreate(
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bitsPerPixel = 32u,
        bytesPerRow = stride.toULong(),
        space = colorSpace,
        bitmapInfo = (CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value or kCGBitmapByteOrder32Little),
        provider = provider,
        decode = null,
        shouldInterpolate = true,
        intent = CGColorRenderingIntent.kCGRenderingIntentDefault,
    )
}

private fun recognizeIosPdfWords(
    image: CGImageRef,
    languages: List<String>,
): List<IosPdfOcrWord> {
    val request = VNRecognizeTextRequest(null)
    request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
    languages.takeIf { it.isNotEmpty() }?.let { request.recognitionLanguages = it }
    val handler = VNImageRequestHandler(image, emptyMap<Any?, Any>())
    runCatching { handler.performRequests(listOf<VNRequest>(request), null) }.getOrElse { return emptyList() }
    return request.results.orEmpty().mapNotNull { observation ->
        val textObservation = observation as? VNRecognizedTextObservation ?: return@mapNotNull null
        val candidate = (textObservation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val bounds = textObservation.boundingBox.useContents {
            PdfPageBounds(
                left = origin.x.toFloat().coerceIn(0f, 1f),
                top = (1.0 - origin.y - size.height).toFloat().coerceIn(0f, 1f),
                right = (origin.x + size.width).toFloat().coerceIn(0f, 1f),
                bottom = (1.0 - origin.y).toFloat().coerceIn(0f, 1f),
            )
        }
        IosPdfOcrWord(candidate, bounds)
    }
}

/**
 * The language preference is deliberately kept outside the PDF session so that a future iOS
 * settings sheet can update it without changing the reader/session contract. Vision accepts BCP
 * 47 language identifiers (for example `en-US`, `ja-JP`, and `zh-Hans`).
 */
internal object IosPdfOcrLanguagePreferences {
    private const val DefaultLanguage = "en-US"

    var languages: List<String> = defaultLanguages()

    private fun defaultLanguages(): List<String> {
        return listOf(DefaultLanguage)
    }
}

/**
 * Builds a stable UTF-16 text stream and per-character geometry from Vision words. Word order is
 * sorted top-to-bottom and left-to-right, matching PDFium's selection ordering closely enough
 * for search, selection handles, copy, and read-aloud fallback.
 */
internal fun buildIosPdfOcrTextPage(words: List<IosPdfOcrWord>): IosPdfOcrTextPage {
    if (words.isEmpty()) return IosPdfOcrTextPage("", emptyList())
    val ordered = words
        .filter { it.text.isNotBlank() && it.bounds.right > it.bounds.left && it.bounds.bottom > it.bounds.top }
        .sortedWith(compareBy<IosPdfOcrWord> { it.bounds.top }.thenBy { it.bounds.left })
    if (ordered.isEmpty()) return IosPdfOcrTextPage("", emptyList())

    val text = StringBuilder()
    val characterBounds = mutableListOf<PdfPageBounds>()
    var previous: IosPdfOcrWord? = null
    ordered.forEach { word ->
        val prior = previous
        val lineBreak = prior != null &&
            (word.bounds.top - prior.bounds.top) > maxOf(prior.bounds.bottom - prior.bounds.top, word.bounds.bottom - word.bounds.top) * 0.6f
        if (text.isNotEmpty()) {
            text.append(if (lineBreak) '\n' else ' ')
            characterBounds += prior?.bounds ?: word.bounds
        }
        val safeText = word.text.trim()
        val widthPerCharacter = (word.bounds.right - word.bounds.left) / safeText.length.coerceAtLeast(1)
        safeText.forEachIndexed { index, _ ->
            text.append(safeText[index])
            val left = word.bounds.left + widthPerCharacter * index
            characterBounds += PdfPageBounds(
                left = left,
                top = word.bounds.top,
                right = (left + widthPerCharacter).coerceAtMost(word.bounds.right),
                bottom = word.bounds.bottom,
            )
        }
        previous = word
    }
    return IosPdfOcrTextPage(text.toString(), characterBounds)
}

internal data class IosPdfOcrTextPage(
    val text: String,
    val characterBounds: List<PdfPageBounds>,
)

internal fun IosPdfOcrTextPage.boundsForRange(startIndex: Int, length: Int): List<PdfPageBounds> {
    if (length <= 0) return emptyList()
    val start = startIndex.coerceIn(0, text.length)
    val end = (start + length).coerceIn(start, text.length)
    if (start >= end) return emptyList()
    return characterBounds.subList(start, end)
        .filterIndexed { index, _ -> text.getOrNull(start + index)?.isWhitespace() != true }
        .groupBy { bounds ->
            // Quantizing the top coordinate makes one Vision line produce one overlay rectangle
            // while still allowing wrapped/rotated lines to remain separate.
            (bounds.top * 1000f).toInt()
        }
        .values
        .map { line ->
            PdfPageBounds(
                left = line.minOf { it.left },
                top = line.minOf { it.top },
                right = line.maxOf { it.right },
                bottom = line.maxOf { it.bottom },
            )
        }
        .sortedWith(compareBy<PdfPageBounds> { it.top }.thenBy { it.left })
}
