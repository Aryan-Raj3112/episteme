package com.aryan.reader.shared.ui

import android.graphics.RectF
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.pdf.PdfLinkTarget
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfTextPageSession
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

@Composable
actual fun rememberPdfTextPageSession(
    book: BookItem,
    pageIndex: Int,
    password: String?,
): PdfTextPageSession? {
    val context = LocalContext.current.applicationContext
    registerSharedAndroidMobileApplicationContext(context)
    var session by remember(book.path, pageIndex, password) {
        mutableStateOf<AndroidPdfTextPageSession?>(null)
    }
    LaunchedEffect(book.path, pageIndex, password) {
        session = runCatching {
            AndroidPdfTextPageSession.open(context, book, pageIndex, password)
        }.getOrNull()
    }
    DisposableEffect(session) {
        val owned = session
        onDispose { owned?.close() }
    }
    return session
}

private class AndroidPdfTextPageSession(
    private val descriptor: ParcelFileDescriptor,
    private val document: PdfDocumentKt,
    private val page: PdfPageKt,
    private val textPage: PdfTextPageKt,
    private val widthPoints: Int,
    private val heightPoints: Int,
) : PdfTextPageSession {
    @Volatile private var closed = false

    override val pageCharCount: Int
        get() = locked(0) { textPage.textPageCountChars() }

    override fun charAt(index: Int): Char = locked('\u0000') {
        textPage.textPageGetUnicode(index)
    }

    override fun charIndexAtNormalized(
        normX: Float,
        normY: Float,
        xTolerance: Double,
        yTolerance: Double,
    ): Int = locked(-1) {
        val point = page.mapDeviceCoordsToPage(
            0, 0, NormalizedDeviceSize, NormalizedDeviceSize, 0,
            (normX.coerceIn(0f, 1f) * NormalizedDeviceSize).roundToInt(),
            (normY.coerceIn(0f, 1f) * NormalizedDeviceSize).roundToInt(),
        )
        textPage.textPageGetCharIndexAtPos(point.x.toDouble(), point.y.toDouble(), xTolerance, yTolerance)
    }

    override fun linkAtNormalized(normX: Float, normY: Float): PdfLinkTarget? = locked(null) {
        val point = page.mapDeviceCoordsToPage(
            0, 0, NormalizedDeviceSize, NormalizedDeviceSize, 0,
            (normX.coerceIn(0f, 1f) * NormalizedDeviceSize).roundToInt(),
            (normY.coerceIn(0f, 1f) * NormalizedDeviceSize).roundToInt(),
        )
        page.getPageLinks().firstOrNull { it.bounds.contains(point.x, point.y) }?.let { link ->
            link.uri?.takeIf { it.isNotBlank() }?.let(PdfLinkTarget::ExternalUrl)
                ?: link.destPageIdx?.let(PdfLinkTarget::InternalPage)
        }
    }

    override fun linkBoundsNormalized(): List<PdfPageBounds> = locked(emptyList()) {
        page.getPageLinks().mapNotNull { normalizedBounds(it.bounds) }
    }

    override fun charBoxNormalized(index: Int): PdfPageBounds? = locked(null) {
        val rect = textPage.textPageGetCharBox(index) ?: return@locked null
        normalizedBounds(rect)
    }

    override fun rectsForRangeNormalized(startIndex: Int, length: Int): List<PdfPageBounds> =
        locked(emptyList()) {
            textPage.textPageGetRectsForRanges(intArrayOf(startIndex, length))
                .orEmpty()
                .mapNotNull { normalizedBounds(it.rect) }
        }

    override fun textForRange(startIndex: Int, length: Int): String? = locked(null) {
        textPage.textPageGetText(startIndex, length)
    }

    private suspend fun normalizedBounds(rect: RectF): PdfPageBounds? {
        if (widthPoints <= 0 || heightPoints <= 0) return null
        val mapped = page.mapRectToDevice(
            0, 0, NormalizedDeviceSize, NormalizedDeviceSize, 0, rect,
        )
        if (mapped.width() <= 0 || mapped.height() <= 0) return null
        return PdfPageBounds(
            left = mapped.left.toFloat() / NormalizedDeviceSize,
            top = mapped.top.toFloat() / NormalizedDeviceSize,
            right = mapped.right.toFloat() / NormalizedDeviceSize,
            bottom = mapped.bottom.toFloat() / NormalizedDeviceSize,
        )
    }

    private fun <T> locked(default: T, block: suspend () -> T): T {
        if (closed) return default
        return runCatching {
            runBlocking { AndroidSharedPdfiumRuntime.mutex.withLock { if (closed) default else block() } }
        }.getOrDefault(default)
    }

    override fun close() {
        if (closed) return
        runBlocking {
            AndroidSharedPdfiumRuntime.mutex.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { textPage.close() }
                runCatching { page.close() }
                runCatching { document.close() }
                runCatching { descriptor.close() }
            }
        }
    }

    companion object {
        suspend fun open(
            context: android.content.Context,
            book: BookItem,
            pageIndex: Int,
            password: String?,
        ): AndroidPdfTextPageSession = AndroidSharedPdfiumRuntime.mutex.withLock {
            val descriptor = context.openSharedPdfDescriptor(book)
            try {
                val document = AndroidSharedPdfiumRuntime.core.newDocument(descriptor, password)
                try {
                    val count = document.getPageCount()
                    require(count > 0) { "PDF has no pages" }
                    val page = requireNotNull(document.openPage(pageIndex.coerceIn(0, count - 1)))
                    try {
                        val textPage = page.openTextPage()
                        AndroidPdfTextPageSession(
                            descriptor, document, page, textPage,
                            page.getPageWidthPoint(), page.getPageHeightPoint(),
                        )
                    } catch (error: Throwable) {
                        page.close()
                        throw error
                    }
                } catch (error: Throwable) {
                    document.close()
                    throw error
                }
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }
    }
}

private const val NormalizedDeviceSize = 10_000
