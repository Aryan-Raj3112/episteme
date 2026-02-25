/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import java.io.File
import java.io.FileInputStream
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import androidx.core.graphics.createBitmap
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.VirtualPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.StringTokenizer

object PdfExporter {
    private class PdfBoxFontCache(val doc: PDDocument, val context: Context) {
        private val cache = mutableMapOf<String, PDFont>()

        fun getFont(fontPath: String?, fontName: String?, isBold: Boolean, isItalic: Boolean): PDFont {
            if (!fontPath.isNullOrBlank()) {
                Timber.tag("PdfFontDebug").d("Exporter: Requesting font at $fontPath")
                val cached = cache[fontPath]
                if (cached != null) return cached

                try {
                    val font = if (fontPath.startsWith("asset:")) {
                        val assetPath = fontPath.removePrefix("asset:")
                        Timber.tag("PdfFontDebug").i("Exporter: Loading preset font from assets: $assetPath")
                        PDType0Font.load(doc, context.assets.open(assetPath))
                    } else {
                        val file = File(fontPath)
                        if (file.exists()) {
                            PDType0Font.load(doc, FileInputStream(file))
                        } else null
                    }

                    if (font != null) {
                        cache[fontPath] = font
                        return font
                    }
                } catch (e: Exception) {
                    Timber.tag("PdfFontDebug").e(e, "Exporter: Failed to embed $fontPath")
                }
            }

            // 2. Map Standard Presets via fontName
            if (fontName != null) {
                when (fontName) {
                    "Serif" -> return when {
                        isBold && isItalic -> PDType1Font.TIMES_BOLD_ITALIC
                        isBold -> PDType1Font.TIMES_BOLD
                        isItalic -> PDType1Font.TIMES_ITALIC
                        else -> PDType1Font.TIMES_ROMAN
                    }
                    "Monospace" -> return when {
                        isBold && isItalic -> PDType1Font.COURIER_BOLD_OBLIQUE
                        isBold -> PDType1Font.COURIER_BOLD
                        isItalic -> PDType1Font.COURIER_OBLIQUE
                        else -> PDType1Font.COURIER
                    }
                    // "Sans" and others fall through to Helvetica
                }
            }

            // 3. Fallback to Helvetica (Sans-Serif)
            return when {
                isBold && isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                isBold -> PDType1Font.HELVETICA_BOLD
                isItalic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
        }
    }

    private fun applyStyleSimulations(
        cs: PDPageContentStream,
        fontSize: Float,
        isBold: Boolean,
        isItalic: Boolean,
        isCustomFont: Boolean,
        x: Float,
        y: Float
    ) {
        if (isCustomFont) {
            if (isBold) {
                cs.setRenderingMode(RenderingMode.FILL_STROKE)
                cs.setLineWidth(fontSize * 0.03f)
            } else {
                cs.setRenderingMode(RenderingMode.FILL)
            }

            if (isItalic) {
                cs.setTextMatrix(Matrix(1f, 0f, 0.3f, 1f, x, y))
            } else {
                cs.setTextMatrix(Matrix(1f, 0f, 0f, 1f, x, y))
            }
        } else {
            cs.setRenderingMode(RenderingMode.FILL)
            cs.setTextMatrix(Matrix(1f, 0f, 0f, 1f, x, y))
        }
    }

    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceUri: Uri,
        destStream: OutputStream,
        virtualPages: List<VirtualPage>?,
        inkAnnotations: Map<Int, List<PdfAnnotation>>,
        richTextPageLayouts: List<PageTextLayout>? = null,
        textBoxes: List<PdfTextBox>? = null
    ) {
        withContext(Dispatchers.IO) {
            var sourceDocument: PDDocument? = null
            var destDocument: PDDocument? = null
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                sourceDocument = PDDocument.load(inputStream)
                destDocument = PDDocument()

                // Determine the sequence of pages to export
                val pagesToProcess: List<VirtualPage> =
                        virtualPages
                                ?: (0 until sourceDocument.numberOfPages).map {
                                    VirtualPage.PdfPage(it)
                                }

                val referencePage =
                        if (sourceDocument.numberOfPages > 0) sourceDocument.getPage(0) else null
                val fontCache = PdfBoxFontCache(destDocument, context)

                pagesToProcess.forEachIndexed { virtualIndex, vPage ->
                    val pageToDecorate: PDPage =
                        when (vPage) {
                            is VirtualPage.PdfPage -> {
                                if (vPage.pdfIndex < sourceDocument.numberOfPages) {
                                    destDocument.importPage(
                                        sourceDocument.getPage(vPage.pdfIndex)
                                    )
                                } else {
                                    Timber.w(
                                        "Source page ${vPage.pdfIndex} is out of bounds! Creating blank page as fallback."
                                    )
                                    val blank =
                                        PDPage(referencePage?.mediaBox ?: PDRectangle.A4)
                                    destDocument.addPage(blank)
                                    blank
                                }
                            }
                            is VirtualPage.BlankPage -> {
                                Timber.tag("PdfExportSize").d("Creating blank page with explicit dimensions: ${vPage.width}x${vPage.height}")
                                val blank = PDPage(PDRectangle(vPage.width.toFloat(), vPage.height.toFloat()))
                                destDocument.addPage(blank)
                                blank
                            }
                        }

                    val pageInkAnnos = inkAnnotations[virtualIndex] ?: emptyList()
                    val richTextLayout = richTextPageLayouts?.find { it.pageIndex == virtualIndex }

                    val cropBox = pageToDecorate.cropBox
                    val pageWidth = cropBox.width
                    val pageHeight = cropBox.height
                    val lowerLeftY = cropBox.lowerLeftY

                    if (pageInkAnnos.isNotEmpty()) {
                        val (pencilAnnos, vectorAnnos) =
                                pageInkAnnos.partition { it.inkType == InkType.PENCIL }

                        if (pencilAnnos.isNotEmpty()) {
                            drawPencilOverlay(
                                    destDocument,
                                    pageToDecorate,
                                    pencilAnnos,
                                    pageWidth,
                                    pageHeight,
                                    lowerLeftY
                            )
                        }

                        if (vectorAnnos.isNotEmpty()) {
                            PDPageContentStream(
                                            destDocument,
                                            pageToDecorate,
                                            PDPageContentStream.AppendMode.APPEND,
                                            true,
                                            true
                                    )
                                    .use { cs ->
                                        vectorAnnos.forEach { annotation ->
                                            if (annotation.inkType == InkType.FOUNTAIN_PEN) {
                                                drawFountainPen(
                                                        cs,
                                                        annotation,
                                                        pageWidth,
                                                        pageHeight,
                                                        lowerLeftY
                                                )
                                            } else {
                                                drawStandardAnnotation(
                                                        cs,
                                                        annotation,
                                                        pageWidth,
                                                        pageHeight,
                                                        lowerLeftY
                                                )
                                            }
                                        }
                                    }
                        }
                    }

                    if (richTextLayout != null && richTextLayout.visibleText.isNotEmpty()) {
                        PDPageContentStream(destDocument, pageToDecorate, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            drawRichTextLayout(cs, richTextLayout, pageWidth, pageHeight, lowerLeftY, fontCache)
                        }
                    }
                    val pageTextBoxes = textBoxes?.filter { it.pageIndex == virtualIndex }
                    if (!pageTextBoxes.isNullOrEmpty()) {
                        PDPageContentStream(destDocument, pageToDecorate, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            drawTextBoxes(cs, pageTextBoxes, pageWidth, pageHeight, lowerLeftY, fontCache)
                        }
                    }
                }
                destDocument.save(destStream)
            } catch (e: Exception) {
                Timber.e(e, "Export failed")
                throw e
            } finally {
                sourceDocument?.close()
                destDocument?.close()
                destStream.close()
            }
        }
    }

    private fun drawTextBoxes(
        cs: PDPageContentStream,
        boxes: List<PdfTextBox>,
        pageWidth: Float,
        pageHeight: Float,
        lowerLeftY: Float,
        fontCache: PdfBoxFontCache
    ) {
        for (box in boxes) {
            if (box.text.isBlank()) continue

            val font = fontCache.getFont(box.fontPath, box.fontName, box.isBold, box.isItalic)
            val fontSize = box.fontSize * pageHeight
            val lineHeight = fontSize * 1.2f
            val boxX = box.relativeBounds.left * pageWidth
            val boxWidth = box.relativeBounds.width * pageWidth
            val topY = lowerLeftY + pageHeight - (box.relativeBounds.top * pageHeight)

            val wrappedLines = mutableListOf<String>()
            val paragraphs = box.text.split('\n')

            for (paragraph in paragraphs) {
                if (paragraph.isEmpty()) {
                    wrappedLines.add("")
                    continue
                }

                val tokenizer = StringTokenizer(paragraph, " ", true)
                var currentLine = StringBuilder()
                var currentLineWidth = 0f

                while (tokenizer.hasMoreTokens()) {
                    val token = tokenizer.nextToken()

                    fun getStringWidth(s: String): Float = try {
                        (font.getStringWidth(s) / 1000f) * fontSize
                    } catch (_: Exception) { 0f }

                    val tokenWidth = getStringWidth(token)

                    if (tokenWidth > boxWidth) {
                        if (currentLine.isNotEmpty()) {
                            wrappedLines.add(currentLine.toString())
                            currentLine = StringBuilder()
                            currentLineWidth = 0f
                        }

                        var tempWord = StringBuilder()
                        var tempWidth = 0f

                        for (char in token) {
                            val charW = getStringWidth(char.toString())
                            if (tempWidth + charW > boxWidth) {
                                wrappedLines.add(tempWord.toString())
                                tempWord = StringBuilder(char.toString())
                                tempWidth = charW
                            } else {
                                tempWord.append(char)
                                tempWidth += charW
                            }
                        }
                        currentLine.append(tempWord)
                        currentLineWidth = tempWidth
                    } else if (currentLineWidth + tokenWidth <= boxWidth) {
                        currentLine.append(token)
                        currentLineWidth += tokenWidth
                    } else {
                        wrappedLines.add(currentLine.toString())
                        if (token.isBlank()) {
                            currentLine = StringBuilder()
                            currentLineWidth = 0f
                        } else {
                            currentLine = StringBuilder(token)
                            currentLineWidth = tokenWidth
                        }
                    }
                }
                if (currentLine.isNotEmpty()) {
                    wrappedLines.add(currentLine.toString())
                }
            }

            if (box.backgroundColor != Color.Transparent &&
                box.backgroundColor != Color.Unspecified) {

                val r = box.backgroundColor.red
                val g = box.backgroundColor.green
                val b = box.backgroundColor.blue
                val a = box.backgroundColor.alpha

                if (a < 1.0f) {
                    val gs = PDExtendedGraphicsState()
                    gs.nonStrokingAlphaConstant = a
                    cs.setGraphicsStateParameters(gs)
                }

                cs.setNonStrokingColor(r, g, b)

                var currentBgY = topY

                for (line in wrappedLines) {
                    if (line.isNotEmpty()) {
                        val lineWidth = try { (font.getStringWidth(line) / 1000f) * fontSize } catch(_: Exception) { 0f }
                        val padding = fontSize * 0.1f

                        cs.addRect(boxX - padding, currentBgY - lineHeight, lineWidth + (padding * 2), lineHeight)
                        cs.fill()
                    }
                    currentBgY -= lineHeight
                }

                if (a < 1.0f) {
                    val gs = PDExtendedGraphicsState()
                    gs.nonStrokingAlphaConstant = 1.0f
                    cs.setGraphicsStateParameters(gs)
                }
            }

            val tr = box.color.red
            val tg = box.color.green
            val tb = box.color.blue
            cs.setNonStrokingColor(tr, tg, tb)
            cs.setFont(font, fontSize)

            val textY = topY - (fontSize * 0.85f)

            cs.beginText()
            for ((index, line) in wrappedLines.withIndex()) {
                val currentLineY = textY - (index * lineHeight)

                applyStyleSimulations(
                    cs = cs,
                    fontSize = fontSize,
                    isBold = box.isBold,
                    isItalic = box.isItalic,
                    isCustomFont = !box.fontPath.isNullOrBlank(),
                    x = boxX,
                    y = currentLineY
                )

                if (line.isNotEmpty()) {
                    try {
                        cs.showText(line)
                    } catch (e: Exception) {
                        Timber.e(e, "Error drawing text line")
                    }
                }
            }
            cs.endText()

            if (box.isUnderline || box.isStrikeThrough) {
                cs.setStrokingColor(tr, tg, tb)
                cs.setLineWidth(fontSize / 15f)

                var decorY = topY - (fontSize * 0.85f)

                for (line in wrappedLines) {
                    if (line.isNotEmpty()) {
                        val lineWidth = try { (font.getStringWidth(line) / 1000f) * fontSize } catch(_:Exception){0f}

                        if (box.isUnderline) {
                            val underlineY = decorY - (fontSize * 0.15f)
                            cs.moveTo(boxX, underlineY)
                            cs.lineTo(boxX + lineWidth, underlineY)
                            cs.stroke()
                        }

                        if (box.isStrikeThrough) {
                            val strikeY = decorY + (fontSize * 0.3f)
                            cs.moveTo(boxX, strikeY)
                            cs.lineTo(boxX + lineWidth, strikeY)
                            cs.stroke()
                        }
                    }
                    decorY -= lineHeight
                }
            }
        }
    }

    private fun drawPencilOverlay(
            document: PDDocument,
            page: PDPage,
            annotations: List<PdfAnnotation>,
            pageWidth: Float,
            pageHeight: Float,
            lowerLeftY: Float
    ) {
        val scale = 2.0f
        val bitmapW = (pageWidth * scale).toInt()
        val bitmapH = (pageHeight * scale).toInt()

        if (bitmapW <= 0 || bitmapH <= 0) return

        val bitmap = createBitmap(bitmapW, bitmapH)
        val canvas = Canvas(bitmap)

        val texture = PdfTextureGenerator.getNoiseTexture()

        val paint =
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                }

        annotations.forEach { annot ->
            if (annot.points.size > 1) {
                val strokeWidthPx = annot.strokeWidth * bitmapW
                paint.strokeWidth = strokeWidthPx

                val adjustedAlpha = (annot.color.alpha * 0.8f).coerceIn(0f, 1f)

                paint.colorFilter =
                        PorterDuffColorFilter(
                                android.graphics.Color.argb(
                                        (adjustedAlpha * 255).toInt(),
                                        (annot.color.red * 255).toInt(),
                                        (annot.color.green * 255).toInt(),
                                        (annot.color.blue * 255).toInt()
                                ),
                                PorterDuff.Mode.SRC_IN
                        )

                val path = android.graphics.Path()
                val startP = annot.points[0]
                path.moveTo(startP.x * bitmapW, startP.y * bitmapH)

                for (i in 1 until annot.points.size) {
                    val p0 = annot.points[i - 1]
                    val p1 = annot.points[i]
                    val p0x = p0.x * bitmapW
                    val p0y = p0.y * bitmapH
                    val p1x = p1.x * bitmapW
                    val p1y = p1.y * bitmapH
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    if (i == 1) path.lineTo(midX, midY) else path.quadTo(p0x, p0y, midX, midY)
                }
                val last = annot.points.last()
                path.lineTo(last.x * bitmapW, last.y * bitmapH)
                canvas.drawPath(path, paint)
            }
        }

        val pdImage = LosslessFactory.createFromImage(document, bitmap)
        bitmap.recycle()
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
                .use { cs -> cs.drawImage(pdImage, 0f, lowerLeftY, pageWidth, pageHeight) }
    }

    private fun drawFountainPen(
            cs: PDPageContentStream,
            annotation: PdfAnnotation,
            pageWidth: Float,
            pageHeight: Float,
            lowerLeftY: Float
    ) {
        if (annotation.points.size < 2) return

        val r = annotation.color.red
        val g = annotation.color.green
        val b = annotation.color.blue
        val a = annotation.color.alpha

        cs.setNonStrokingColor(r, g, b)

        if (a < 1.0f) {
            val graphicsState = PDExtendedGraphicsState()
            graphicsState.nonStrokingAlphaConstant = a
            cs.setGraphicsStateParameters(graphicsState)
        }

        val baseStrokeWidth = annotation.strokeWidth * pageWidth
        val (leftSide, rightSide) =
                PdfInkGeometry.calculateFountainPenPoints(
                        annotation.points,
                        baseStrokeWidth,
                        pageWidth,
                        pageHeight
                )

        if (leftSide.isNotEmpty()) {
            fun fixY(y: Float): Float = lowerLeftY + pageHeight - y

            cs.moveTo(leftSide[0].x, fixY(leftSide[0].y))

            for (i in 1 until leftSide.size) {
                cs.lineTo(leftSide[i].x, fixY(leftSide[i].y))
            }

            for (i in rightSide.size - 1 downTo 0) {
                cs.lineTo(rightSide[i].x, fixY(rightSide[i].y))
            }

            @Suppress("DEPRECATION") cs.closeSubPath()
            cs.fill()
        }

        if (a < 1.0f) {
            val resetState = PDExtendedGraphicsState()
            resetState.nonStrokingAlphaConstant = 1.0f
            cs.setGraphicsStateParameters(resetState)
        }
    }

    private fun drawStandardAnnotation(
            cs: PDPageContentStream,
            annotation: PdfAnnotation,
            pageWidth: Float,
            pageHeight: Float,
            lowerLeftY: Float
    ) {
        if (annotation.points.isEmpty()) return

        val r = annotation.color.red
        val g = annotation.color.green
        val b = annotation.color.blue
        val a = annotation.color.alpha

        cs.setStrokingColor(r, g, b)

        if (a < 1.0f ||
                        annotation.inkType == InkType.HIGHLIGHTER ||
                        annotation.inkType == InkType.HIGHLIGHTER_ROUND
        ) {
            val graphicsState = PDExtendedGraphicsState()
            graphicsState.strokingAlphaConstant = a

            if (annotation.inkType == InkType.HIGHLIGHTER ||
                            annotation.inkType == InkType.HIGHLIGHTER_ROUND
            ) {
                graphicsState.blendMode = BlendMode.MULTIPLY
            }
            cs.setGraphicsStateParameters(graphicsState)
        }

        val lineWidth = annotation.strokeWidth * pageWidth
        cs.setLineWidth(lineWidth)

        when (annotation.inkType) {
            InkType.HIGHLIGHTER -> cs.setLineCapStyle(0)
            else -> cs.setLineCapStyle(1)
        }
        cs.setLineJoinStyle(1)

        val points = annotation.points
        val startX = points[0].x * pageWidth
        val startY = lowerLeftY + pageHeight - (points[0].y * pageHeight)

        cs.moveTo(startX, startY)

        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]

            val p0x = p0.x * pageWidth
            val p0y = lowerLeftY + pageHeight - (p0.y * pageHeight)

            val p1x = p1.x * pageWidth
            val p1y = lowerLeftY + pageHeight - (p1.y * pageHeight)

            val midX = (p0x + p1x) / 2f
            val midY = (p0y + p1y) / 2f

            if (i == 1) {
                cs.lineTo(midX, midY)
            } else {
                cs.curveTo2(p0x, p0y, midX, midY)
            }
        }
        val lastP = points.last()
        val lastX = lastP.x * pageWidth
        val lastY = lowerLeftY + pageHeight - (lastP.y * pageHeight)
        cs.lineTo(lastX, lastY)

        cs.stroke()

        val resetState = PDExtendedGraphicsState()
        resetState.strokingAlphaConstant = 1.0f
        resetState.blendMode = BlendMode.NORMAL
        cs.setGraphicsStateParameters(resetState)
    }

    private data class StyledRun(
        val text: String,
        val fontSize: Float,
        val isBold: Boolean,
        val isItalic: Boolean,
        val isUnderline: Boolean,
        val isStrikethrough: Boolean,
        val colorArgb: Int,
        val backgroundColorArgb: Int,
        val fontPath: String?,
        val fontName: String? // Add this field
    )

    private fun buildStyledRuns(
        text: AnnotatedString,
        @Suppress("SameParameterValue") startIndex: Int,
        endIndex: Int,
        scaleFactor: Float
    ): List<StyledRun> {
        if (startIndex >= endIndex || text.text.isEmpty()) return emptyList()

        val runs = mutableListOf<StyledRun>()
        var currentRunStart = startIndex
        val currentStyle = getStyleAt(text, startIndex)

        // Updated Tuple to 9 elements
        data class StyleProps(
            val fontSize: Float,
            val isBold: Boolean,
            val isItalic: Boolean,
            val isUnderline: Boolean,
            val isStrikethrough: Boolean,
            val colorArgb: Int,
            val backgroundColorArgb: Int,
            val fontPath: String?,
            val fontName: String?
        )

        fun extractRunProperties(style: SpanStyle): StyleProps {
            val fontSize = if (style.fontSize.isSpecified) style.fontSize.value * scaleFactor else 16f * scaleFactor
            val isBold = style.fontWeight == FontWeight.Bold
            val isItalic = style.fontStyle == FontStyle.Italic
            val decoration = style.textDecoration ?: TextDecoration.None
            val isUnderline = decoration.contains(TextDecoration.Underline)
            val isStrikethrough = decoration.contains(TextDecoration.LineThrough)
            val colorArgb = if (style.color != Color.Unspecified) style.color.toArgb() else android.graphics.Color.BLACK
            val bgColorArgb = if (style.background != Color.Unspecified) style.background.toArgb() else android.graphics.Color.TRANSPARENT

            val fontPath = PdfFontCache.getPath(style.fontFamily)

            // Map standard families back to names for the exporter
            val fontName = when (style.fontFamily) {
                FontFamily.Serif -> "Serif"
                FontFamily.Monospace -> "Monospace"
                FontFamily.SansSerif -> "Sans"
                else -> null
            }

            return StyleProps(fontSize, isBold, isItalic, isUnderline, isStrikethrough, colorArgb, bgColorArgb, fontPath, fontName)
        }

        var currentProps = extractRunProperties(currentStyle)

        for (i in (startIndex + 1) until endIndex) {
            val charStyle = getStyleAt(text, i)
            val charProps = extractRunProperties(charStyle)

            if (charProps != currentProps) {
                val runText = text.text.substring(currentRunStart, i)
                runs.add(
                    StyledRun(
                        text = runText,
                        fontSize = currentProps.fontSize,
                        isBold = currentProps.isBold,
                        isItalic = currentProps.isItalic,
                        isUnderline = currentProps.isUnderline,
                        isStrikethrough = currentProps.isStrikethrough,
                        colorArgb = currentProps.colorArgb,
                        backgroundColorArgb = currentProps.backgroundColorArgb,
                        fontPath = currentProps.fontPath,
                        fontName = currentProps.fontName // Pass fontName
                    )
                )
                currentRunStart = i
                currentProps = charProps
            }
        }

        val lastRunText = text.text.substring(currentRunStart, endIndex)
        if (lastRunText.isNotEmpty()) {
            runs.add(
                StyledRun(
                    text = lastRunText,
                    fontSize = currentProps.fontSize,
                    isBold = currentProps.isBold,
                    isItalic = currentProps.isItalic,
                    isUnderline = currentProps.isUnderline,
                    isStrikethrough = currentProps.isStrikethrough,
                    colorArgb = currentProps.colorArgb,
                    backgroundColorArgb = currentProps.backgroundColorArgb,
                    fontPath = currentProps.fontPath,
                    fontName = currentProps.fontName
                )
            )
        }

        return runs
    }

    private fun drawRichTextLayout(
        cs: PDPageContentStream,
        layout: PageTextLayout,
        pageWidth: Float,
        pageHeight: Float,
        lowerLeftY: Float,
        fontCache: PdfBoxFontCache
    ) {
        val text = layout.visibleText
        val layoutPageHeightPx = layout.pageHeightPx

        if (text.text.isEmpty()) return

        Timber.tag("PdfExportWrap").d("Starting export for Page ${layout.pageIndex}")

        val estimatedDensity = 2.3f
        val scaleFactor =
            if (layoutPageHeightPx > 0) {
                estimatedDensity * pageHeight / layoutPageHeightPx
            } else {
                1.15f
            }

        val marginX = pageWidth * 0.1f
        val marginY = pageHeight * 0.08f
        val contentWidth = pageWidth - (marginX * 2)

        Timber.tag("PdfExportWrap").d("Layout Constants: pageWidth=$pageWidth, contentWidth=$contentWidth, scaleFactor=$scaleFactor")

        val allRuns = buildStyledRuns(text, 0, text.text.length, scaleFactor)

        val firstFontSize = allRuns.firstOrNull()?.fontSize ?: (16f * scaleFactor)
        var currentY = lowerLeftY + pageHeight - marginY - (firstFontSize * 1.25f)

        data class LineRun(val run: StyledRun, val width: Float)
        val currentLineRuns = mutableListOf<LineRun>()
        var currentLineWidth = 0f
        var maxFontSizeInLine = 0f

        fun flushLine() {
            if (currentLineRuns.isEmpty()) return
            Timber.tag("PdfExportWrap").d("Flushing Line: width=$currentLineWidth, y=$currentY, runsCount=${currentLineRuns.size}")
            drawLineOfRuns(cs, currentLineRuns.map { it.run }, marginX, currentY, contentWidth, fontCache)
            currentY -= (maxFontSizeInLine * 1.2f)
            currentLineRuns.clear()
            currentLineWidth = 0f
            maxFontSizeInLine = 0f
        }

        for (run in allRuns) {
            val parts = run.text.split('\n')
            parts.forEachIndexed { partIndex, part ->
                if (partIndex > 0) {
                    flushLine()
                    if (part.isEmpty()) {
                        currentY -= (run.fontSize * 1.2f)
                        return@forEachIndexed
                    }
                }

                if (part.isEmpty()) return@forEachIndexed

                val tokenizer = StringTokenizer(part, " \t\u000B\u000C\r", true)

                while (tokenizer.hasMoreTokens()) {
                    val token = tokenizer.nextToken()
                    var remainingToken = token

                    while (remainingToken.isNotEmpty()) {
                        val font = fontCache.getFont(run.fontPath, run.fontName, run.isBold, run.isItalic)

                        fun measure(s: String): Float = try {
                            (font.getStringWidth(s) / 1000f) * run.fontSize
                        } catch (_: Exception) { 0f }

                        val tokenWidth = measure(remainingToken)

                        if (currentLineWidth + tokenWidth <= contentWidth) {
                            currentLineRuns.add(LineRun(run.copy(text = remainingToken), tokenWidth))
                            currentLineWidth += tokenWidth
                            if (run.fontSize > maxFontSizeInLine) maxFontSizeInLine = run.fontSize
                            remainingToken = ""
                        }
                        else if (currentLineRuns.isNotEmpty()) {
                            flushLine()
                        }
                        else {
                            var low = 1
                            var high = remainingToken.length
                            var bestIndex = 1

                            while (low <= high) {
                                val mid = (low + high) / 2
                                if (measure(remainingToken.take(mid)) <= contentWidth) {
                                    bestIndex = mid
                                    low = mid + 1
                                } else {
                                    high = mid - 1
                                }
                            }

                            val chunk = remainingToken.take(bestIndex)
                            val chunkWidth = measure(chunk)

                            currentLineRuns.add(LineRun(run.copy(text = chunk), chunkWidth))
                            currentLineWidth = chunkWidth
                            maxFontSizeInLine = run.fontSize

                            flushLine()
                            remainingToken = remainingToken.substring(bestIndex)
                        }
                    }
                }
            }
        }
        flushLine()
    }

    private fun drawLineOfRuns(
        cs: PDPageContentStream,
        runs: List<StyledRun>,
        startX: Float,
        y: Float,
        @Suppress("UNUSED_PARAMETER") contentWidth: Float,
        fontCache: PdfBoxFontCache
    ) {
        if (runs.isEmpty()) return

        var bgX = startX
        for (run in runs) {
            val font = fontCache.getFont(run.fontPath, run.fontName, run.isBold, run.isItalic)
            val safeText = run.text.replace("\n", " ")
                .replace("\r", "")
                .replace("\u000C", "")
                .replace("\u200B", "")

            val runWidth = (font.getStringWidth(safeText) / 1000f) * run.fontSize

            if (run.backgroundColorArgb != android.graphics.Color.TRANSPARENT) {
                val r = android.graphics.Color.red(run.backgroundColorArgb) / 255f
                val g = android.graphics.Color.green(run.backgroundColorArgb) / 255f
                val b = android.graphics.Color.blue(run.backgroundColorArgb) / 255f
                cs.setNonStrokingColor(r, g, b)
                cs.addRect(bgX, y - (run.fontSize * 0.2f), runWidth, run.fontSize * 1.2f)
                cs.fill()
            }
            bgX += runWidth
        }

        cs.beginText()
        cs.newLineAtOffset(startX, y)

        var currentFont: PDFont? = null
        var currentFontSize = -1f
        var currentColor = -1
        android.graphics.Color.BLACK

        var currentX = startX

        for (run in runs) {
            val font = fontCache.getFont(run.fontPath, run.fontName, run.isBold, run.isItalic)
            val isCustom = !run.fontPath.isNullOrBlank()

            if (font != currentFont || run.fontSize != currentFontSize) {
                cs.setFont(font, run.fontSize)
                currentFont = font
                currentFontSize = run.fontSize
            }

            if (run.colorArgb != currentColor) {
                val r = android.graphics.Color.red(run.colorArgb) / 255f
                val g = android.graphics.Color.green(run.colorArgb) / 255f
                val b = android.graphics.Color.blue(run.colorArgb) / 255f
                cs.setNonStrokingColor(r, g, b)
                currentColor = run.colorArgb
            }

            applyStyleSimulations(cs, run.fontSize, run.isBold, run.isItalic, isCustom, currentX, y)

            try {
                val safeText = run.text.replace("\n", " ").replace("\r", "").replace("\u000C", "").replace("\u200B", "")
                cs.showText(safeText)

                val runWidth = (font.getStringWidth(safeText) / 1000f) * run.fontSize
                currentX += runWidth
            } catch (e: Exception) {
                Timber.e(e, "Error drawing run: ${run.text}")
            }
        }
        cs.endText()

        var decorationX = startX
        for (run in runs) {
            val font = fontCache.getFont(run.fontPath, run.fontName, run.isBold, run.isItalic)
            val safeText = run.text.replace("\n", " ")
                .replace("\r", "")
                .replace("\u000C", "")
                .replace("\u200B", "")
            val runWidth = (font.getStringWidth(safeText) / 1000f) * run.fontSize

            if (run.isUnderline) {
                val r = android.graphics.Color.red(run.colorArgb) / 255f
                val g = android.graphics.Color.green(run.colorArgb) / 255f
                val b = android.graphics.Color.blue(run.colorArgb) / 255f
                cs.setStrokingColor(r, g, b)
                cs.setLineWidth(run.fontSize / 15f)
                cs.moveTo(decorationX, y - (run.fontSize * 0.15f))
                cs.lineTo(decorationX + runWidth, y - (run.fontSize * 0.15f))
                cs.stroke()
            }

            if (run.isStrikethrough) {
                val r = android.graphics.Color.red(run.colorArgb) / 255f
                val g = android.graphics.Color.green(run.colorArgb) / 255f
                val b = android.graphics.Color.blue(run.colorArgb) / 255f
                cs.setStrokingColor(r, g, b)
                cs.setLineWidth(run.fontSize / 15f)
                cs.moveTo(decorationX, y + (run.fontSize * 0.25f))
                cs.lineTo(decorationX + runWidth, y + (run.fontSize * 0.25f))
                cs.stroke()
            }

            decorationX += runWidth
        }
    }

    private fun getStyleAt(text: AnnotatedString, index: Int): SpanStyle {
        val styles = text.spanStyles.filter { index >= it.start && index < it.end }
        var style = SpanStyle()
        styles.forEach { style = style.merge(it.item) }
        return style
    }
}