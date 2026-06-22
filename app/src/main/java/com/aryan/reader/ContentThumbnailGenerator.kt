package com.aryan.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Xml
import androidx.core.graphics.createBitmap
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.epub.MobiParser
import com.aryan.reader.epub.OdtParser
import com.aryan.reader.pdf.ArchiveDocumentWrapper
import com.aryan.reader.pdf.PdfCoverGenerator
import com.aryan.reader.pptx.PptxCoverGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.ceil

internal class ContentThumbnailGenerator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generate(item: RecentFileItem, targetHeight: Int = 800): Bitmap? = withContext(Dispatchers.IO) {
        val uri = item.uriString?.let(Uri::parse) ?: return@withContext null
        when (item.type) {
            FileType.PDF -> PdfCoverGenerator(appContext).generateCover(uri, targetHeight)
            FileType.PPTX -> PptxCoverGenerator(appContext).generateCover(uri, targetHeight)
            FileType.CBZ,
            FileType.CBR,
            FileType.CB7,
            FileType.CBT -> generateComicCover(uri, item.type, targetHeight)
            FileType.ODT -> generateOdtThumbnail(uri, item, isFlat = false, targetHeight = targetHeight)
            FileType.FODT -> generateOdtThumbnail(uri, item, isFlat = true, targetHeight = targetHeight)
            FileType.MOBI -> generateMobiThumbnail(uri, item, targetHeight)
            FileType.FB2,
            FileType.HTML,
            FileType.MD,
            FileType.TXT,
            FileType.DOCX -> generateTextPreview(uri, item.type)
            else -> null
        }
    }

    private suspend fun generateOdtThumbnail(
        uri: Uri,
        item: RecentFileItem,
        isFlat: Boolean,
        targetHeight: Int
    ): Bitmap? {
        val embeddedThumbnail = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                OdtParser(appContext).createOdtBook(
                    inputStream = input,
                    bookId = item.bookId,
                    originalBookNameHint = item.displayName,
                    isFlat = isFlat,
                    parseContent = false
                ).coverImage
            }
        }.onFailure { Timber.w(it, "Failed to extract ODT thumbnail") }.getOrNull()
        if (embeddedThumbnail != null) return embeddedThumbnail

        return generateTextPreview(uri, if (isFlat) FileType.FODT else FileType.ODT, targetHeight)
    }

    private suspend fun generateMobiThumbnail(uri: Uri, item: RecentFileItem, targetHeight: Int): Bitmap? {
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val book = MobiParser(appContext).createMobiBook(
                    inputStream = input,
                    bookId = item.bookId,
                    originalBookNameHint = item.displayName,
                    parseContent = true
                )
                book?.coverImage ?: book?.chapters
                    ?.firstOrNull { it.plainTextContent.isNotBlank() }
                    ?.plainTextContent
                    ?.let { renderTextPage(it, targetHeight) }
            }
        }.onFailure { Timber.w(it, "Failed to generate MOBI content thumbnail") }.getOrNull()
    }

    private suspend fun generateComicCover(uri: Uri, type: FileType, targetHeight: Int): Bitmap? {
        var cacheFile: File? = null
        return try {
            cacheFile = File(appContext.cacheDir, "temp_archive_cover_${UUID.randomUUID()}.${type.name.lowercase()}")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val archiveDoc = ArchiveDocumentWrapper(cacheFile)
            try {
                if (archiveDoc.getPageCount() <= 0) return null
                val page = archiveDoc.openPage(0) ?: return null
                try {
                    val width = page.getPageWidthPoint()
                    val height = page.getPageHeightPoint()
                    if (width <= 0 || height <= 0) return null
                    val targetWidth = (targetHeight * (width.toFloat() / height.toFloat())).toInt().coerceAtLeast(1)
                    val bitmap = createBitmap(targetWidth, targetHeight)
                    page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false)
                    bitmap
                } finally {
                    page.close()
                }
            } finally {
                archiveDoc.close()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate comic archive thumbnail")
            null
        } finally {
            runCatching { cacheFile?.delete() }
        }
    }

    private fun generateTextPreview(uri: Uri, type: FileType, targetHeight: Int = 800): Bitmap? {
        val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
            when (type) {
                FileType.FB2 -> extractXmlText(input, textTags = FB2_TEXT_TAGS, rootTag = "body")
                FileType.HTML -> extractHtmlText(input)
                FileType.MD, FileType.TXT -> readPlainText(input)
                FileType.DOCX -> extractDocxText(input)
                FileType.ODT -> extractOdtText(input, isFlat = false)
                FileType.FODT -> extractOdtText(input, isFlat = true)
                else -> null
            }
        }?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: return null

        return renderTextPage(text, targetHeight)
    }

    private fun extractHtmlText(input: InputStream): String? {
        val raw = input.bufferedReader(Charsets.UTF_8).use { it.readTextLimited(MAX_TEXT_SOURCE_CHARS) }
        return Jsoup.parse(raw).body().text().takeIf { it.isNotBlank() }
    }

    private fun readPlainText(input: InputStream): String? {
        return input.bufferedReader(Charsets.UTF_8).use { it.readTextLimited(MAX_TEXT_SOURCE_CHARS) }
            .takeIf { it.isNotBlank() }
    }

    private fun extractDocxText(input: InputStream): String? {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (!entry.isDirectory && entry.name == "word/document.xml") {
                        return extractXmlText(zip, textTags = setOf("t"))
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        return null
    }

    private fun extractOdtText(input: InputStream, isFlat: Boolean): String? {
        if (isFlat) return extractXmlText(input, textTags = ODT_TEXT_TAGS, rootTag = "text")

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (!entry.isDirectory && entry.name == "content.xml") {
                        return extractXmlText(zip, textTags = ODT_TEXT_TAGS, rootTag = "text")
                    }
                } finally {
                    zip.closeEntry()
                }
            }
        }
        return null
    }

    private fun extractXmlText(input: InputStream, textTags: Set<String>, rootTag: String? = null): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val output = StringBuilder()
        var inRoot = rootTag == null
        var textDepth = 0
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && output.length < MAX_PREVIEW_TEXT_CHARS) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.localName()
                    if (name == rootTag) inRoot = true
                    if (inRoot && name in textTags) {
                        if (output.isNotEmpty()) output.append(' ')
                        textDepth++
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inRoot && textDepth > 0) {
                        val value = parser.text?.trim()
                        if (!value.isNullOrBlank()) {
                            if (output.isNotEmpty() && !output.endsWith(' ')) output.append(' ')
                            output.append(value)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.localName()
                    if (inRoot && name in textTags && textDepth > 0) textDepth--
                    if (name == rootTag) inRoot = false
                }
            }
            event = parser.next()
        }
        return output.toString().takeIf { it.isNotBlank() }
    }

    private fun renderTextPage(text: String, targetHeight: Int): Bitmap {
        val width = 560
        val height = targetHeight.coerceAtLeast(320)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 28, 28)
            textSize = 25f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
        }
        val margin = 48f
        val lineHeight = 36f
        val maxWidth = width - margin * 2
        var y = margin + lineHeight

        for (line in wrapText(text, paint, maxWidth)) {
            if (y > height - margin) break
            canvas.drawText(line, margin, y, paint)
            y += lineHeight
        }
        return bitmap
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): Sequence<String> = sequence {
        val words = text.split(' ').filter { it.isNotBlank() }
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotBlank()) yield(current)
                current = if (paint.measureText(word) <= maxWidth) word else trimToWidth(word, paint, maxWidth)
            }
        }
        if (current.isNotBlank()) yield(current)
    }

    private fun trimToWidth(text: String, paint: Paint, maxWidth: Float): String {
        val keep = ceil(text.length * (maxWidth / paint.measureText(text)).coerceIn(0.05f, 1f)).toInt()
        var candidate = text.take(keep.coerceAtLeast(1))
        while (candidate.length > 1 && paint.measureText(candidate) > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return candidate
    }

    private fun InputStream.readTextLimited(maxBytes: Int): String {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (total < maxBytes) {
            val count = read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (count <= 0) break
            output.write(buffer, 0, count)
            total += count
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun java.io.Reader.readTextLimited(maxChars: Int): String {
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        val output = StringBuilder(minOf(maxChars, DEFAULT_BUFFER_SIZE))
        while (output.length < maxChars) {
            val count = read(buffer, 0, minOf(buffer.size, maxChars - output.length))
            if (count <= 0) break
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    private fun String.localName(): String = substringAfter(':').lowercase()

    private fun StringBuilder.endsWith(char: Char): Boolean = isNotEmpty() && this[length - 1] == char

    companion object {
        private const val MAX_TEXT_SOURCE_CHARS = 256_000
        private const val MAX_PREVIEW_TEXT_CHARS = 8_000
        private val ODT_TEXT_TAGS = setOf("p", "h", "span", "a")
        private val FB2_TEXT_TAGS = setOf("p", "v", "subtitle", "text-author")
    }
}
