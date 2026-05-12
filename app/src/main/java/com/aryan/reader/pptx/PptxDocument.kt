package com.aryan.reader.pptx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.aryan.reader.pdf.DummyTextPage
import com.aryan.reader.pdf.ReaderDocument
import com.aryan.reader.pdf.ReaderLink
import com.aryan.reader.pdf.ReaderPage
import com.aryan.reader.pdf.ReaderTextPage
import com.aryan.reader.pdf.ReaderTextRect
import io.legere.pdfiumandroid.api.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val PPTX_RENDERER_VERSION = 1
private const val EMU_PER_POINT = 12_700f
private const val DEFAULT_SLIDE_WIDTH_EMU = 12_192_000
private const val DEFAULT_SLIDE_HEIGHT_EMU = 6_858_000
private const val DEFAULT_TEXT_SIZE_PT = 18f
private const val DEFAULT_TEXT_MARGIN_PT = 91_440f / EMU_PER_POINT

internal data class PptxDeck(
    val widthPoint: Int,
    val heightPoint: Int,
    val slides: List<PptxSlide>
)

internal data class PptxSlide(
    val widthPoint: Int,
    val heightPoint: Int,
    val backgroundColor: Int?,
    val elements: List<PptxElement>,
    val text: String,
    val charBoxes: List<PptxCharBox>
)

internal data class PptxCharBox(
    val char: Char,
    val bounds: RectF
)

internal sealed interface PptxElement {
    val bounds: RectF
}

internal data class PptxShapeElement(
    override val bounds: RectF,
    val preset: String,
    val fillColor: Int?,
    val lineColor: Int?,
    val lineWidthPoint: Float,
    val paragraphs: List<PptxParagraph>,
    val hyperlink: String?,
    val placeholderKey: PptxPlaceholderKey?
) : PptxElement

internal data class PptxImageElement(
    override val bounds: RectF,
    val bytes: ByteArray,
    val contentType: String?
) : PptxElement

internal data class PptxParagraph(
    val runs: List<PptxTextRun>,
    val alignment: PptxTextAlign = PptxTextAlign.START,
    val bullet: String? = null
)

internal data class PptxTextRun(
    val text: String,
    val sizePt: Float? = null,
    val color: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val typeface: String? = null
)

internal enum class PptxTextAlign {
    START,
    CENTER,
    END
}

internal data class PptxPlaceholderKey(
    val type: String?,
    val index: String?
)

private data class PptxRelationships(
    val byId: Map<String, PptxRelationship>
)

private data class PptxRelationship(
    val id: String,
    val target: String,
    val resolvedTarget: String,
    val type: String,
    val targetMode: String?
)

private data class PptxTheme(
    val colors: Map<String, Int> = emptyMap()
)

private data class ParsedPart(
    val backgroundColor: Int? = null,
    val elements: List<PptxElement> = emptyList()
)

internal object PptxDeckCache {
    private const val MAX_ENTRIES = 4
    private val cache = object : LinkedHashMap<String, PptxDeck>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PptxDeck>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun load(file: File): PptxDeck {
        val key = "${file.contentHash()}:${file.length()}:$PPTX_RENDERER_VERSION"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val parsed = PptxDocumentParser.parse(file)
        synchronized(cache) {
            cache[key] = parsed
        }
        return parsed
    }
}

internal object PptxDocumentParser {
    fun parse(file: File): PptxDeck {
        ZipFile(file).use { zip ->
            val presentation = zip.xml("ppt/presentation.xml")
                ?: error("ppt/presentation.xml not found in PPTX archive.")
            val presentationRels = zip.relationshipsFor("ppt/presentation.xml")
            val width = (presentation.firstByLocalTag("sldSz")?.xmlFloat("cx") ?: DEFAULT_SLIDE_WIDTH_EMU.toFloat()).emuToPointInt()
            val height = (presentation.firstByLocalTag("sldSz")?.xmlFloat("cy") ?: DEFAULT_SLIDE_HEIGHT_EMU.toFloat()).emuToPointInt()
            val slidePaths = presentation.allByLocalTag("sldId")
                .mapNotNull { slideId -> slideId.xmlAttr("r:id") }
                .mapNotNull { relId -> presentationRels.byId[relId]?.resolvedTarget }
                .ifEmpty {
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.matches(Regex("""ppt/slides/slide\d+\.xml""")) }
                        .sortedWith(naturalSlidePathComparator())
                        .toList()
                }

            val slides = slidePaths.mapNotNull { slidePath ->
                runCatching { parseSlide(zip, slidePath, width, height) }
                    .onFailure { Timber.w(it, "Failed to parse PPTX slide $slidePath") }
                    .getOrNull()
            }

            return PptxDeck(
                widthPoint = width,
                heightPoint = height,
                slides = slides.ifEmpty {
                    listOf(
                        PptxSlide(
                            widthPoint = width,
                            heightPoint = height,
                            backgroundColor = Color.WHITE,
                            elements = emptyList(),
                            text = "",
                            charBoxes = emptyList()
                        )
                    )
                }
            )
        }
    }

    private fun parseSlide(zip: ZipFile, slidePath: String, width: Int, height: Int): PptxSlide {
        val slideXml = zip.xml(slidePath) ?: error("Missing slide part: $slidePath")
        val slideRels = zip.relationshipsFor(slidePath)
        val layoutPath = slideRels.byId.values
            .firstOrNull { it.type.endsWith("/slideLayout", ignoreCase = true) }
            ?.resolvedTarget
        val layoutRels = layoutPath?.let { zip.relationshipsFor(it) }
        val masterPath = layoutRels?.byId?.values
            ?.firstOrNull { it.type.endsWith("/slideMaster", ignoreCase = true) }
            ?.resolvedTarget
        val masterRels = masterPath?.let { zip.relationshipsFor(it) }
        val themePath = masterRels?.byId?.values
            ?.firstOrNull { it.type.endsWith("/theme", ignoreCase = true) }
            ?.resolvedTarget
        val theme = themePath?.let { path -> zip.xml(path)?.let(::parseTheme) } ?: PptxTheme()

        val master = masterPath?.let { path ->
            zip.xml(path)?.let { parsePart(zip, it, zip.relationshipsFor(path), theme, renderPlaceholderText = false) }
        } ?: ParsedPart()
        val layout = layoutPath?.let { path ->
            zip.xml(path)?.let { parsePart(zip, it, zip.relationshipsFor(path), theme, renderPlaceholderText = false) }
        } ?: ParsedPart()
        val slide = parsePart(zip, slideXml, slideRels, theme, renderPlaceholderText = true)
        val inheritedElements = master.elements + layout.elements
        val elements = inheritedElements + inheritPlaceholderBounds(slide.elements, inheritedElements)
        val backgroundColor = slide.backgroundColor ?: layout.backgroundColor ?: master.backgroundColor ?: Color.WHITE
        val textIndex = PptxTextIndexer.index(elements)

        return PptxSlide(
            widthPoint = width,
            heightPoint = height,
            backgroundColor = backgroundColor,
            elements = elements,
            text = textIndex.text,
            charBoxes = textIndex.charBoxes
        )
    }

    private fun parsePart(
        zip: ZipFile,
        document: Element,
        relationships: PptxRelationships,
        theme: PptxTheme,
        renderPlaceholderText: Boolean
    ): ParsedPart {
        val background = document.firstByLocalTag("bgPr")?.solidFillColor(theme)
            ?: document.firstByLocalTag("bgRef")?.schemeColor(theme)
        val elements = mutableListOf<PptxElement>()
        val tree = document.firstByLocalTag("spTree") ?: document
        tree.children().forEach { child ->
            parseDrawingElement(
                zip = zip,
                element = child,
                relationships = relationships,
                theme = theme,
                renderPlaceholderText = renderPlaceholderText,
                output = elements
            )
        }
        return ParsedPart(backgroundColor = background, elements = elements)
    }

    private fun parseDrawingElement(
        zip: ZipFile,
        element: Element,
        relationships: PptxRelationships,
        theme: PptxTheme,
        renderPlaceholderText: Boolean,
        output: MutableList<PptxElement>
    ) {
        when (element.localTag()) {
            "sp", "cxnsp" -> parseShape(element, relationships, theme, renderPlaceholderText)?.let(output::add)
            "pic" -> parseImage(zip, element, relationships)?.let(output::add)
            "grpsp" -> element.children().forEach { child ->
                parseDrawingElement(zip, child, relationships, theme, renderPlaceholderText, output)
            }
            "graphicframe" -> parseGraphicFrame(element, theme)?.let(output::add)
        }
    }

    private fun parseShape(
        element: Element,
        relationships: PptxRelationships,
        theme: PptxTheme,
        renderPlaceholderText: Boolean
    ): PptxShapeElement? {
        val spPr = element.childrenByLocalTag("spPr").firstOrNull()
        val bounds = spPr?.boundsFromTransform() ?: element.boundsFromTransform()
        val preset = spPr?.childrenByLocalTag("prstGeom")?.firstOrNull()?.xmlAttr("prst")
            ?: if (element.localTag() == "cxnsp") "line" else "rect"
        val placeholderKey = element.firstByLocalTag("ph")?.placeholderKey()
        val paragraphs = if (placeholderKey != null && !renderPlaceholderText) {
            emptyList()
        } else {
            parseTextBody(element.firstByLocalTag("txBody"), theme)
        }
        val fillColor = when {
            spPr?.firstDirectByLocalTag("noFill") != null -> null
            else -> spPr?.solidFillColor(theme)
        }
        val line = spPr?.childrenByLocalTag("ln")?.firstOrNull()
        val lineColor = line?.solidFillColor(theme)
        val lineWidth = line?.xmlFloat("w")?.emuToPoint() ?: 0.75f
        val hyperlink = element.firstByLocalTag("hlinkClick")
            ?.xmlAttr("r:id")
            ?.let { relationships.byId[it] }
            ?.let { rel -> if (rel.targetMode.equals("External", ignoreCase = true)) rel.target else rel.resolvedTarget }

        if (bounds.width() <= 0f && bounds.height() <= 0f && paragraphs.isEmpty()) return null
        return PptxShapeElement(
            bounds = bounds,
            preset = preset.lowercase(Locale.ROOT),
            fillColor = fillColor,
            lineColor = lineColor,
            lineWidthPoint = lineWidth,
            paragraphs = paragraphs,
            hyperlink = hyperlink,
            placeholderKey = placeholderKey
        )
    }

    private fun parseImage(
        zip: ZipFile,
        element: Element,
        relationships: PptxRelationships
    ): PptxImageElement? {
        val relId = element.firstByLocalTag("blip")?.xmlAttr("r:embed") ?: return null
        val rel = relationships.byId[relId] ?: return null
        val target = rel.resolvedTarget
        val entry = zip.getEntry(target) ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val bounds = element.childrenByLocalTag("spPr").firstOrNull()?.boundsFromTransform()
            ?: element.boundsFromTransform()
        return PptxImageElement(
            bounds = bounds,
            bytes = bytes,
            contentType = target.imageContentType()
        )
    }

    private fun parseGraphicFrame(element: Element, theme: PptxTheme): PptxElement? {
        val table = element.firstByLocalTag("tbl") ?: return null
        val bounds = element.boundsFromTransform()
        val rows = table.childrenByLocalTag("tr")
        val text = rows.joinToString("\n") { row ->
            row.childrenByLocalTag("tc")
                .joinToString("    ") { cell -> cell.allByLocalTag("t").joinToString("") { it.wholeText() } }
        }
        if (text.isBlank()) return null
        return PptxShapeElement(
            bounds = bounds,
            preset = "rect",
            fillColor = Color.TRANSPARENT,
            lineColor = theme.colors["tx1"] ?: Color.DKGRAY,
            lineWidthPoint = 0.75f,
            paragraphs = text.lines().map { line -> PptxParagraph(listOf(PptxTextRun(line))) },
            hyperlink = null,
            placeholderKey = null
        )
    }

    private fun parseTextBody(txBody: Element?, theme: PptxTheme): List<PptxParagraph> {
        if (txBody == null) return emptyList()
        return txBody.childrenByLocalTag("p").mapNotNull { paragraph ->
            val pPr = paragraph.childrenByLocalTag("pPr").firstOrNull()
            val defaultRunPr = pPr?.childrenByLocalTag("defRPr")?.firstOrNull()
            val defaultSize = defaultRunPr?.xmlFloat("sz")?.let { it / 100f }
            val defaultColor = defaultRunPr?.solidFillColor(theme)
            val alignment = when (pPr?.xmlAttr("algn")) {
                "ctr" -> PptxTextAlign.CENTER
                "r" -> PptxTextAlign.END
                else -> PptxTextAlign.START
            }
            val bullet = pPr?.firstByLocalTag("buChar")?.xmlAttr("char")
            val runs = mutableListOf<PptxTextRun>()
            paragraph.children().forEach { child ->
                when (child.localTag()) {
                    "r", "fld" -> {
                        val rPr = child.childrenByLocalTag("rPr").firstOrNull()
                        val text = child.firstByLocalTag("t")?.wholeText().orEmpty()
                        if (text.isNotEmpty()) {
                            runs += PptxTextRun(
                                text = text,
                                sizePt = rPr?.xmlFloat("sz")?.let { it / 100f } ?: defaultSize,
                                color = rPr?.solidFillColor(theme) ?: defaultColor,
                                bold = rPr?.xmlAttr("b").isTruthyXmlFlag(),
                                italic = rPr?.xmlAttr("i").isTruthyXmlFlag(),
                                typeface = rPr?.firstByLocalTag("latin")?.xmlAttr("typeface")
                            )
                        }
                    }
                    "br" -> runs += PptxTextRun("\n", sizePt = defaultSize, color = defaultColor)
                }
            }
            val safeRuns = runs.ifEmpty { listOf(PptxTextRun("")) }
            PptxParagraph(safeRuns, alignment, bullet).takeIf { paragraph ->
                paragraph.runs.any { it.text.isNotBlank() }
            }
        }
    }

    private fun parseTheme(document: Element): PptxTheme {
        val scheme = document.firstByLocalTag("clrScheme") ?: return PptxTheme()
        val colors = scheme.children().mapNotNull { colorNode ->
            val value = colorNode.firstByLocalTag("srgbClr")?.xmlAttr("val")?.toColorOrNull()
                ?: colorNode.firstByLocalTag("sysClr")?.xmlAttr("lastClr")?.toColorOrNull()
            value?.let { colorNode.localTag() to it }
        }.toMap()
        val aliases = buildMap {
            putAll(colors)
            colors["lt1"]?.let { put("bg1", it) }
            colors["dk1"]?.let { put("tx1", it) }
            colors["lt2"]?.let { put("bg2", it) }
            colors["dk2"]?.let { put("tx2", it) }
        }
        return PptxTheme(aliases)
    }

    private fun ZipFile.xml(path: String): Element? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).use { input ->
            Jsoup.parse(input, null, "", Parser.xmlParser())
        }
    }

    private fun ZipFile.relationshipsFor(partPath: String): PptxRelationships {
        val relsPath = partPath.relationshipsPath()
        val document = xml(relsPath) ?: return PptxRelationships(emptyMap())
        val rels = document.allByLocalTag("Relationship").mapNotNull { rel ->
            val id = rel.xmlAttr("Id") ?: return@mapNotNull null
            val target = rel.xmlAttr("Target") ?: return@mapNotNull null
            val type = rel.xmlAttr("Type").orEmpty()
            PptxRelationship(
                id = id,
                target = target,
                resolvedTarget = resolveRelationshipTarget(partPath, target, rel.xmlAttr("TargetMode")),
                type = type,
                targetMode = rel.xmlAttr("TargetMode")
            )
        }.associateBy { it.id }
        return PptxRelationships(rels)
    }
}

private fun inheritPlaceholderBounds(
    slideElements: List<PptxElement>,
    inheritedElements: List<PptxElement>
): List<PptxElement> {
    val inheritedPlaceholders = inheritedElements
        .filterIsInstance<PptxShapeElement>()
        .filter { it.placeholderKey != null && it.bounds.width() > 0f && it.bounds.height() > 0f }

    if (inheritedPlaceholders.isEmpty()) return slideElements

    return slideElements.map { element ->
        val shape = element as? PptxShapeElement ?: return@map element
        val key = shape.placeholderKey ?: return@map shape
        if (shape.bounds.width() > 0f && shape.bounds.height() > 0f) return@map shape

        val inherited = inheritedPlaceholders.lastOrNull { inherited ->
            inherited.placeholderKey?.matches(key) == true
        } ?: return@map shape

        shape.copy(
            bounds = RectF(inherited.bounds),
            preset = if (shape.preset == "rect") inherited.preset else shape.preset,
            fillColor = shape.fillColor ?: inherited.fillColor,
            lineColor = shape.lineColor ?: inherited.lineColor,
            lineWidthPoint = if (shape.lineWidthPoint == 0.75f) inherited.lineWidthPoint else shape.lineWidthPoint
        )
    }
}

internal class PptxDocumentWrapper(
    private val file: File,
    private val deleteOnClose: Boolean = false
) : ReaderDocument {
    private val deck: PptxDeck by lazy { PptxDeckCache.load(file) }

    override suspend fun getPageCount(): Int = deck.slides.size

    override suspend fun openPage(pageIndex: Int): ReaderPage? {
        return deck.slides.getOrNull(pageIndex)?.let(::PptxPageWrapper)
    }

    override suspend fun getTableOfContents(): List<Bookmark> = emptyList()

    override fun close() {
        if (deleteOnClose) {
            runCatching { file.delete() }
        }
    }
}

internal class PptxPageWrapper(
    private val slide: PptxSlide
) : ReaderPage {
    override suspend fun getPageWidthPoint(): Int = slide.widthPoint
    override suspend fun getPageHeightPoint(): Int = slide.heightPoint
    override suspend fun getPageRotation(): Int = 0

    override suspend fun renderPageBitmap(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        drawSizeX: Int,
        drawSizeY: Int,
        renderAnnot: Boolean
    ) {
        withContext(Dispatchers.Default) {
            PptxSlideRenderer.render(slide, bitmap, startX, startY, drawSizeX, drawSizeY)
        }
    }

    override suspend fun mapRectToDevice(
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        coords: RectF
    ): Rect {
        val scaleX = sizeX.toFloat() / slide.widthPoint.toFloat().coerceAtLeast(1f)
        val scaleY = sizeY.toFloat() / slide.heightPoint.toFloat().coerceAtLeast(1f)
        return Rect(
            (startX + coords.left * scaleX).roundToInt(),
            (startY + coords.top * scaleY).roundToInt(),
            (startX + coords.right * scaleX).roundToInt(),
            (startY + coords.bottom * scaleY).roundToInt()
        )
    }

    override suspend fun mapDeviceCoordsToPage(
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        deviceX: Int,
        deviceY: Int
    ): PointF {
        val scaleX = sizeX.toFloat() / slide.widthPoint.toFloat().coerceAtLeast(1f)
        val scaleY = sizeY.toFloat() / slide.heightPoint.toFloat().coerceAtLeast(1f)
        return PointF(
            (deviceX - startX) / scaleX,
            (deviceY - startY) / scaleY
        )
    }

    override suspend fun openTextPage(): ReaderTextPage {
        return if (slide.text.isBlank()) DummyTextPage() else PptxTextPage(slide)
    }

    override suspend fun getLinks(): List<ReaderLink> {
        return slide.elements.mapNotNull { element ->
            val shape = element as? PptxShapeElement ?: return@mapNotNull null
            val link = shape.hyperlink?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ReaderLink(uri = link, destPageIdx = null, bounds = RectF(shape.bounds))
        }
    }

    override fun getNativePointer(): Long = 0L
    override fun close() = Unit
}

internal class PptxTextPage(
    private val slide: PptxSlide
) : ReaderTextPage {
    override suspend fun textPageCountChars(): Int = slide.text.length

    override suspend fun textPageGetText(startIndex: Int, count: Int): String? {
        if (count <= 0 || startIndex !in 0..slide.text.length) return ""
        val end = (startIndex + count).coerceAtMost(slide.text.length)
        return slide.text.substring(startIndex, end)
    }

    override suspend fun textPageGetRectsForRanges(ranges: IntArray): List<ReaderTextRect>? {
        if (ranges.size < 2) return emptyList()
        val rects = mutableListOf<ReaderTextRect>()
        var index = 0
        while (index + 1 < ranges.size) {
            val start = ranges[index].coerceIn(0, slide.charBoxes.size)
            val length = ranges[index + 1].coerceAtLeast(0)
            val end = (start + length).coerceAtMost(slide.charBoxes.size)
            val lineRects = slide.charBoxes.subList(start, end)
                .filter { !it.char.isWhitespace() }
                .groupBy { it.bounds.top.roundToInt() }
                .values
                .mapNotNull { boxes ->
                    boxes.fold<PptxCharBox, RectF?>(null) { acc, box ->
                        if (acc == null) RectF(box.bounds) else acc.apply { union(box.bounds) }
                    }
                }
            rects += lineRects.map(::ReaderTextRect)
            index += 2
        }
        return rects
    }

    override suspend fun textPageGetCharIndexAtPos(
        x: Double,
        y: Double,
        xTolerance: Double,
        yTolerance: Double
    ): Int {
        val pointX = x.toFloat()
        val pointY = y.toFloat()
        val expanded = RectF()
        slide.charBoxes.forEachIndexed { index, box ->
            expanded.set(box.bounds)
            expanded.inset(-xTolerance.toFloat(), -yTolerance.toFloat())
            if (expanded.contains(pointX, pointY)) return index
        }
        return -1
    }

    override suspend fun textPageGetCharBox(index: Int): RectF? {
        return slide.charBoxes.getOrNull(index)?.bounds?.let(::RectF)
    }

    override suspend fun textPageGetUnicode(index: Int): Int {
        return slide.text.getOrNull(index)?.code ?: 0
    }

    override suspend fun loadWebLink() = null
    override fun close() = Unit
}

internal class PptxCoverGenerator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generateCover(uri: Uri, targetHeight: Int = 800): Bitmap? = withContext(Dispatchers.IO) {
        val cacheFile = File(appContext.cacheDir, "pptx_cover_${System.currentTimeMillis()}.pptx")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            PptxDocumentWrapper(cacheFile, deleteOnClose = true).use { doc ->
                val page = doc.openPage(0) ?: return@withContext null
                page.use {
                    val width = it.getPageWidthPoint()
                    val height = it.getPageHeightPoint()
                    if (width <= 0 || height <= 0) return@withContext null
                    val targetWidth = (targetHeight * (width.toFloat() / height.toFloat())).roundToInt().coerceAtLeast(1)
                    val bitmap = createBitmap(targetWidth, targetHeight)
                    it.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false)
                    bitmap
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate PPTX cover")
            null
        } finally {
            runCatching { cacheFile.delete() }
        }
    }
}

private data class PptxTextIndex(
    val text: String,
    val charBoxes: List<PptxCharBox>
)

private object PptxTextIndexer {
    fun index(elements: List<PptxElement>): PptxTextIndex {
        val text = StringBuilder()
        val charBoxes = mutableListOf<PptxCharBox>()
        elements.forEach { element ->
            val shape = element as? PptxShapeElement ?: return@forEach
            val layout = layoutParagraphs(shape, shape.bounds)
            layout.forEach { laidOut ->
                val globalStart = text.length
                text.append(laidOut.text)
                laidOut.charBoxes.forEachIndexed { localIndex, rect ->
                    charBoxes += PptxCharBox(
                        char = laidOut.text.getOrElse(localIndex) { ' ' },
                        bounds = rect
                    )
                }
                if (globalStart < text.length && text.lastOrNull() != '\n') {
                    text.append('\n')
                    charBoxes += PptxCharBox('\n', RectF(shape.bounds.left, shape.bounds.bottom, shape.bounds.left, shape.bounds.bottom))
                }
            }
        }
        val indexedText = text.toString().trimEnd()
        return PptxTextIndex(indexedText, charBoxes.take(indexedText.length))
    }
}

private data class LaidOutParagraph(
    val text: String,
    val lines: List<LaidOutLine>,
    val charBoxes: List<RectF>
)

private data class LaidOutLine(
    val text: String,
    val x: Float,
    val baseline: Float,
    val fontSizePoint: Float,
    val run: PptxTextRun
)

private object PptxSlideRenderer {
    fun render(slide: PptxSlide, bitmap: Bitmap, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(slide.backgroundColor ?: Color.WHITE)
        if (slide.widthPoint <= 0 || slide.heightPoint <= 0) return
        canvas.save()
        canvas.translate(startX.toFloat(), startY.toFloat())
        canvas.scale(drawSizeX.toFloat() / slide.widthPoint, drawSizeY.toFloat() / slide.heightPoint)

        slide.elements.forEach { element ->
            when (element) {
                is PptxShapeElement -> drawShape(canvas, element)
                is PptxImageElement -> drawImage(canvas, element)
            }
        }

        canvas.restore()
    }

    private fun drawShape(canvas: Canvas, shape: PptxShapeElement) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = shape.fillColor ?: Color.TRANSPARENT
        }
        if (shape.fillColor != null && Color.alpha(shape.fillColor) > 0) {
            when (shape.preset) {
                "ellipse" -> canvas.drawOval(shape.bounds, fillPaint)
                "roundrect" -> canvas.drawRoundRect(shape.bounds, shape.bounds.width() * 0.08f, shape.bounds.height() * 0.08f, fillPaint)
                "triangle" -> canvas.drawPath(Path().apply {
                    moveTo(shape.bounds.centerX(), shape.bounds.top)
                    lineTo(shape.bounds.right, shape.bounds.bottom)
                    lineTo(shape.bounds.left, shape.bounds.bottom)
                    close()
                }, fillPaint)
                else -> canvas.drawRect(shape.bounds, fillPaint)
            }
        }

        val strokeColor = shape.lineColor
        if (strokeColor != null && Color.alpha(strokeColor) > 0) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = strokeColor
                strokeWidth = shape.lineWidthPoint.coerceAtLeast(0.25f)
            }
            if (shape.preset == "line") {
                canvas.drawLine(shape.bounds.left, shape.bounds.top, shape.bounds.right, shape.bounds.bottom, strokePaint)
            } else {
                canvas.drawRect(shape.bounds, strokePaint)
            }
        }

        drawText(canvas, shape)
    }

    private fun drawText(canvas: Canvas, shape: PptxShapeElement) {
        val layout = layoutParagraphs(shape, shape.bounds)
        layout.flatMap { it.lines }.forEach { line ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line.run.color ?: Color.BLACK
                textSize = line.fontSizePoint
                typeface = Typeface.create(
                    line.run.typeface ?: "sans-serif",
                    when {
                        line.run.bold && line.run.italic -> Typeface.BOLD_ITALIC
                        line.run.bold -> Typeface.BOLD
                        line.run.italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                )
            }
            canvas.drawText(line.text, line.x, line.baseline, paint)
        }
    }

    private fun drawImage(canvas: Canvas, image: PptxImageElement) {
        val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, image.bounds, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.LTGRAY
            }
            canvas.drawRect(image.bounds, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.GRAY
            paint.strokeWidth = 0.75f
            canvas.drawRect(image.bounds, paint)
        }
    }
}

private fun layoutParagraphs(shape: PptxShapeElement, bounds: RectF): List<LaidOutParagraph> {
    if (shape.paragraphs.isEmpty() || bounds.width() <= 0f || bounds.height() <= 0f) return emptyList()
    val result = mutableListOf<LaidOutParagraph>()
    var y = bounds.top + DEFAULT_TEXT_MARGIN_PT
    val maxWidth = (bounds.width() - DEFAULT_TEXT_MARGIN_PT * 2f).coerceAtLeast(bounds.width() * 0.6f)
    shape.paragraphs.forEach { paragraph ->
        val paragraphText = paragraph.runs.joinToString("") { it.text }
        if (paragraphText.isBlank()) {
            y += DEFAULT_TEXT_SIZE_PT
            return@forEach
        }
        val firstRun = paragraph.runs.firstOrNull { it.text.isNotEmpty() } ?: PptxTextRun("")
        val fontSizePoint = firstRun.sizePt ?: DEFAULT_TEXT_SIZE_PT
        val lineHeight = fontSizePoint * 1.22f
        val words = paragraphText.replace('\n', ' ').split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = paragraph.bullet?.let { "$it " }.orEmpty()
        words.forEach { word ->
            val candidate = if (current.isBlank()) word else "$current $word"
            if (estimateTextWidth(candidate, fontSizePoint) > maxWidth && current.isNotBlank()) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotBlank()) lines += current

        val laidOutLines = mutableListOf<LaidOutLine>()
        val charBoxes = mutableListOf<RectF>()
        val paragraphTextOut = StringBuilder()
        lines.forEachIndexed { index, lineText ->
            val lineWidth = estimateTextWidth(lineText, fontSizePoint)
            val x = when (paragraph.alignment) {
                PptxTextAlign.CENTER -> bounds.left + (bounds.width() - lineWidth) / 2f
                PptxTextAlign.END -> bounds.right - DEFAULT_TEXT_MARGIN_PT - lineWidth
                PptxTextAlign.START -> bounds.left + DEFAULT_TEXT_MARGIN_PT
            }
            val baseline = y + fontSizePoint
            laidOutLines += LaidOutLine(lineText, x, baseline, fontSizePoint, firstRun)
            var charX = x
            lineText.forEach { char ->
                val charWidth = estimateTextWidth(char.toString(), fontSizePoint)
                charBoxes += RectF(charX, y, charX + charWidth, y + lineHeight)
                charX += charWidth
                paragraphTextOut.append(char)
            }
            if (index < lines.lastIndex) {
                paragraphTextOut.append('\n')
                charBoxes += RectF(charX, y, charX, y + lineHeight)
            }
            y += lineHeight
        }
        y += lineHeight * 0.22f
        result += LaidOutParagraph(paragraphTextOut.toString(), laidOutLines, charBoxes)
    }
    return result
}

private fun estimateTextWidth(text: String, fontSizePoint: Float): Float {
    return text.sumOf { char ->
        when {
            char == ' ' -> fontSizePoint * 0.32f
            char.code > 0x2E80 -> fontSizePoint
            char.isUpperCase() -> fontSizePoint * 0.62f
            else -> fontSizePoint * 0.54f
        }.toDouble()
    }.toFloat()
}

private fun Element.boundsFromTransform(): RectF {
    val xfrm = childrenByLocalTag("xfrm").firstOrNull() ?: firstByLocalTag("xfrm")
    val off = xfrm?.childrenByLocalTag("off")?.firstOrNull()
    val ext = xfrm?.childrenByLocalTag("ext")?.firstOrNull()
    val x = off?.xmlFloat("x")?.emuToPoint() ?: 0f
    val y = off?.xmlFloat("y")?.emuToPoint() ?: 0f
    val cx = ext?.xmlFloat("cx")?.emuToPoint() ?: 0f
    val cy = ext?.xmlFloat("cy")?.emuToPoint() ?: 0f
    return RectF(x, y, x + cx, y + cy)
}

private fun Float.emuToPoint(): Float = this / EMU_PER_POINT

private fun Float.emuToPointInt(): Int = emuToPoint().roundToInt().coerceAtLeast(1)

private fun Element.solidFillColor(theme: PptxTheme): Int? {
    val solid = childrenByLocalTag("solidFill").firstOrNull() ?: return null
    solid.firstByLocalTag("srgbClr")?.let { color ->
        return color.xmlAttr("val")?.toColorOrNull()?.applyLuminance(color)
    }
    solid.firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.colors[scheme]?.applyLuminance(color)
    }
    solid.firstByLocalTag("sysClr")?.let { color ->
        return color.xmlAttr("lastClr")?.toColorOrNull()?.applyLuminance(color)
    }
    return null
}

private fun Element.schemeColor(theme: PptxTheme): Int? {
    firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.colors[scheme]?.applyLuminance(color)
    }
    return xmlAttr("idx")?.let { theme.colors[it] }
}

private fun Int.applyLuminance(colorElement: Element): Int {
    val mod = colorElement.firstByLocalTag("lumMod")?.xmlFloat("val")?.let { it / 100_000f } ?: 1f
    val off = colorElement.firstByLocalTag("lumOff")?.xmlFloat("val")?.let { it / 100_000f } ?: 0f
    fun channel(value: Int): Int = ((value * mod) + (255f * off)).roundToInt().coerceIn(0, 255)
    return Color.argb(Color.alpha(this), channel(Color.red(this)), channel(Color.green(this)), channel(Color.blue(this)))
}

private fun String.toColorOrNull(): Int? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6) return null
    return runCatching { Color.rgb(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16)) }.getOrNull()
}

private fun File.contentHash(): String {
    return runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }.getOrElse {
        Timber.w(it, "Falling back to path-based PPTX cache key")
        "${canonicalPath}:${lastModified()}"
    }
}

private fun String?.isTruthyXmlFlag(): Boolean {
    return this == "1" || equals("true", ignoreCase = true)
}

private fun Element.localTag(): String = tagName().substringAfter(':').lowercase(Locale.ROOT)

private fun Element.xmlAttr(name: String): String? {
    if (":" in name) {
        return attributes().asList()
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }
    val expectedLocal = name.substringAfter(':')
    for (attribute in attributes().asList()) {
        val key = attribute.key
        if (key.equals(name, ignoreCase = true) || key.substringAfter(':').equals(expectedLocal, ignoreCase = true)) {
            return attribute.value.takeIf { it.isNotBlank() }
        }
    }
    return null
}

private fun Element.xmlInt(name: String): Int? = xmlAttr(name)?.toIntOrNull()
private fun Element.xmlFloat(name: String): Float? = xmlAttr(name)?.toFloatOrNull()

private fun Element.placeholderKey(): PptxPlaceholderKey {
    return PptxPlaceholderKey(
        type = xmlAttr("type")?.lowercase(Locale.ROOT),
        index = xmlAttr("idx")
    )
}

private fun PptxPlaceholderKey.matches(other: PptxPlaceholderKey): Boolean {
    if (index != null && other.index != null && index == other.index) return true
    if (type != null && other.type != null && type == other.type) return true
    return index == null && other.index == null && type == null && other.type == null
}

private fun Element.childrenByLocalTag(tag: String): List<Element> {
    val local = tag.lowercase(Locale.ROOT)
    return children().filter { it.localTag() == local }
}

private fun Element.firstDirectByLocalTag(tag: String): Element? = childrenByLocalTag(tag).firstOrNull()

private fun Element.firstByLocalTag(tag: String): Element? {
    val local = tag.lowercase(Locale.ROOT)
    return getAllElements().firstOrNull { it.localTag() == local }
}

private fun Element.allByLocalTag(tag: String): List<Element> {
    val local = tag.lowercase(Locale.ROOT)
    return getAllElements().filter { it.localTag() == local }
}

private fun String.relationshipsPath(): String {
    val dir = substringBeforeLast('/', missingDelimiterValue = "")
    val name = substringAfterLast('/')
    return if (dir.isBlank()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
}

private fun resolveRelationshipTarget(partPath: String, target: String, targetMode: String?): String {
    if (targetMode.equals("External", ignoreCase = true)) return target
    val cleanTarget = target.substringBefore('#').removePrefix("/")
    val base = partPath.substringBeforeLast('/', missingDelimiterValue = "")
    return normalizePartPath(if (target.startsWith("/")) cleanTarget else "$base/$cleanTarget")
}

private fun normalizePartPath(path: String): String {
    val clean = path.removePrefix("/")
    val parts = ArrayDeque<String>()
    clean.split('/').forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> {
                if (parts.isNotEmpty()) parts.removeLast()
            }
            else -> parts.addLast(part)
        }
    }
    return parts.joinToString("/")
}

private fun String.imageContentType(): String {
    return when (substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }
}

private fun naturalSlidePathComparator(): Comparator<String> {
    return compareBy { path ->
        Regex("""slide(\d+)\.xml""").find(path)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
    }
}
