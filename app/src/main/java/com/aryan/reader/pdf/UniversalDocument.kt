// UniversalDocument.kt
package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import com.aryan.reader.FileType
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import timber.log.Timber
import java.util.UUID
import java.util.zip.ZipFile

interface ReaderDocument : AutoCloseable {
    suspend fun getPageCount(): Int
    suspend fun openPage(pageIndex: Int): ReaderPage?
    suspend fun getTableOfContents(): List<Bookmark>
}

interface ReaderPage : AutoCloseable {
    suspend fun getPageWidthPoint(): Int
    suspend fun getPageHeightPoint(): Int
    suspend fun getPageRotation(): Int
    suspend fun renderPageBitmap(bitmap: Bitmap, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int, renderAnnot: Boolean)
    suspend fun mapRectToDevice(startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, coords: RectF): Rect
    suspend fun mapDeviceCoordsToPage(startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int): PointF
    suspend fun openTextPage(): ReaderTextPage
    suspend fun getLinks(): List<ReaderLink>
    fun getNativePointer(): Long
}

interface ReaderTextPage : AutoCloseable {
    suspend fun textPageCountChars(): Int
    suspend fun textPageGetText(startIndex: Int, count: Int): String?
    suspend fun textPageGetRectsForRanges(ranges: IntArray): List<ReaderTextRect>?
    suspend fun textPageGetCharIndexAtPos(x: Double, y: Double, xTolerance: Double, yTolerance: Double): Int
    suspend fun textPageGetCharBox(index: Int): RectF?
    suspend fun textPageGetUnicode(index: Int): Int
    suspend fun loadWebLink(): ReaderWebLinks?
}

data class ReaderLink(val uri: String?, val destPageIdx: Int?, val bounds: RectF)
data class ReaderTextRect(val rect: RectF)

interface ReaderWebLinks : AutoCloseable {
    suspend fun countWebLinks(): Int
    suspend fun getURL(linkIndex: Int, maxLength: Int): String?
    suspend fun countRects(linkIndex: Int): Int
    suspend fun getRect(linkIndex: Int, rectIndex: Int): RectF
}

object DocumentFactory {
    suspend fun loadDocument(context: Context, uri: Uri, type: FileType, password: String?, pdfiumCore: PdfiumCoreKt): ReaderDocument {
        return if (type == FileType.CBZ || type == FileType.CBR || type == FileType.CB7) {
            val cacheFile = File(context.cacheDir, "temp_comic_${System.currentTimeMillis()}.${type.name.lowercase()}")
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            ArchiveDocumentWrapper(cacheFile)
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw Exception("Failed to open PDF")
            PdfDocumentWrapper(pdfiumCore.newDocument(pfd, password))
        }
    }
}

// ================= PDF IMPLEMENTATION =================

class PdfDocumentWrapper(val pdfDocument: PdfDocumentKt) : ReaderDocument {
    override suspend fun getPageCount() = pdfDocument.getPageCount()
    override suspend fun openPage(pageIndex: Int): ReaderPage? {
        val page = pdfDocument.openPage(pageIndex) ?: return null
        return PdfPageWrapper(page)
    }
    override suspend fun getTableOfContents() = pdfDocument.getFixedTableOfContents()
    override fun close() { pdfDocument.close() }
}

class PdfPageWrapper(val pdfPage: PdfPageKt) : ReaderPage {
    override suspend fun getPageWidthPoint() = pdfPage.getPageWidthPoint()
    override suspend fun getPageHeightPoint() = pdfPage.getPageHeightPoint()
    override suspend fun getPageRotation() = pdfPage.getPageRotation()

    override suspend fun renderPageBitmap(bitmap: Bitmap, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int, renderAnnot: Boolean) {
        pdfPage.renderPageBitmap(bitmap, startX, startY, drawSizeX, drawSizeY, renderAnnot)
    }

    override suspend fun mapRectToDevice(startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, coords: RectF) =
        pdfPage.mapRectToDevice(startX, startY, sizeX, sizeY, rotate, coords)

    override suspend fun mapDeviceCoordsToPage(startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int) =
        pdfPage.mapDeviceCoordsToPage(startX, startY, sizeX, sizeY, rotate, deviceX, deviceY)

    override suspend fun openTextPage(): ReaderTextPage = PdfTextPageWrapper(pdfPage.openTextPage())

    override suspend fun getLinks(): List<ReaderLink> {
        return pdfPage.getPageLinks().map { ReaderLink(it.uri, it.destPageIdx, it.bounds) }
    }

    override fun getNativePointer(): Long {
        return try {
            val field = pdfPage.javaClass.getDeclaredField("mNativePagePtr")
            field.isAccessible = true
            field.get(pdfPage) as? Long ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun close() { pdfPage.close() }
}

class PdfTextPageWrapper(private val textPage: PdfTextPageKt) : ReaderTextPage {
    override suspend fun textPageCountChars() = textPage.textPageCountChars()
    override suspend fun textPageGetText(startIndex: Int, count: Int) = textPage.textPageGetText(startIndex, count)
    override suspend fun textPageGetRectsForRanges(ranges: IntArray) = textPage.textPageGetRectsForRanges(ranges)?.map { ReaderTextRect(it.rect) }
    override suspend fun textPageGetCharIndexAtPos(x: Double, y: Double, xTolerance: Double, yTolerance: Double) = textPage.textPageGetCharIndexAtPos(x, y, xTolerance, yTolerance)
    override suspend fun textPageGetCharBox(index: Int) = textPage.textPageGetCharBox(index)
    override suspend fun textPageGetUnicode(index: Int): Int {
        return textPage.textPageGetUnicode(index).code
    }
    override suspend fun loadWebLink(): ReaderWebLinks? {
        val links = textPage.loadWebLink() ?: return null
        return object : ReaderWebLinks {
            override suspend fun countWebLinks() = links.countWebLinks()
            override suspend fun getURL(linkIndex: Int, maxLength: Int) = links.getURL(linkIndex, maxLength)
            override suspend fun countRects(linkIndex: Int) = links.countRects(linkIndex)
            override suspend fun getRect(linkIndex: Int, rectIndex: Int) = links.getRect(linkIndex, rectIndex)
            override fun close() { links.close() }
        }
    }
    override fun close() { textPage.close() }
}

// ================= CBZ, CBR, CB7 IMPLEMENTATION =================

class DummyTextPage : ReaderTextPage {
    override suspend fun textPageCountChars() = 0
    override suspend fun textPageGetText(startIndex: Int, count: Int) = null
    override suspend fun textPageGetRectsForRanges(ranges: IntArray) = null
    override suspend fun textPageGetCharIndexAtPos(x: Double, y: Double, xTolerance: Double, yTolerance: Double) = -1
    override suspend fun textPageGetCharBox(index: Int) = null
    override suspend fun textPageGetUnicode(index: Int) = 0
    override suspend fun loadWebLink() = null
    override fun close() {}
}

class ArchiveDocumentWrapper(private val file: File) : ReaderDocument {
    private val imageEntries = mutableListOf<String>()
    private var zipFile: ZipFile? = null
    private var extractedDir: File? = null

    init {
        // Try reading as ZIP first for instant O(1) random access (Handles .cbz efficiently)
        try {
            val zf = ZipFile(file)
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp)$", RegexOption.IGNORE_CASE))) {
                    imageEntries.add(entry.name)
                }
            }
            if (imageEntries.isNotEmpty()) {
                zipFile = zf
                imageEntries.sort()
            } else {
                zf.close()
            }
        } catch (_: Exception) {
            zipFile = null
        }

        if (zipFile == null) {
            imageEntries.clear()
            extractedDir = File(file.parentFile, "extracted_${file.name}_${System.currentTimeMillis()}")
            extractedDir?.mkdirs()

            var archive = 0L
            try {
                archive = Archive.readNew()
                Archive.readSupportFilterAll(archive)
                Archive.readSupportFormatAll(archive)
                Archive.readOpenFileName(archive, file.absolutePath.toByteArray(), 10240)

                val tempEntries = mutableListOf<Pair<String, File>>()

                while (true) {
                    val entry = try {
                        Archive.readNextHeader(archive)
                    } catch (e: ArchiveException) {
                        if (e.code == Archive.ERRNO_EOF) break
                        throw e
                    }
                    if (entry == 0L) break

                    val path = ArchiveEntry.pathnameUtf8(entry)
                    if (path != null && path.matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp)$", RegexOption.IGNORE_CASE))) {
                        val extractedFile = File(extractedDir, UUID.randomUUID().toString() + ".img")
                        tempEntries.add(Pair(path, extractedFile))

                        var pfd: android.os.ParcelFileDescriptor? = null
                        try {
                            // Extract seamlessly using fd to avoid ByteBuffer's state sync bug
                            pfd = android.os.ParcelFileDescriptor.open(extractedFile, android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE)
                            Archive.readDataIntoFd(archive, pfd.fd)
                        } finally {
                            pfd?.close()
                        }
                    } else {
                        Archive.readDataSkip(archive)
                    }
                }

                tempEntries.sortBy { it.first } // Natural sorting order based on the filename inside the archive
                tempEntries.forEach { imageEntries.add(it.second.absolutePath) }

            } catch (e: Exception) {
                Timber.e(e, "Failed to extract archive entries")
            } finally {
                if (archive != 0L) Archive.readFree(archive)
            }
        }
    }

    override suspend fun getPageCount() = imageEntries.size

    override suspend fun openPage(pageIndex: Int): ReaderPage? = withContext(Dispatchers.IO) {
        if (pageIndex !in imageEntries.indices) return@withContext null
        val targetPath = imageEntries[pageIndex]

        var imageBytes: ByteArray? = null

        if (zipFile != null) {
            try {
                val entry = zipFile!!.getEntry(targetPath)
                if (entry != null) {
                    zipFile!!.getInputStream(entry).use { imageBytes = it.readBytes() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract page from ZIP")
            }
        } else {
            try {
                val extractedFile = File(targetPath)
                if (extractedFile.exists()) {
                    imageBytes = extractedFile.readBytes()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read extracted page")
            }
        }

        if (imageBytes != null && imageBytes!!.isNotEmpty()) ArchivePageWrapper(imageBytes!!) else null
    }

    override suspend fun getTableOfContents() = emptyList<Bookmark>()

    override fun close() {
        try { zipFile?.close() } catch (_: Exception) {}
        try { extractedDir?.deleteRecursively() } catch (_: Exception) {}
        try { file.delete() } catch (_: Exception) {}
    }
}

class ArchivePageWrapper(imageBytes: ByteArray) : ReaderPage {
    private val decoder = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size, false)
        }
    } catch (_: Exception) {
        null
    }

    private val originalWidth = decoder?.width ?: 1
    private val originalHeight = decoder?.height ?: 1

    override suspend fun getPageWidthPoint() = originalWidth
    override suspend fun getPageHeightPoint() = originalHeight
    override suspend fun getPageRotation() = 0

    override suspend fun renderPageBitmap(bitmap: Bitmap, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int, renderAnnot: Boolean) {
        if (decoder == null || decoder.isRecycled) return
        val scaleX = drawSizeX.toFloat() / originalWidth
        val scaleY = drawSizeY.toFloat() / originalHeight

        val srcLeft = (-startX / scaleX).toInt().coerceAtLeast(0)
        val srcTop = (-startY / scaleY).toInt().coerceAtLeast(0)
        val srcRight = (srcLeft + (bitmap.width / scaleX).toInt()).coerceAtMost(originalWidth)
        val srcBottom = (srcTop + (bitmap.height / scaleY).toInt()).coerceAtMost(originalHeight)

        val rect = Rect(srcLeft, srcTop, srcRight, srcBottom)
        if (rect.width() <= 0 || rect.height() <= 0) return

        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val region = try {
            decoder.decodeRegion(rect, options)
        } catch (_: Exception) {
            null
        }

        if (region != null) {
            val canvas = Canvas(bitmap)
            val destRect = Rect(0, 0, bitmap.width, bitmap.height)
            canvas.drawBitmap(region, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            region.recycle()
        }
    }

    override suspend fun mapRectToDevice(startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, coords: RectF): Rect {
        val scaleX = sizeX.toFloat() / originalWidth
        val scaleY = sizeY.toFloat() / originalHeight
        return Rect(
            (startX + coords.left * scaleX).toInt(),
            (startY + coords.top * scaleY).toInt(),
            (startX + coords.right * scaleX).toInt(),
            (startY + coords.bottom * scaleY).toInt()
        )
    }

    override suspend fun mapDeviceCoordsToPage(startX: Int, startY: Int, sizeX: Int, sizeY: Int, rotate: Int, deviceX: Int, deviceY: Int): PointF {
        val scaleX = sizeX.toFloat() / originalWidth
        val scaleY = sizeY.toFloat() / originalHeight
        return PointF((deviceX - startX) / scaleX, (deviceY - startY) / scaleY)
    }

    override suspend fun openTextPage() = DummyTextPage()
    override suspend fun getLinks() = emptyList<ReaderLink>()
    override fun getNativePointer() = 0L

    override fun close() {
        decoder?.recycle()
    }
}