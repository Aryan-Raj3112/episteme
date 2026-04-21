package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.graphics.createBitmap
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.random.Random

object PdfiumCoreProvider {
    val core: PdfiumCoreKt by lazy {
        PdfiumCoreKt(Dispatchers.Default)
    }
}

internal data class DocumentCacheItem(
    val doc: ReaderDocument,
    val pfd: ParcelFileDescriptor,
    val totalPages: Int,
    val pageAspectRatios: List<Float>,
    val flatTableOfContents: List<TocEntry>
)

internal class DocumentCache(val maxSize: Int = 3) {
    val cache = object : LruCache<String, DocumentCacheItem>(maxSize) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: DocumentCacheItem,
            newValue: DocumentCacheItem?
        ) {
            if (evicted) {
                CoroutineScope(Dispatchers.IO).launch {
                    try { oldValue.doc.close() } catch (e: Exception) { Timber.e(e) }
                    try { oldValue.pfd.close() } catch (e: Exception) { Timber.e(e) }
                }
            }
        }
    }
    fun put(key: String, item: DocumentCacheItem) { cache.put(key, item) }
    fun get(key: String): DocumentCacheItem? = cache.get(key)
    fun evictAll() { cache.evictAll() }
}

class PdfPrintDocumentAdapter(
    private val context: Context,
    private val pdfUri: Uri,
    private val fileName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(destination?.fileDescriptor).use { output ->
                        val buf = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buf).also { bytesRead = it } > 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                Timber.tag("PdfPrint").d("Print job cancelled during write")
                                callback?.onWriteCancelled()
                                return
                            }
                            output.write(buf, 0, bytesRead)
                        }
                    }
                }
            }
            Timber.tag("PdfPrint").i("PDF successfully streamed to print spooler")
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Timber.tag("PdfPrint").e(e, "Error writing PDF to print spooler")
            callback?.onWriteFailed(e.message)
        }
    }
}

internal fun generateShortId(): String {
    return Random.nextInt(1000, 9999).toString()
}

internal fun getSuggestedFilename(originalName: String?, isAnnotated: Boolean): String {
    val base = originalName?.substringBeforeLast('.') ?: "Document"
    val safeBase = base.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(50)

    val suffix = if (isAnnotated) "_annotated" else ""
    val shortId = generateShortId()

    return "${safeBase}${suffix}_${shortId}.pdf"
}

internal fun getFastFileId(context: Context, uri: Uri): String {
    var result = uri.toString()
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                val name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown"

                result = "${name}_${size}"
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to generate fast file ID")
    }
    return result
}

internal suspend fun renderPageToBitmap(doc: ReaderDocument, pageIndex: Int): Bitmap? {
    return withContext(Dispatchers.IO) {
        var page: ReaderPage? = null
        try {
            page = doc.openPage(pageIndex)
            if (page == null) return@withContext null

            val bitmapWidth = 1080
            val aspectRatio =
                page.getPageWidthPoint().toFloat() / page.getPageHeightPoint().toFloat()
            if (aspectRatio.isNaN() || aspectRatio <= 0) {
                Timber.e("Invalid aspect ratio for page $pageIndex")
                return@withContext null
            }
            val bitmapHeight = (bitmapWidth / aspectRatio).toInt()

            if (bitmapHeight <= 0) {
                Timber.e("Invalid calculated bitmap height for page $pageIndex")
                return@withContext null
            }

            val bitmap = createBitmap(bitmapWidth, bitmapHeight)
            page.renderPageBitmap(
                bitmap = bitmap,
                startX = 0,
                startY = 0,
                drawSizeX = bitmapWidth,
                drawSizeY = bitmapHeight,
                renderAnnot = true
            )
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Error rendering page $pageIndex to bitmap for summarization")
            null
        } finally {
            try {
                page?.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing page in renderPageToBitmap")
            }
        }
    }
}