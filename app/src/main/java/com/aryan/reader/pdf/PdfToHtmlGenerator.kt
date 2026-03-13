// PdfToHtmlGenerator.kt
package com.aryan.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

private const val TAG = "PdfToHtml"

object PdfToHtmlGenerator {

    suspend fun generateHtmlFile(
        context: Context,
        pdfUri: Uri,
        destFile: File,
        startPage: Int = 1,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        Timber.tag(TAG).d("generateHtmlFile START | uri=$pdfUri | startPage=$startPage")

        val pdfiumCore = PdfiumCoreKt(Dispatchers.Default)
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: run {
            Timber.tag(TAG).e("Failed to open ParcelFileDescriptor")
            return@withContext false
        }

        try {
            val doc = pdfiumCore.newDocument(pfd)
            val totalPages = doc.getPageCount()
            Timber.tag(TAG).d("Document loaded. Total pages: $totalPages")

            val headerFooterStrings = detectRepeatingHeaderFooter(doc, totalPages)

            destFile.bufferedWriter().use { writer ->
                writer.write(buildGlobalHtmlHeader())
                for (pageIdx in (startPage - 1) until totalPages) {
                    if (pageIdx > startPage - 1) {
                        writer.write("\n<page-break></page-break>\n")
                    }

                    val pageHtml = extractPageHtml(doc, pageIdx, pageIdx + 1, headerFooterStrings)
                    writer.write(pageHtml)
                    if (pageIdx % 5 == 0 || pageIdx == totalPages - 1) {
                        onProgress((pageIdx + 1).toFloat() / totalPages.toFloat())
                    }
                }
                writer.write(buildGlobalHtmlFooter())
            }

            doc.close()
            pfd.close()
            Timber.tag(TAG).d("generateHtmlFile SUCCESS | ${System.currentTimeMillis() - t0}ms")
            return@withContext true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to generate HTML from PDF")
            try { pfd.close() } catch (_: Exception) {}
            return@withContext false
        }
    }

    private data class TextSpan(
        val text: String,
        val size: Float,
        val isBold: Boolean,
        val isItalic: Boolean
    )

    private sealed interface PageElement {
        val yPos: Float
    }

    private data class TextElement(
        val line: TextLine,
        override val yPos: Float
    ) : PageElement

    private data class ImageElement(
        val base64Data: String,
        val width: Int,
        val height: Int,
        override val yPos: Float
    ) : PageElement

    private data class TextLine(
        val spans: List<TextSpan>,
        val yPos: Float,
        val charCount: Int
    )

    private fun buildGlobalHtmlHeader(): String = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
        body { font-family: sans-serif; line-height: 1.65; padding: 1em; max-width: 100%; margin: 0; }
        h1 { font-size: 1.9em; font-weight: bold; margin: 1.2em 0 0.4em; }
        h2 { font-size: 1.55em; font-weight: bold; margin: 1.1em 0 0.35em; }
        h3 { font-size: 1.3em; font-weight: bold; margin: 1.0em 0 0.3em; }
        h4 { font-size: 1.1em; font-weight: bold; margin: 0.9em 0 0.25em; }
        p { margin: 0.5em 0; }
        ul, ol { padding-left: 1.5em; margin: 0.5em 0; }
        li { margin-bottom: 0.2em; }
        hr { border: none; border-top: 1px solid currentColor; opacity: 0.25; margin: 1.4em 0; }
        .page-section { margin-bottom: 0.5em; }
        .page-marker { opacity: 0.4; font-size: 0.72em; margin-bottom: 1.2em; letter-spacing: 0.04em; }
        .page-divider { border: none; border-top: 1px solid currentColor; opacity: 0.12; margin: 2em 0 1.5em; }
        </style>
        </head>
        <body>
    """.trimIndent() + "\n"

    private fun buildGlobalHtmlFooter(): String = "\n</body>\n</html>\n"

    private suspend fun extractPageHtml(
        doc: PdfDocumentKt,
        pageIdx: Int,
        pageNumber: Int,
        headerFooterStrings: Set<String>
    ): String {
        return try {
            doc.openPage(pageIdx).use { page ->
                if (page == null) return buildEmptyPageSection(pageNumber)
                page.openTextPage().use { textPage ->
                    val charCount = textPage.textPageCountChars()

                    val pagePtr = page.page.pagePtr
                    val textPagePtr = textPage.page.pagePtr

                    val imageElements = mutableListOf<ImageElement>()
                    val objCount = NativePdfiumBridge.getPageObjectCount(pagePtr)
                    for (i in 0 until objCount) {
                        if (NativePdfiumBridge.getPageObjectType(pagePtr, i) == 3) {
                            val bbox = FloatArray(4)
                            if (NativePdfiumBridge.getPageObjectBoundingBox(pagePtr, i, bbox)) {
                                val topY = bbox[3]
                                val dimens = IntArray(2)
                                val pixels = NativePdfiumBridge.extractImagePixels(pagePtr, i, dimens)
                                if (pixels != null && dimens[0] > 0 && dimens[1] > 0) {
                                    try {
                                        val bmp = Bitmap.createBitmap(pixels, dimens[0], dimens[1], Bitmap.Config.ARGB_8888)
                                        val baos = ByteArrayOutputStream()
                                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                                        imageElements.add(ImageElement(b64, dimens[0], dimens[1], topY))
                                        bmp.recycle()
                                    } catch (_: Exception) {
                                        Timber.tag(TAG).w("Failed to process image $i on page $pageIdx")
                                    }
                                }
                            }
                        }
                    }

                    if (charCount <= 0) {
                        return@use if (imageElements.isNotEmpty()) {
                            buildPageHtml(pageNumber, imageElements.sortedByDescending { it.yPos }, headerFooterStrings)
                        } else buildEmptyPageSection(pageNumber)
                    }

                    val rawText = textPage.textPageGetText(0, charCount) ?: ""
                    val actualCount = minOf(charCount, rawText.length)

                    val sizes: FloatArray?
                    val weights: IntArray?
                    val flags: IntArray?
                    val charBoxes: FloatArray?

                    synchronized(PdfiumCore.lock) {
                        sizes     = NativePdfiumBridge.getPageFontSizes(textPagePtr, actualCount)
                        weights   = NativePdfiumBridge.getPageFontWeights(textPagePtr, actualCount)
                        flags     = NativePdfiumBridge.getPageFontFlags(textPagePtr, actualCount)
                        charBoxes = NativePdfiumBridge.getPageCharBoxes(textPagePtr, actualCount)
                    }

                    if (sizes == null || weights == null || flags == null) {
                        return@use buildFallbackPageSection(pageNumber, rawText)
                    }

                    val textLines = mutableListOf<TextLine>()
                    val currentSpans = mutableListOf<TextSpan>()
                    val currentSpanBuf = StringBuilder()
                    var curSize = -1f
                    var curBold = false
                    var curItalic = false
                    var lineBaseline = 0f

                    fun commitSpan() {
                        if (currentSpanBuf.isNotEmpty()) {
                            currentSpans.add(TextSpan(currentSpanBuf.toString(), curSize, curBold, curItalic))
                            currentSpanBuf.clear()
                        }
                    }

                    fun commitLine() {
                        commitSpan()
                        if (currentSpans.isNotEmpty()) {
                            val text = currentSpans.joinToString("") { it.text }
                            if (text.isNotBlank()) {
                                textLines.add(TextLine(currentSpans.toList(), lineBaseline, text.length))
                            }
                            currentSpans.clear()
                        }
                        lineBaseline = 0f
                    }

                    for (i in 0 until actualCount) {
                        val c = rawText[i]
                        val code = c.code

                        if (code == 0 || code == 13) continue

                        if (c == '\n') {
                            commitLine()
                            continue
                        }

                        val charToProcess = when (c) {
                            '\u00A0' -> ' '
                            '\u00AD' -> '-'
                            '\u0009' -> ' '
                            else -> c
                        }

                        val type = Character.getType(c).toByte()
                        val isJunk = when {
                            code == 0xFFFE || code == 0xFFFF -> true
                            code == 0xFFFD -> true
                            type == Character.PRIVATE_USE -> true
                            type == Character.SURROGATE -> true
                            type == Character.UNASSIGNED -> true
                            (type == Character.CONTROL && code > 31) -> true
                            else -> false
                        }

                        if (isJunk) {
                            val prefix = rawText.substring(maxOf(0, i - 2), i).replace("\n", "\\n")
                            val suffix = rawText.substring(minOf(actualCount, i + 1), minOf(actualCount, i + 3)).replace("\n", "\\n")
                            Timber.tag("PdfToHtml").w("Filtered Junk: 0x${Integer.toHexString(code).uppercase()} at pg $pageIdx. Context: '$prefix[$c]$suffix'")
                            continue
                        }

                        val size = sizes[i].coerceAtLeast(0f)
                        val isBold = weights[i] > 600
                        val isItalic = (flags[i] and 64) != 0

                        if (currentSpanBuf.isEmpty() && currentSpans.isEmpty() && !charToProcess.isWhitespace()) {
                            lineBaseline = if (charBoxes != null && i * 4 + 1 < charBoxes.size) charBoxes[i * 4 + 1] else 0f
                        }

                        if (currentSpanBuf.isEmpty()) {
                            curSize = size; curBold = isBold; curItalic = isItalic
                            currentSpanBuf.append(charToProcess)
                        } else if (!charToProcess.isWhitespace() && (size != curSize || isBold != curBold || isItalic != curItalic)) {
                            commitSpan()
                            curSize = size; curBold = isBold; curItalic = isItalic
                            currentSpanBuf.append(charToProcess)
                        } else {
                            currentSpanBuf.append(charToProcess)
                        }
                    }
                    commitLine()

                    // 3. MERGE TEXT AND IMAGES VERTICALLY
                    val finalElements = mutableListOf<PageElement>()
                    var imgIdx = 0
                    val sortedImages = imageElements.sortedByDescending { it.yPos }

                    for (line in textLines) {
                        // Place images physically positioned above this text line
                        while (imgIdx < sortedImages.size && sortedImages[imgIdx].yPos >= line.yPos) {
                            finalElements.add(sortedImages[imgIdx])
                            imgIdx++
                        }
                        finalElements.add(TextElement(line, line.yPos))
                    }
                    // Place any remaining images at the bottom of the page
                    while (imgIdx < sortedImages.size) {
                        finalElements.add(sortedImages[imgIdx])
                        imgIdx++
                    }

                    buildPageHtml(pageNumber, finalElements, headerFooterStrings)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error extracting page $pageIdx")
            buildEmptyPageSection(pageNumber)
        }
    }

    private fun buildEmptyPageSection(pageNumber: Int) =
        "<section class=\"page-section\">\n" +
                "<p class=\"page-marker\">— Page $pageNumber —</p>\n" +
                "<p><em>(No text on this page)</em></p>\n</section>\n"

    private fun buildFallbackPageSection(pageNumber: Int, rawText: String) =
        "<section class=\"page-section\">\n" +
                "<p class=\"page-marker\">— Page $pageNumber —</p>\n" +
                "<p>${rawText.escapeHtml()}</p>\n</section>\n"

    private fun buildPageHtml(
        pageNumber: Int,
        elements: List<PageElement>,
        headerFooterStrings: Set<String>
    ): String {
        val textElements = elements.filterIsInstance<TextElement>()

        val sizeFreq = HashMap<Int, Int>()
        textElements.forEach { te ->
            te.line.spans.forEach { span ->
                val s = span.size.roundToInt().coerceAtLeast(1)
                sizeFreq[s] = (sizeFreq[s] ?: 0) + span.text.length
            }
        }
        val baseSize = sizeFreq.maxByOrNull { it.value }?.key?.toFloat() ?: 12f

        val lineLengths = textElements.filter { it.line.charCount > 10 }.map { it.line.charCount }.sorted()
        val typicalLineLen = if (lineLengths.isNotEmpty())
            lineLengths[(lineLengths.size * 0.80).toInt().coerceAtMost(lineLengths.size - 1)]
        else 80
        val wrapThreshold = (typicalLineLen * 0.80).toInt()

        val sb = StringBuilder()
        sb.append("<section class=\"page-section\">\n")
        sb.append("<p class=\"page-marker\">— Page $pageNumber —</p>\n")

        var inParagraph = false
        var inUl = false
        var inOl = false

        fun closeParagraph() { if (inParagraph) { sb.append("</p>\n"); inParagraph = false } }
        fun closeList() {
            if (inUl) { sb.append("</ul>\n"); inUl = false }
            if (inOl) { sb.append("</ol>\n"); inOl = false }
        }

        for ((index, element) in elements.withIndex()) {
            when (element) {
                is ImageElement -> {
                    closeParagraph()
                    closeList()
                    sb.append("<div style=\"text-align:center; margin: 1.5em 0;\">\n")
                    sb.append("<img src=\"data:image/jpeg;base64,${element.base64Data}\" style=\"max-width:100%; height:auto; border-radius: 6px;\"/>\n")
                    sb.append("</div>\n")
                }
                is TextElement -> {
                    val line = element.line
                    val lineText = line.spans.joinToString("") { it.text }
                    val trimmed  = lineText.trim()

                    if (trimmed.isEmpty() || headerFooterStrings.any { hf -> trimmed.equals(hf, ignoreCase = true) }) {
                        closeParagraph()
                        continue
                    }

                    val maxSize = line.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.size } ?: baseSize
                    val headingLevel = when {
                        maxSize > baseSize * 1.6f -> 1
                        maxSize > baseSize * 1.28f -> 2
                        maxSize > baseSize * 1.10f -> 3
                        maxSize > baseSize * 1.04f -> 4
                        else -> 0
                    }

                    val lineLen  = trimmed.length
                    val isShort  = lineLen < 60
                    val isAllCaps = isShort && lineLen >= 3 && trimmed.any { it.isLetter() } && trimmed.all { it.isUpperCase() || !it.isLetter() } && !trimmed.endsWith(".")
                    val isBullet = trimmed.startsWith("•") || trimmed.startsWith("▪") || trimmed.startsWith("◦") || trimmed.startsWith("–") || (trimmed.startsWith("- ") && trimmed.length > 2 && !trimmed.startsWith("--"))
                    val numberedMatch = Regex("""^(\d{1,3}[.)]\s|\p{L}[.)]\s)""").containsMatchIn(trimmed)
                    val isHr = isShort && trimmed.length >= 3 && trimmed.all { it == '-' || it == '=' || it == '_' || it == '—' || it.isWhitespace() }

                    val effectiveHeading = when {
                        headingLevel > 0 -> headingLevel
                        isAllCaps && !isBullet && !numberedMatch -> 2
                        else -> 0
                    }

                    val nextTextElem = elements.drop(index + 1).firstOrNull { it is TextElement && it.line.spans.joinToString(""){ s->s.text}.isNotBlank() } as? TextElement
                    val shouldBreakParagraph = effectiveHeading > 0 || isBullet || numberedMatch || isHr ||
                            lineLen < wrapThreshold ||
                            trimmed.last().let { it == '.' || it == '!' || it == '?' || it == ':' || it == '"' || it == '\u201d' } ||
                            (nextTextElem != null && nextTextElem.line.spans.joinToString("") { it.text }.trimStart().let { it.startsWith("\u201c") || it.startsWith("\"") || it.startsWith("-") })

                    when {
                        isHr -> {
                            closeParagraph(); closeList()
                            sb.append("<hr>\n")
                        }
                        effectiveHeading > 0 -> {
                            closeParagraph(); closeList()
                            val tag = "h${effectiveHeading.coerceIn(1, 4)}"
                            sb.append("<$tag>${renderSpans(line.spans, insideHeading = true)}</$tag>\n")
                        }
                        isBullet -> {
                            closeParagraph()
                            if (inOl) { sb.append("</ol>\n"); inOl = false }
                            if (!inUl) { sb.append("<ul>\n"); inUl = true }
                            val content = trimmed.removePrefix("•").removePrefix("▪").removePrefix("◦").removePrefix("–").removePrefix("- ").trim()
                            sb.append("<li>${content.escapeHtml()}</li>\n")
                        }
                        numberedMatch -> {
                            closeParagraph()
                            if (inUl) { sb.append("</ul>\n"); inUl = false }
                            if (!inOl) { sb.append("<ol>\n"); inOl = true }
                            val content = trimmed.substringAfter(" ").trim()
                            sb.append("<li>${content.escapeHtml()}</li>\n")
                        }
                        shouldBreakParagraph -> {
                            closeList()
                            if (!inParagraph) { sb.append("<p>"); inParagraph = true }
                            sb.append(renderSpans(line.spans))
                            closeParagraph()
                        }
                        else -> {
                            closeList()
                            if (!inParagraph) { sb.append("<p>"); inParagraph = true } else sb.append(" ")
                            sb.append(renderSpans(line.spans))
                        }
                    }
                }
            }
        }

        closeParagraph()
        closeList()
        sb.append("</section>\n")
        return sb.toString()
    }

    private fun renderSpans(spans: List<TextSpan>, insideHeading: Boolean = false): String {
        val sb = StringBuilder()
        for (span in spans) {
            val s = span.text.escapeHtml()
            if (s.isBlank()) { sb.append(s); continue }

            val leadCount  = s.length - s.trimStart().length
            val trailCount = s.length - s.trimEnd().length
            val pre  = s.take(leadCount)
            val post = if (trailCount > 0) s.takeLast(trailCount) else ""
            val mid  = s.substring(leadCount, s.length - trailCount)

            if (mid.isEmpty()) { sb.append(s); continue }

            sb.append(pre)
            if (!insideHeading) {
                if (span.isBold && span.isItalic) sb.append("<strong><em>")
                else if (span.isBold)             sb.append("<strong>")
                else if (span.isItalic)           sb.append("<em>")
            }
            sb.append(mid)
            if (!insideHeading) {
                if (span.isBold && span.isItalic) sb.append("</em></strong>")
                else if (span.isBold)             sb.append("</strong>")
                else if (span.isItalic)           sb.append("</em>")
            }
            sb.append(post)
        }
        return sb.toString()
    }

    private suspend fun detectRepeatingHeaderFooter(
        doc: PdfDocumentKt,
        totalPages: Int
    ): Set<String> = withContext(Dispatchers.Default) {
        if (totalPages < 5) return@withContext emptySet()

        val step = maxOf(1, totalPages / 8)
        val samplePages = (0 until totalPages).filter { it % step == 0 }.take(8)
        val frequency = HashMap<String, Int>()

        for (pageIdx in samplePages) {
            try {
                doc.openPage(pageIdx).use { page ->
                    page.openTextPage().use { textPage ->
                        val charCount = textPage.textPageCountChars()
                        if (charCount <= 0) return@use
                        val rawText = textPage.textPageGetText(0, charCount) ?: return@use

                        val lines = rawText.split('\n').map { it.trim() }.filter { it.length > 2 }
                        if (lines.isNotEmpty()) {
                            val edgeLines = lines.take(2) + lines.takeLast(2)
                            for (line in edgeLines) {
                                frequency[line] = (frequency[line] ?: 0) + 1
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Header/footer sampling failed for page $pageIdx")
            }
        }
        frequency.filter { it.value >= 3 }.keys.toSet()
    }

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}