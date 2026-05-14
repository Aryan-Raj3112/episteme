package com.aryan.reader.pptx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation

internal const val PPTX_RENDERER_VERSION = 6
private const val EMU_PER_POINT = 12_700f
private const val DEFAULT_SLIDE_WIDTH_EMU = 12_192_000
private const val DEFAULT_SLIDE_HEIGHT_EMU = 6_858_000
private const val DEFAULT_TEXT_SIZE_PT = 18f
private const val DEFAULT_TEXT_MARGIN_PT = 91_440f / EMU_PER_POINT
private const val DEFAULT_LINE_SPACING_MULTIPLE = 1.0f
private const val MAX_NUMBERING_LEVELS = 9

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
    val gradientFill: PptxGradientFill? = null,
    val lineColor: Int?,
    val lineWidthPoint: Float,
    val paragraphs: List<PptxParagraph>,
    val hyperlink: String?,
    val placeholderKey: PptxPlaceholderKey?,
    val textInsets: PptxTextInsets = PptxTextInsets(),
    val verticalAnchor: PptxVerticalAnchor = PptxVerticalAnchor.TOP,
    val rotationDegrees: Float = 0f,
    val renderText: Boolean = true,
    val fontScale: Float = 1f,
    val lineSpacingReduction: Float = 0f,
    val autoFitMode: PptxAutoFitMode = PptxAutoFitMode.NONE,
    val customGeometry: PptxCustomGeometry? = null
) : PptxElement

internal data class PptxImageElement(
    override val bounds: RectF,
    val bytes: ByteArray,
    val contentType: String?,
    val crop: PptxImageCrop = PptxImageCrop(),
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
) : PptxElement

internal data class PptxImageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

internal data class PptxTableElement(
    override val bounds: RectF,
    val rows: List<PptxTableRow>,
    val rotationDegrees: Float = 0f
) : PptxElement

internal data class PptxTableRow(
    val heightPoint: Float?,
    val cells: List<PptxTableCell>
)

internal data class PptxTableCell(
    val widthPoint: Float?,
    val fillColor: Int?,
    val lineColor: Int?,
    val paragraphs: List<PptxParagraph>,
    val textInsets: PptxTextInsets = PptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f),
    val verticalAnchor: PptxVerticalAnchor = PptxVerticalAnchor.TOP
)

internal data class PptxParagraph(
    val runs: List<PptxTextRun>,
    val alignment: PptxTextAlign = PptxTextAlign.START,
    val bullet: String? = null,
    val level: Int = 0,
    val marginLeftPt: Float? = null,
    val indentPt: Float? = null,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    val lineSpacingMultiple: Float = DEFAULT_LINE_SPACING_MULTIPLE,
    val alignmentExplicit: Boolean = false,
    val bulletExplicit: Boolean = false,
    val spaceBeforeExplicit: Boolean = false,
    val spaceAfterExplicit: Boolean = false,
    val lineSpacingExplicit: Boolean = false
)

internal data class PptxTextRun(
    val text: String,
    val sizePt: Float? = null,
    val color: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val typeface: String? = null,
    val baseline: Float = 0f,
    val sizeExplicit: Boolean = false,
    val colorExplicit: Boolean = false,
    val boldExplicit: Boolean = false,
    val italicExplicit: Boolean = false,
    val typefaceExplicit: Boolean = false,
    val baselineExplicit: Boolean = false
)

internal enum class PptxTextAlign {
    START,
    CENTER,
    END
}

internal enum class PptxVerticalAnchor {
    TOP,
    MIDDLE,
    BOTTOM
}

internal enum class PptxAutoFitMode {
    NONE,
    NORMAL,
    SHAPE
}

internal data class PptxTextInsets(
    val left: Float = DEFAULT_TEXT_MARGIN_PT,
    val top: Float = DEFAULT_TEXT_MARGIN_PT,
    val right: Float = DEFAULT_TEXT_MARGIN_PT,
    val bottom: Float = DEFAULT_TEXT_MARGIN_PT
)

internal data class PptxGradientFill(
    val startColor: Int,
    val endColor: Int,
    val angleDegrees: Float = 0f
)

internal data class PptxCustomGeometry(
    val width: Float,
    val height: Float,
    val commands: List<PptxPathCommand>
) {
    fun toPath(bounds: RectF): Path {
        val scaleX = bounds.width() / width.coerceAtLeast(1f)
        val scaleY = bounds.height() / height.coerceAtLeast(1f)
        fun x(value: Float) = bounds.left + value * scaleX
        fun y(value: Float) = bounds.top + value * scaleY
        return Path().apply {
            commands.forEach { command ->
                when (command) {
                    is PptxPathCommand.MoveTo -> moveTo(x(command.x), y(command.y))
                    is PptxPathCommand.LineTo -> lineTo(x(command.x), y(command.y))
                    is PptxPathCommand.QuadTo -> quadTo(
                        x(command.x1),
                        y(command.y1),
                        x(command.x2),
                        y(command.y2)
                    )
                    is PptxPathCommand.CubicTo -> cubicTo(
                        x(command.x1),
                        y(command.y1),
                        x(command.x2),
                        y(command.y2),
                        x(command.x3),
                        y(command.y3)
                    )
                    PptxPathCommand.Close -> close()
                }
            }
        }
    }
}

internal sealed interface PptxPathCommand {
    data class MoveTo(val x: Float, val y: Float) : PptxPathCommand
    data class LineTo(val x: Float, val y: Float) : PptxPathCommand
    data class QuadTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : PptxPathCommand
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x3: Float,
        val y3: Float
    ) : PptxPathCommand
    object Close : PptxPathCommand
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
    val colors: Map<String, Int> = emptyMap(),
    val majorTypeface: String? = null,
    val minorTypeface: String? = null
) {
    fun color(name: String): Int? {
        return colors[name] ?: colors[name.lowercase(Locale.ROOT)]
    }
}

private data class ParsedPart(
    val backgroundColor: Int? = null,
    val elements: List<PptxElement> = emptyList(),
    val textDefaults: PptxTextDefaults = PptxTextDefaults()
)

private data class PptxTextDefaults(
    val title: Map<Int, PptxParagraphStyle> = emptyMap(),
    val body: Map<Int, PptxParagraphStyle> = emptyMap(),
    val other: Map<Int, PptxParagraphStyle> = emptyMap()
) {
    fun merge(override: PptxTextDefaults): PptxTextDefaults {
        return PptxTextDefaults(
            title = title.mergeStyles(override.title),
            body = body.mergeStyles(override.body),
            other = other.mergeStyles(override.other)
        )
    }

    fun forPlaceholder(key: PptxPlaceholderKey?): Map<Int, PptxParagraphStyle> {
        return when (key?.type?.placeholderFamily()) {
            "title" -> title
            "body", null -> if (key != null) body else other
            "subtitle" -> other.ifEmpty { body }
            else -> other
        }
    }
}

private data class PptxParagraphStyle(
    val alignment: PptxTextAlign? = null,
    val bullet: String? = null,
    val autoNumberType: String? = null,
    val autoNumberStartAt: Int? = null,
    val bulletExplicit: Boolean = false,
    val marginLeftPt: Float? = null,
    val indentPt: Float? = null,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val lineSpacingMultiple: Float? = null,
    val run: PptxRunStyle = PptxRunStyle()
) {
    fun merge(override: PptxParagraphStyle): PptxParagraphStyle {
        return PptxParagraphStyle(
            alignment = override.alignment ?: alignment,
            bullet = if (override.bulletExplicit) override.bullet else bullet,
            autoNumberType = if (override.bulletExplicit) override.autoNumberType else autoNumberType,
            autoNumberStartAt = if (override.bulletExplicit) override.autoNumberStartAt else autoNumberStartAt,
            bulletExplicit = bulletExplicit || override.bulletExplicit,
            marginLeftPt = override.marginLeftPt ?: marginLeftPt,
            indentPt = override.indentPt ?: indentPt,
            spaceBeforePt = override.spaceBeforePt ?: spaceBeforePt,
            spaceAfterPt = override.spaceAfterPt ?: spaceAfterPt,
            lineSpacingMultiple = override.lineSpacingMultiple ?: lineSpacingMultiple,
            run = run.merge(override.run)
        )
    }
}

private data class PptxRunStyle(
    val sizePt: Float? = null,
    val color: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val typeface: String? = null,
    val baseline: Float? = null
) {
    fun merge(override: PptxRunStyle): PptxRunStyle {
        return PptxRunStyle(
            sizePt = override.sizePt ?: sizePt,
            color = override.color ?: color,
            bold = override.bold ?: bold,
            italic = override.italic ?: italic,
            typeface = override.typeface ?: typeface,
            baseline = override.baseline ?: baseline
        )
    }
}

private data class PptxTableStyle(
    val whole: PptxTableCellStyle = PptxTableCellStyle(),
    val firstRow: PptxTableCellStyle = PptxTableCellStyle(),
    val lastRow: PptxTableCellStyle = PptxTableCellStyle(),
    val firstColumn: PptxTableCellStyle = PptxTableCellStyle(),
    val lastColumn: PptxTableCellStyle = PptxTableCellStyle(),
    val band1Horizontal: PptxTableCellStyle = PptxTableCellStyle(),
    val band2Horizontal: PptxTableCellStyle = PptxTableCellStyle()
) {
    fun cellStyle(
        rowIndex: Int,
        columnIndex: Int,
        rowCount: Int,
        columnCount: Int,
        options: PptxTableStyleOptions
    ): PptxTableCellStyle {
        var style = whole
        if (options.bandRow) {
            val bandIndex = rowIndex - if (options.firstRow) 1 else 0
            if (bandIndex >= 0) {
                style = style.merge(if (bandIndex % 2 == 0) band1Horizontal else band2Horizontal)
            }
        }
        if (options.firstRow && rowIndex == 0) style = style.merge(firstRow)
        if (options.lastRow && rowIndex == rowCount - 1) style = style.merge(lastRow)
        if (options.firstColumn && columnIndex == 0) style = style.merge(firstColumn)
        if (options.lastColumn && columnIndex == columnCount - 1) style = style.merge(lastColumn)
        return style
    }
}

private data class PptxTableCellStyle(
    val fillColor: Int? = null,
    val lineColor: Int? = null,
    val run: PptxRunStyle = PptxRunStyle()
) {
    fun merge(override: PptxTableCellStyle): PptxTableCellStyle {
        return PptxTableCellStyle(
            fillColor = override.fillColor ?: fillColor,
            lineColor = override.lineColor ?: lineColor,
            run = run.merge(override.run)
        )
    }
}

private data class PptxTableStyleOptions(
    val firstRow: Boolean = false,
    val lastRow: Boolean = false,
    val firstColumn: Boolean = false,
    val lastColumn: Boolean = false,
    val bandRow: Boolean = false
)

private data class PptxGroupTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotationDegrees: Float = 0f
) {
    fun then(child: PptxGroupTransform): PptxGroupTransform {
        return PptxGroupTransform(
            scaleX = scaleX * child.scaleX,
            scaleY = scaleY * child.scaleY,
            dx = dx + child.dx * scaleX,
            dy = dy + child.dy * scaleY,
            rotationDegrees = rotationDegrees + child.rotationDegrees
        )
    }

    fun apply(element: PptxElement): PptxElement {
        if (this == IDENTITY) return element
        return when (element) {
            is PptxShapeElement -> element.copy(
                bounds = mapRect(element.bounds),
                lineWidthPoint = element.lineWidthPoint * averageScale(),
                rotationDegrees = element.rotationDegrees + rotationDegrees
            )
            is PptxImageElement -> element.copy(
                bounds = mapRect(element.bounds),
                rotationDegrees = element.rotationDegrees + rotationDegrees
            )
            is PptxTableElement -> element.copy(
                bounds = mapRect(element.bounds),
                rotationDegrees = element.rotationDegrees + rotationDegrees
            )
        }
    }

    private fun mapRect(rect: RectF): RectF {
        val left = rect.left * scaleX + dx
        val right = rect.right * scaleX + dx
        val top = rect.top * scaleY + dy
        val bottom = rect.bottom * scaleY + dy
        return RectF(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
    }

    private fun averageScale(): Float = ((scaleX + scaleY) / 2f).coerceAtLeast(0.01f)

    companion object {
        val IDENTITY = PptxGroupTransform()

        fun fromGroup(group: Element): PptxGroupTransform {
            val xfrm = group.childrenByLocalTag("grpSpPr")
                .firstOrNull()
                ?.childrenByLocalTag("xfrm")
                ?.firstOrNull()
                ?: return IDENTITY
            val off = xfrm.childrenByLocalTag("off").firstOrNull()
            val ext = xfrm.childrenByLocalTag("ext").firstOrNull()
            val chOff = xfrm.childrenByLocalTag("chOff").firstOrNull()
            val chExt = xfrm.childrenByLocalTag("chExt").firstOrNull() ?: return IDENTITY
            val childWidth = chExt.xmlFloat("cx")?.emuToPoint()?.takeIf { it != 0f } ?: return IDENTITY
            val childHeight = chExt.xmlFloat("cy")?.emuToPoint()?.takeIf { it != 0f } ?: return IDENTITY
            val scaleX = (ext?.xmlFloat("cx")?.emuToPoint() ?: childWidth) / childWidth
            val scaleY = (ext?.xmlFloat("cy")?.emuToPoint() ?: childHeight) / childHeight
            val childX = chOff?.xmlFloat("x")?.emuToPoint() ?: 0f
            val childY = chOff?.xmlFloat("y")?.emuToPoint() ?: 0f
            val offX = off?.xmlFloat("x")?.emuToPoint() ?: 0f
            val offY = off?.xmlFloat("y")?.emuToPoint() ?: 0f
            return PptxGroupTransform(
                scaleX = scaleX,
                scaleY = scaleY,
                dx = offX - childX * scaleX,
                dy = offY - childY * scaleY,
                rotationDegrees = xfrm.xmlFloat("rot")?.let { it / 60_000f } ?: 0f
            )
        }
    }
}

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
                runCatching { parseSlide(zip, presentation, slidePath, width, height) }
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

    private fun parseSlide(
        zip: ZipFile,
        presentation: Element,
        slidePath: String,
        width: Int,
        height: Int
    ): PptxSlide {
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
        val presentationDefaults = presentation.presentationTextDefaults(theme)

        val master = masterPath?.let { path ->
            zip.xml(path)?.let {
                parsePart(
                    zip = zip,
                    document = it,
                    relationships = zip.relationshipsFor(path),
                    theme = theme,
                    renderPlaceholderText = false,
                    inheritedTextDefaults = presentationDefaults
                )
            }
        } ?: ParsedPart()
        val layout = layoutPath?.let { path ->
            zip.xml(path)?.let {
                parsePart(
                    zip = zip,
                    document = it,
                    relationships = zip.relationshipsFor(path),
                    theme = theme,
                    renderPlaceholderText = false,
                    inheritedTextDefaults = master.textDefaults
                )
            }
        } ?: ParsedPart()
        val slide = parsePart(
            zip = zip,
            document = slideXml,
            relationships = slideRels,
            theme = theme,
            renderPlaceholderText = true,
            inheritedTextDefaults = layout.textDefaults
        )
        val inheritedElements = master.elements + layout.elements
        val elements = inheritedElements + inheritPlaceholderProperties(slide.elements, inheritedElements)
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
        renderPlaceholderText: Boolean,
        inheritedTextDefaults: PptxTextDefaults = PptxTextDefaults()
    ): ParsedPart {
        val partTheme = theme.withColorMap(document.colorMapElement())
        val textDefaults = inheritedTextDefaults.merge(document.textDefaults(partTheme))
        val background = document.firstByLocalTag("bgPr")?.solidFillColor(partTheme)
            ?: document.firstByLocalTag("bgRef")?.schemeColor(partTheme)
        val tableStyles = zip.tableStyles(partTheme)
        val elements = mutableListOf<PptxElement>()
        val tree = document.firstByLocalTag("spTree") ?: document
        tree.children().forEach { child ->
            parseDrawingElement(
                zip = zip,
                element = child,
                relationships = relationships,
                theme = partTheme,
                renderPlaceholderText = renderPlaceholderText,
                textDefaults = textDefaults,
                tableStyles = tableStyles,
                output = elements
            )
        }
        return ParsedPart(backgroundColor = background, elements = elements, textDefaults = textDefaults)
    }

    private fun parseDrawingElement(
        zip: ZipFile,
        element: Element,
        relationships: PptxRelationships,
        theme: PptxTheme,
        renderPlaceholderText: Boolean,
        textDefaults: PptxTextDefaults,
        tableStyles: Map<String, PptxTableStyle>,
        output: MutableList<PptxElement>,
        transform: PptxGroupTransform = PptxGroupTransform.IDENTITY
    ) {
        when (element.localTag()) {
            "sp", "cxnsp" -> parseShape(element, relationships, theme, renderPlaceholderText, textDefaults)
                ?.let { output += transform.apply(it) }
            "pic" -> parseImage(zip, element, relationships)
                ?.let { output += transform.apply(it) }
            "grpsp" -> element.children().forEach { child ->
                val childTransform = transform.then(PptxGroupTransform.fromGroup(element))
                parseDrawingElement(
                    zip = zip,
                    element = child,
                    relationships = relationships,
                    theme = theme,
                    renderPlaceholderText = renderPlaceholderText,
                    textDefaults = textDefaults,
                    tableStyles = tableStyles,
                    output = output,
                    transform = childTransform
                )
            }
            "graphicframe" -> parseGraphicFrame(element, relationships, theme, tableStyles)
                ?.let { output += transform.apply(it) }
        }
    }

    private fun parseShape(
        element: Element,
        relationships: PptxRelationships,
        theme: PptxTheme,
        renderPlaceholderText: Boolean,
        textDefaults: PptxTextDefaults
    ): PptxShapeElement? {
        val spPr = element.childrenByLocalTag("spPr").firstOrNull()
        val bounds = spPr?.boundsFromTransform() ?: element.boundsFromTransform()
        val txBody = element.firstByLocalTag("txBody")
        val bodyPr = txBody?.childrenByLocalTag("bodyPr")?.firstOrNull()
        val preset = spPr?.childrenByLocalTag("prstGeom")?.firstOrNull()?.xmlAttr("prst")
            ?: if (element.localTag() == "cxnsp") "line" else "rect"
        val customGeometry = spPr?.childrenByLocalTag("custGeom")?.firstOrNull()?.customGeometry()
        val placeholderKey = element.firstByLocalTag("ph")?.placeholderKey()
        val style = element.childrenByLocalTag("style").firstOrNull()
        val shapeRunDefaults = PptxRunStyle(color = style?.firstByLocalTag("fontRef")?.solidLikeColor(theme))
        val paragraphs = parseTextBody(
            txBody = txBody,
            theme = theme,
            inheritedStyles = textDefaults.forPlaceholder(placeholderKey),
            shapeRunDefaults = shapeRunDefaults
        )
        val useBackgroundFill = element.xmlAttr("useBgFill").isTruthyXmlFlag()
        val fillColor = when {
            useBackgroundFill -> null
            spPr?.firstDirectByLocalTag("noFill") != null -> null
            else -> spPr?.solidFillColor(theme) ?: style?.firstByLocalTag("fillRef")?.solidLikeColor(theme)
        }
        val gradientFill = spPr?.gradientFill(theme)
        val line = spPr?.childrenByLocalTag("ln")?.firstOrNull()
        val lineColor = when {
            line?.firstDirectByLocalTag("noFill") != null -> null
            else -> line?.solidFillColor(theme) ?: style?.firstByLocalTag("lnRef")?.solidLikeColor(theme)
        }
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
            gradientFill = gradientFill,
            lineColor = lineColor,
            lineWidthPoint = lineWidth,
            paragraphs = paragraphs,
            hyperlink = hyperlink,
            placeholderKey = placeholderKey,
            textInsets = bodyPr?.textInsets() ?: PptxTextInsets(),
            verticalAnchor = bodyPr?.verticalAnchor() ?: PptxVerticalAnchor.TOP,
            rotationDegrees = spPr?.rotationDegreesFromTransform() ?: element.rotationDegreesFromTransform(),
            renderText = placeholderKey == null || renderPlaceholderText,
            fontScale = bodyPr?.autoFitFontScale() ?: 1f,
            lineSpacingReduction = bodyPr?.autoFitLineSpacingReduction() ?: 0f,
            autoFitMode = bodyPr?.autoFitMode() ?: PptxAutoFitMode.NONE,
            customGeometry = customGeometry
        )
    }

    private fun parseImage(
        zip: ZipFile,
        element: Element,
        relationships: PptxRelationships
    ): PptxImageElement? {
        val blip = element.firstByLocalTag("blip") ?: return null
        val relId = blip.xmlAttr("r:embed") ?: blip.xmlAttr("r:link") ?: return null
        val rel = relationships.byId[relId] ?: return null
        val target = rel.resolvedTarget
        val entry = zip.getEntry(target) ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val crop = element.firstByLocalTag("srcRect")?.imageCrop() ?: PptxImageCrop()
        val bounds = element.childrenByLocalTag("spPr").firstOrNull()?.boundsFromTransform()
            ?: element.boundsFromTransform()
        return PptxImageElement(
            bounds = bounds,
            bytes = bytes,
            contentType = target.imageContentType(),
            crop = crop,
            rotationDegrees = element.childrenByLocalTag("spPr").firstOrNull()?.rotationDegreesFromTransform()
                ?: element.rotationDegreesFromTransform(),
            opacity = blip.imageOpacity()
        )
    }

    private fun parseGraphicFrame(
        element: Element,
        relationships: PptxRelationships,
        theme: PptxTheme,
        tableStyles: Map<String, PptxTableStyle>
    ): PptxElement? {
        val table = element.firstByLocalTag("tbl") ?: return parseGraphicPlaceholder(element, relationships, theme)
        val bounds = element.boundsFromTransform()
        val tblPr = table.childrenByLocalTag("tblPr").firstOrNull()
        val tableStyle = tblPr
            ?.firstDirectByLocalTag("tableStyleId")
            ?.wholeText()
            ?.trim()
            ?.let { tableStyles[it] }
        val styleOptions = PptxTableStyleOptions(
            firstRow = tblPr?.xmlAttr("firstRow").isTruthyXmlFlag(),
            lastRow = tblPr?.xmlAttr("lastRow").isTruthyXmlFlag(),
            firstColumn = tblPr?.xmlAttr("firstCol").isTruthyXmlFlag(),
            lastColumn = tblPr?.xmlAttr("lastCol").isTruthyXmlFlag(),
            bandRow = tblPr?.xmlAttr("bandRow").isTruthyXmlFlag()
        )
        val gridWidths = table.firstByLocalTag("tblGrid")
            ?.childrenByLocalTag("gridCol")
            ?.map { it.xmlFloat("w")?.emuToPoint() }
            .orEmpty()
        val rowElements = table.childrenByLocalTag("tr")
        val rows = rowElements.mapIndexed { rowIndex, row ->
            val cells = row.childrenByLocalTag("tc").mapIndexed { index, cell ->
                val tcPr = cell.childrenByLocalTag("tcPr").firstOrNull()
                val style = tableStyle?.cellStyle(
                    rowIndex = rowIndex,
                    columnIndex = index,
                    rowCount = rowElements.size,
                    columnCount = gridWidths.size.coerceAtLeast(row.childrenByLocalTag("tc").size),
                    options = styleOptions
                )
                val textInsets = tcPr?.textInsets() ?: PptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f)
                PptxTableCell(
                    widthPoint = gridWidths.getOrNull(index),
                    fillColor = when {
                        tcPr?.firstDirectByLocalTag("noFill") != null -> null
                        else -> tcPr?.solidFillColor(theme) ?: style?.fillColor
                    },
                    lineColor = tcPr?.tableCellLineColor(theme) ?: style?.lineColor,
                    paragraphs = parseTextBody(
                        txBody = cell.childrenByLocalTag("txBody").firstOrNull(),
                        theme = theme,
                        shapeRunDefaults = style?.run ?: PptxRunStyle()
                    ),
                    textInsets = textInsets,
                    verticalAnchor = tcPr?.verticalAnchor() ?: PptxVerticalAnchor.TOP
                )
            }
            PptxTableRow(
                heightPoint = row.xmlFloat("h")?.emuToPoint(),
                cells = cells
            )
        }
        if (rows.all { row -> row.cells.all { it.paragraphs.isEmpty() } }) return null
        return PptxTableElement(
            bounds = bounds,
            rows = rows,
            rotationDegrees = element.rotationDegreesFromTransform()
        )
    }

    private fun parseGraphicPlaceholder(
        element: Element,
        relationships: PptxRelationships,
        theme: PptxTheme
    ): PptxElement? {
        val bounds = element.boundsFromTransform()
        if (bounds.width() <= 0f || bounds.height() <= 0f) return null
        val chartRel = element.firstByLocalTag("chart")?.xmlAttr("r:id")
        val diagramRel = element.firstByLocalTag("relIds")?.xmlAttr("r:dm")
        val mediaRel = element.firstByLocalTag("videoFile")?.xmlAttr("r:link")
            ?: element.firstByLocalTag("audioFile")?.xmlAttr("r:link")
        val label = when {
            chartRel != null -> "Chart"
            diagramRel != null -> "SmartArt"
            mediaRel != null -> "Media"
            else -> return null
        }
        val target = (chartRel ?: diagramRel ?: mediaRel)?.let { relationships.byId[it]?.resolvedTarget }
        return PptxShapeElement(
            bounds = bounds,
            preset = "rect",
            fillColor = Color.rgb(245, 246, 248),
            gradientFill = null,
            lineColor = theme.color("tx1") ?: Color.GRAY,
            lineWidthPoint = 0.75f,
            paragraphs = listOf(
                PptxParagraph(
                    runs = listOf(PptxTextRun(target?.let { "$label: ${it.substringAfterLast('/')}" } ?: label)),
                    alignment = PptxTextAlign.CENTER
                )
            ),
            hyperlink = null,
            placeholderKey = null,
            textInsets = PptxTextInsets(left = 8f, top = 8f, right = 8f, bottom = 8f),
            verticalAnchor = PptxVerticalAnchor.MIDDLE,
            rotationDegrees = element.rotationDegreesFromTransform()
        )
    }

    private fun parseTextBody(
        txBody: Element?,
        theme: PptxTheme,
        inheritedStyles: Map<Int, PptxParagraphStyle> = emptyMap(),
        shapeRunDefaults: PptxRunStyle = PptxRunStyle()
    ): List<PptxParagraph> {
        if (txBody == null) return emptyList()
        val localStyles = txBody.childrenByLocalTag("lstStyle")
            .firstOrNull()
            ?.paragraphStyles(theme)
            .orEmpty()
        val styles = inheritedStyles.mergeStyles(localStyles)
        val numberCounters = IntArray(MAX_NUMBERING_LEVELS)
        val numberCounterStarted = BooleanArray(MAX_NUMBERING_LEVELS)
        return txBody.childrenByLocalTag("p").mapNotNull { paragraph ->
            val pPr = paragraph.childrenByLocalTag("pPr").firstOrNull()
            val endParaRunPr = paragraph.childrenByLocalTag("endParaRPr").firstOrNull()
            val level = pPr?.xmlInt("lvl")?.coerceAtLeast(0) ?: 0
            val paragraphStyle = (styles[level] ?: styles[0] ?: PptxParagraphStyle())
                .merge(pPr?.paragraphStyle(theme) ?: PptxParagraphStyle())
            val paragraphRunStyle = shapeRunDefaults
                .merge(paragraphStyle.run)
                .merge(endParaRunPr?.runStyle(theme) ?: PptxRunStyle())
            val runs = mutableListOf<PptxTextRun>()
            paragraph.children().forEach { child ->
                when (child.localTag()) {
                    "r", "fld" -> {
                        val rPr = child.childrenByLocalTag("rPr").firstOrNull()
                        val runStyle = paragraphRunStyle.merge(rPr?.runStyle(theme) ?: PptxRunStyle())
                        val text = child.firstByLocalTag("t")?.wholeText().orEmpty()
                        if (text.isNotEmpty()) {
                            runs += PptxTextRun(
                                text = text,
                                sizePt = runStyle.sizePt,
                                color = runStyle.color,
                                bold = runStyle.bold ?: false,
                                italic = runStyle.italic ?: false,
                                typeface = runStyle.typeface,
                                baseline = runStyle.baseline ?: 0f,
                                sizeExplicit = rPr?.xmlAttr("sz") != null,
                                colorExplicit = rPr?.hasTextColor() == true,
                                boldExplicit = rPr?.xmlAttr("b") != null,
                                italicExplicit = rPr?.xmlAttr("i") != null,
                                typefaceExplicit = rPr?.hasTypeface() == true,
                                baselineExplicit = rPr?.xmlAttr("baseline") != null
                            )
                        }
                    }
                    "br" -> {
                        val rPr = child.childrenByLocalTag("rPr").firstOrNull()
                        val runStyle = paragraphRunStyle.merge(rPr?.runStyle(theme) ?: PptxRunStyle())
                        runs += PptxTextRun(
                            "\n",
                            sizePt = runStyle.sizePt,
                            color = runStyle.color,
                            bold = runStyle.bold ?: false,
                            italic = runStyle.italic ?: false,
                            typeface = runStyle.typeface,
                            baseline = runStyle.baseline ?: 0f
                        )
                    }
                    "tab" -> runs += PptxTextRun(
                        "\t",
                        sizePt = paragraphRunStyle.sizePt,
                        color = paragraphRunStyle.color,
                        bold = paragraphRunStyle.bold ?: false,
                        italic = paragraphRunStyle.italic ?: false,
                        typeface = paragraphRunStyle.typeface,
                        baseline = paragraphRunStyle.baseline ?: 0f
                    )
                }
            }
            val safeRuns = runs.ifEmpty { listOf(PptxTextRun("")) }
            if (safeRuns.none { it.text.isNotBlank() }) return@mapNotNull null
            val bullet = paragraphStyle.resolvedBullet(level, numberCounters, numberCounterStarted)
            PptxParagraph(
                runs = safeRuns,
                alignment = paragraphStyle.alignment ?: PptxTextAlign.START,
                bullet = bullet,
                level = level,
                marginLeftPt = paragraphStyle.marginLeftPt,
                indentPt = paragraphStyle.indentPt,
                spaceBeforePt = paragraphStyle.spaceBeforePt ?: 0f,
                spaceAfterPt = paragraphStyle.spaceAfterPt ?: 0f,
                lineSpacingMultiple = paragraphStyle.lineSpacingMultiple ?: DEFAULT_LINE_SPACING_MULTIPLE,
                alignmentExplicit = pPr?.xmlAttr("algn") != null,
                bulletExplicit = pPr?.hasBulletDefinition() == true,
                spaceBeforeExplicit = pPr?.firstByLocalTag("spcBef") != null,
                spaceAfterExplicit = pPr?.firstByLocalTag("spcAft") != null,
                lineSpacingExplicit = pPr?.firstByLocalTag("lnSpc") != null
            )
        }
    }

    private fun parseTheme(document: Element): PptxTheme {
        val scheme = document.firstByLocalTag("clrScheme") ?: return PptxTheme()
        val colors = scheme.children().mapNotNull { colorNode ->
            val value = colorNode.firstByLocalTag("srgbClr")?.xmlAttr("val")?.toColorOrNull()
                ?: colorNode.firstByLocalTag("sysClr")?.xmlAttr("lastClr")?.toColorOrNull()
            value?.let { colorNode.localTag() to it }
        }.toMap()
        val fontScheme = document.firstByLocalTag("fontScheme")
        val majorTypeface = fontScheme?.firstByLocalTag("majorFont")
            ?.firstByLocalTag("latin")
            ?.xmlAttr("typeface")
            ?.takeIf { it.isNotBlank() }
        val minorTypeface = fontScheme?.firstByLocalTag("minorFont")
            ?.firstByLocalTag("latin")
            ?.xmlAttr("typeface")
            ?.takeIf { it.isNotBlank() }
        val aliases = buildMap {
            putAll(colors)
            colors["lt1"]?.let { put("bg1", it) }
            colors["dk1"]?.let { put("tx1", it) }
            colors["lt2"]?.let { put("bg2", it) }
            colors["dk2"]?.let { put("tx2", it) }
        }
        return PptxTheme(
            colors = aliases,
            majorTypeface = majorTypeface,
            minorTypeface = minorTypeface
        )
    }

    private fun Element.presentationTextDefaults(theme: PptxTheme): PptxTextDefaults {
        val defaults = firstByLocalTag("defaultTextStyle")
            ?.paragraphStyles(theme)
            .orEmpty()
        if (defaults.isEmpty()) return PptxTextDefaults()
        return PptxTextDefaults(title = defaults, body = defaults, other = defaults)
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

    private fun ZipFile.tableStyles(theme: PptxTheme): Map<String, PptxTableStyle> {
        val document = xml("ppt/tableStyles.xml") ?: return emptyMap()
        return document.allByLocalTag("tblStyle").mapNotNull { style ->
            val id = style.xmlAttr("styleId") ?: return@mapNotNull null
            id to PptxTableStyle(
                whole = style.childrenByLocalTag("wholeTbl").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle(),
                firstRow = style.childrenByLocalTag("firstRow").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle(),
                lastRow = style.childrenByLocalTag("lastRow").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle(),
                firstColumn = style.childrenByLocalTag("firstCol").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle(),
                lastColumn = style.childrenByLocalTag("lastCol").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle(),
                band1Horizontal = style.childrenByLocalTag("band1H").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle(),
                band2Horizontal = style.childrenByLocalTag("band2H").firstOrNull()?.tableStylePart(theme) ?: PptxTableCellStyle()
            )
        }.toMap()
    }
}

private fun PptxTheme.withColorMap(mapping: Element?): PptxTheme {
    if (mapping == null) return this
    val mappedColors = colors.toMutableMap()
    listOf("bg1", "tx1", "bg2", "tx2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
        .forEach { alias ->
            val target = mapping.xmlAttr(alias) ?: return@forEach
            color(target)?.let { mappedColors[alias.lowercase(Locale.ROOT)] = it }
        }
    return copy(colors = mappedColors)
}

private fun Element.colorMapElement(): Element? {
    firstByLocalTag("overrideClrMapping")?.let { return it }
    if (firstByLocalTag("masterClrMapping") != null) return null
    return firstByLocalTag("clrMap")
}

private fun inheritPlaceholderProperties(
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

        val inherited = inheritedPlaceholders.lastOrNull { inherited ->
            inherited.placeholderKey?.matches(key) == true
        } ?: return@map shape
        val shouldInheritBounds = shape.bounds.width() <= 0f || shape.bounds.height() <= 0f

        shape.copy(
            bounds = if (shouldInheritBounds) RectF(inherited.bounds) else shape.bounds,
            preset = if (shouldInheritBounds && shape.preset == "rect") inherited.preset else shape.preset,
            fillColor = shape.fillColor ?: inherited.fillColor,
            gradientFill = shape.gradientFill ?: inherited.gradientFill,
            lineColor = shape.lineColor ?: inherited.lineColor,
            lineWidthPoint = if (shape.lineWidthPoint == 0.75f) inherited.lineWidthPoint else shape.lineWidthPoint,
            paragraphs = shape.paragraphs.inheritTextStyles(inherited.paragraphs),
            textInsets = if (shape.textInsets == PptxTextInsets()) inherited.textInsets else shape.textInsets,
            verticalAnchor = if (shape.verticalAnchor == PptxVerticalAnchor.TOP) inherited.verticalAnchor else shape.verticalAnchor,
            rotationDegrees = if (shape.rotationDegrees == 0f) inherited.rotationDegrees else shape.rotationDegrees,
            fontScale = if (shape.fontScale == 1f) inherited.fontScale else shape.fontScale,
            lineSpacingReduction = if (shape.lineSpacingReduction == 0f) {
                inherited.lineSpacingReduction
            } else {
                shape.lineSpacingReduction
            },
            autoFitMode = if (shape.autoFitMode == PptxAutoFitMode.NONE) inherited.autoFitMode else shape.autoFitMode,
            customGeometry = shape.customGeometry ?: inherited.customGeometry
        )
    }
}

private fun List<PptxParagraph>.inheritTextStyles(fallback: List<PptxParagraph>): List<PptxParagraph> {
    if (isEmpty() || fallback.isEmpty()) return this
    return mapIndexed { index, paragraph ->
        val fallbackParagraph = fallback.firstOrNull { it.level == paragraph.level }
            ?: fallback.getOrNull(index)
            ?: fallback.first()
        paragraph.inheritTextStyle(fallbackParagraph)
    }
}

private fun PptxParagraph.inheritTextStyle(fallback: PptxParagraph): PptxParagraph {
    val fallbackRun = fallback.runs.firstOrNull()
    return copy(
        runs = runs.map { run -> run.inheritTextStyle(fallbackRun) },
        alignment = if (alignmentExplicit) alignment else fallback.alignment,
        bullet = if (bulletExplicit) bullet else fallback.bullet,
        marginLeftPt = marginLeftPt ?: fallback.marginLeftPt,
        indentPt = indentPt ?: fallback.indentPt,
        spaceBeforePt = if (spaceBeforeExplicit) spaceBeforePt else fallback.spaceBeforePt,
        spaceAfterPt = if (spaceAfterExplicit) spaceAfterPt else fallback.spaceAfterPt,
        lineSpacingMultiple = if (lineSpacingExplicit) lineSpacingMultiple else fallback.lineSpacingMultiple
    )
}

private fun PptxTextRun.inheritTextStyle(fallback: PptxTextRun?): PptxTextRun {
    if (fallback == null) return this
    return copy(
        sizePt = if (sizeExplicit) sizePt else sizePt ?: fallback.sizePt,
        color = if (colorExplicit) color else color ?: fallback.color,
        bold = if (boldExplicit) bold else fallback.bold || bold,
        italic = if (italicExplicit) italic else fallback.italic || italic,
        typeface = if (typefaceExplicit) typeface else typeface ?: fallback.typeface,
        baseline = if (baselineExplicit) baseline else baseline.takeUnless { it == 0f } ?: fallback.baseline
    )
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
                        acc?.apply { union(box.bounds) } ?: RectF(box.bounds)
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
            when (element) {
                is PptxShapeElement -> appendShapeText(element, text, charBoxes)
                is PptxTableElement -> layoutTableCells(element).forEach { cell ->
                    appendShapeText(cell.asShape(), text, charBoxes)
                }
                is PptxImageElement -> Unit
            }
        }
        val indexedText = text.toString().trimEnd()
        return PptxTextIndex(indexedText, charBoxes.take(indexedText.length))
    }

    private fun appendShapeText(
        shape: PptxShapeElement,
        text: StringBuilder,
        charBoxes: MutableList<PptxCharBox>
    ) {
        if (!shape.renderText) return
        val textBounds = shape.textBounds()
        if (textBounds.width() <= 0f || textBounds.height() <= 0f) return
        val paragraphs = shape.paragraphs
            .mapNotNull { paragraph -> paragraph.displayText().takeIf { it.isNotBlank() }?.let { paragraph to it } }
        if (paragraphs.isEmpty()) return

        val paragraphHeights = paragraphs.map { (paragraph, displayText) ->
            paragraph.spaceBeforePt + displayText.lineCount() * paragraph.approximateLineHeight(shape) + paragraph.spaceAfterPt
        }
        val totalHeight = paragraphHeights.sumOf { it.toDouble() }.toFloat()
        val effectiveBounds = if (shape.autoFitMode == PptxAutoFitMode.SHAPE && totalHeight > textBounds.height()) {
            RectF(textBounds).apply { bottom = top + totalHeight }
        } else {
            textBounds
        }
        var y = when (shape.verticalAnchor) {
            PptxVerticalAnchor.TOP -> effectiveBounds.top
            PptxVerticalAnchor.MIDDLE -> effectiveBounds.top + ((effectiveBounds.height() - totalHeight) / 2f).coerceAtLeast(0f)
            PptxVerticalAnchor.BOTTOM -> effectiveBounds.bottom - totalHeight.coerceAtMost(effectiveBounds.height())
        }

        paragraphs.forEachIndexed { index, (paragraph, displayText) ->
            y += paragraph.spaceBeforePt
            displayText.lines().forEach { line ->
                if (shape.autoFitMode != PptxAutoFitMode.SHAPE && y > effectiveBounds.bottom) return@forEach
                appendLine(line, paragraph, shape, effectiveBounds, y, text, charBoxes)
                y += paragraph.approximateLineHeight(shape)
            }
            y += paragraph.spaceAfterPt
            if (index < paragraphs.lastIndex && text.lastOrNull() != '\n') {
                text.append('\n')
                charBoxes += PptxCharBox('\n', RectF(shape.bounds.left, shape.bounds.bottom, shape.bounds.left, shape.bounds.bottom))
            }
        }
    }

    private fun appendLine(
        line: String,
        paragraph: PptxParagraph,
        shape: PptxShapeElement,
        textBounds: RectF,
        y: Float,
        text: StringBuilder,
        charBoxes: MutableList<PptxCharBox>
    ) {
        if (line.isEmpty()) {
            text.append('\n')
            charBoxes += PptxCharBox('\n', RectF(textBounds.left, y, textBounds.left, y))
            return
        }
        val fontSize = scaledTextSize(paragraph.runs.firstOrNull()?.sizePt, shape.fontScale).toFloat()
        val estimatedWidth = line.sumOf { char ->
            when {
                char.isWhitespace() -> 0.33
                char in "ilI.,;:!|" -> 0.3
                char in "MW@#%&" -> 0.85
                else -> 0.55
            }
        }.toFloat() * fontSize
        val maxLineWidth = textBounds.width().coerceAtLeast(0.5f)
        val minLineWidth = min(line.length * 0.5f, maxLineWidth)
        val lineWidth = estimatedWidth.coerceIn(minLineWidth, maxLineWidth)
        val charAdvance = (lineWidth / line.length.coerceAtLeast(1)).coerceAtLeast(0.5f)
        val startX = when (paragraph.alignment) {
            PptxTextAlign.START -> textBounds.left
            PptxTextAlign.CENTER -> textBounds.left + ((textBounds.width() - lineWidth) / 2f).coerceAtLeast(0f)
            PptxTextAlign.END -> textBounds.right - lineWidth
        }
        val bottom = y + paragraph.approximateLineHeight(shape)

        text.append(line)
        line.forEachIndexed { index, char ->
            val left = startX + index * charAdvance
            val right = if (index == line.lastIndex) startX + lineWidth else left + charAdvance
            charBoxes += PptxCharBox(
                char = char,
                bounds = RectF(left, y, right, bottom).rotatedBounds(shape.bounds, shape.rotationDegrees)
            )
        }
    }

    private fun String.lineCount(): Int = lines().size.coerceAtLeast(1)

    private fun PptxParagraph.approximateLineHeight(shape: PptxShapeElement): Float {
        val fontSize = scaledTextSize(runs.firstOrNull()?.sizePt, shape.fontScale).toFloat()
        return (fontSize * 1.2f * effectiveLineSpacing(shape)).coerceAtLeast(1f)
    }
}

private data class LaidOutParagraph(
    val text: String,
    val layout: StaticLayout,
    val x: Float,
    val y: Float,
    val charBoxes: List<RectF>
)

private data class PreparedParagraphLayout(
    val paragraph: PptxParagraph,
    val text: String,
    val layout: StaticLayout
)

private data class LaidOutTableCell(
    val rect: RectF,
    val cell: PptxTableCell
)

private fun LaidOutTableCell.asShape(): PptxShapeElement {
    return PptxShapeElement(
        bounds = rect,
        preset = "rect",
        fillColor = cell.fillColor,
        gradientFill = null,
        lineColor = cell.lineColor,
        lineWidthPoint = 0.75f,
        paragraphs = cell.paragraphs,
        hyperlink = null,
        placeholderKey = null,
        textInsets = cell.textInsets,
        verticalAnchor = cell.verticalAnchor
    )
}

private val TextLayoutPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = DEFAULT_TEXT_SIZE_PT
}

private object PptxSlideRenderer {
    fun render(slide: PptxSlide, bitmap: Bitmap, startX: Int, startY: Int, drawSizeX: Int, drawSizeY: Int) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(slide.backgroundColor ?: Color.WHITE)
        if (slide.widthPoint <= 0 || slide.heightPoint <= 0) return
        canvas.withTranslation(startX.toFloat(), startY.toFloat()) {
            scale(drawSizeX.toFloat() / slide.widthPoint, drawSizeY.toFloat() / slide.heightPoint)

            slide.elements.forEach { element ->
                when (element) {
                    is PptxShapeElement -> drawShape(this, element)
                    is PptxImageElement -> drawImage(this, element)
                    is PptxTableElement -> drawTable(this, element)
                }
            }

        }
    }

    private fun drawShape(canvas: Canvas, shape: PptxShapeElement) {
        canvas.withRotation(shape.bounds, shape.rotationDegrees) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shape.gradientFill?.let { shader = it.toShader(shape.bounds) }
                if (shape.gradientFill == null) {
                    color = shape.fillColor ?: Color.TRANSPARENT
                }
            }
            if (shape.gradientFill != null || (shape.fillColor != null && Color.alpha(shape.fillColor) > 0)) {
                drawPresetShape(canvas, shape.bounds, shape.preset, shape.customGeometry, fillPaint)
            }

            val strokeColor = shape.lineColor
            if (strokeColor != null && Color.alpha(strokeColor) > 0) {
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = strokeColor
                    strokeWidth = shape.lineWidthPoint.coerceAtLeast(0.25f)
                }
                drawPresetShape(canvas, shape.bounds, shape.preset, shape.customGeometry, strokePaint)
            }

            drawText(canvas, shape)
        }
    }

    private fun drawText(canvas: Canvas, shape: PptxShapeElement) {
        if (!shape.renderText) return
        val layout = layoutParagraphs(shape, shape.bounds)
        val clip = shape.textBounds().takeUnless { shape.autoFitMode == PptxAutoFitMode.SHAPE }
        layout.forEach { paragraph ->
            canvas.withSave {
                clip?.let { clipRect(it) }
                translate(paragraph.x, paragraph.y)
                paragraph.layout.draw(this)
            }
        }
    }

    private fun drawTable(canvas: Canvas, table: PptxTableElement) {
        canvas.withRotation(table.bounds, table.rotationDegrees) {
            layoutTableCells(table).forEach { laidOutCell ->
                val rect = laidOutCell.rect
                val cell = laidOutCell.cell
                val fill = cell.fillColor
                if (fill != null && Color.alpha(fill) > 0) {
                    canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = fill
                    })
                }
                cell.lineColor?.let { lineColor ->
                    canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = lineColor
                        strokeWidth = 0.5f
                    })
                }
                drawText(canvas, laidOutCell.asShape())
            }
        }
    }

    private fun drawImage(canvas: Canvas, image: PptxImageElement) {
        canvas.withRotation(image.bounds, image.rotationDegrees) {
            val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            if (bitmap != null) {
                canvas.drawBitmap(
                    bitmap,
                    image.crop.sourceRect(bitmap.width, bitmap.height),
                    image.bounds,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = (255f * image.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
                    }
                )
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
}

private fun layoutParagraphs(shape: PptxShapeElement, bounds: RectF): List<LaidOutParagraph> {
    if (shape.paragraphs.isEmpty() || bounds.width() <= 0f || bounds.height() <= 0f) return emptyList()
    val baseTextBounds = shape.textBounds(bounds)
    if (baseTextBounds.width() <= 0f || baseTextBounds.height() <= 0f) return emptyList()

    var fontScale = shape.fontScale
    var prepared = prepareParagraphLayouts(shape, baseTextBounds, fontScale)
    if (shape.autoFitMode == PptxAutoFitMode.NORMAL && prepared.isNotEmpty()) {
        repeat(8) {
            val totalHeight = prepared.totalHeight()
            if (totalHeight <= baseTextBounds.height() || fontScale <= 0.35f) return@repeat
            val fitRatio = (baseTextBounds.height() / totalHeight).coerceIn(0.35f, 0.96f)
            fontScale = (fontScale * fitRatio).coerceAtLeast(0.35f)
            prepared = prepareParagraphLayouts(shape, baseTextBounds, fontScale)
        }
    }
    if (prepared.isEmpty()) return emptyList()

    val totalHeight = prepared.totalHeight()
    val textBounds = if (shape.autoFitMode == PptxAutoFitMode.SHAPE && totalHeight > baseTextBounds.height()) {
        RectF(baseTextBounds).apply { bottom = top + totalHeight }
    } else {
        baseTextBounds
    }
    var y = when (shape.verticalAnchor) {
        PptxVerticalAnchor.TOP -> textBounds.top
        PptxVerticalAnchor.MIDDLE -> textBounds.top + ((textBounds.height() - totalHeight) / 2f).coerceAtLeast(0f)
        PptxVerticalAnchor.BOTTOM -> textBounds.bottom - totalHeight.coerceAtMost(textBounds.height())
    }

    return prepared.mapNotNull { item ->
        y += item.paragraph.spaceBeforePt
        if (shape.autoFitMode != PptxAutoFitMode.SHAPE && y > textBounds.bottom) return@mapNotNull null
        val paragraphY = y
        val charBoxes = item.layout.charBoxesFor(item.text, textBounds.left, paragraphY)
            .map { rect -> rect.rotatedBounds(shape.bounds, shape.rotationDegrees) }
        y += item.layout.height + item.paragraph.spaceAfterPt
        LaidOutParagraph(
            text = item.text,
            layout = item.layout,
            x = textBounds.left,
            y = paragraphY,
            charBoxes = charBoxes
        )
    }
}

private fun prepareParagraphLayouts(
    shape: PptxShapeElement,
    textBounds: RectF,
    fontScale: Float
): List<PreparedParagraphLayout> {
    val layoutWidth = textBounds.width().roundToInt().coerceAtLeast(1)
    return shape.paragraphs.mapNotNull { paragraph ->
        val text = paragraph.displayText()
        if (text.isBlank()) return@mapNotNull null
        val spannable = paragraph.toSpannable(text, fontScale)
        val layout = StaticLayout.Builder
            .obtain(spannable, 0, spannable.length, TextLayoutPaint, layoutWidth)
            .setAlignment(paragraph.alignment.toLayoutAlignment())
            .setIncludePad(true)
            .setLineSpacing(0f, paragraph.effectiveLineSpacing(shape))
            .build()
        PreparedParagraphLayout(paragraph, text, layout)
    }
}

private fun List<PreparedParagraphLayout>.totalHeight(): Float {
    return sumOf { item ->
        (item.paragraph.spaceBeforePt + item.layout.height + item.paragraph.spaceAfterPt).toDouble()
    }.toFloat()
}

private fun PptxParagraph.effectiveLineSpacing(shape: PptxShapeElement): Float {
    val reduced = lineSpacingMultiple * (1f - shape.lineSpacingReduction)
    return reduced.coerceIn(0.9f, 2.5f)
}

private fun PptxShapeElement.textBounds(sourceBounds: RectF = bounds): RectF {
    return RectF(
        sourceBounds.left + textInsets.left,
        sourceBounds.top + textInsets.top,
        sourceBounds.right - textInsets.right,
        sourceBounds.bottom - textInsets.bottom
    )
}

private fun PptxParagraph.displayText(): String {
    val text = runs.joinToString("") { it.text }
    val prefix = bullet?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return prefix + text
}

private fun PptxParagraph.toSpannable(displayText: String, fontScale: Float): SpannableStringBuilder {
    val builder = SpannableStringBuilder(displayText)
    val bulletPrefixLength = bullet?.takeIf { it.isNotBlank() }?.let { it.length + 1 } ?: 0
    val baseMargin = marginLeftPt ?: ((level * 18f) + if (bulletPrefixLength > 0) 18f else 0f)
    val firstMargin = (baseMargin + (indentPt ?: if (bulletPrefixLength > 0) -12f else 0f))
        .roundToInt()
        .coerceAtLeast(0)
    val restMargin = baseMargin.roundToInt().coerceAtLeast(0)
    if (firstMargin > 0 || restMargin > 0) {
        builder.setSpan(
            LeadingMarginSpan.Standard(firstMargin, restMargin),
            0,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    var offset = bulletPrefixLength
    runs.forEach { run ->
        val start = offset
        val end = (start + run.text.length).coerceAtMost(builder.length)
        if (start >= end) return@forEach
        builder.setSpan(
            AbsoluteSizeSpan(scaledTextSize(run.sizePt, fontScale), false),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        run.color?.let { color ->
            builder.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (run.bold || run.italic) {
            builder.setSpan(
                StyleSpan(
                    when {
                        run.bold && run.italic -> Typeface.BOLD_ITALIC
                        run.bold -> Typeface.BOLD
                        else -> Typeface.ITALIC
                    }
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        run.typeface?.takeIf { it.isNotBlank() && !it.startsWith("+") }?.let { family ->
            builder.setSpan(TypefaceSpan(family), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        when {
            run.baseline > 0.05f -> {
                builder.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            run.baseline < -0.05f -> {
                builder.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(0.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        offset = end
    }

    if (bulletPrefixLength > 0) {
        val firstRun = runs.firstOrNull()
        builder.setSpan(
            AbsoluteSizeSpan(scaledTextSize(firstRun?.sizePt, fontScale), false),
            0,
            bulletPrefixLength,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        firstRun?.color?.let { color ->
            builder.setSpan(ForegroundColorSpan(color), 0, bulletPrefixLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return builder
}

private fun PptxTextAlign.toLayoutAlignment(): Layout.Alignment {
    return when (this) {
        PptxTextAlign.START -> Layout.Alignment.ALIGN_NORMAL
        PptxTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        PptxTextAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

private fun StaticLayout.charBoxesFor(text: String, originX: Float, originY: Float): List<RectF> {
    if (text.isEmpty()) return emptyList()
    return text.indices.map { index ->
        val line = getLineForOffset(index.coerceIn(0, text.length))
        val nextOffset = (index + 1).coerceAtMost(text.length)
        val left = runCatching { getPrimaryHorizontal(index) }.getOrDefault(getLineLeft(line))
        val right = runCatching { getPrimaryHorizontal(nextOffset) }.getOrDefault(left)
        val minX = min(left, right)
        val maxX = max(left, right).let { if (it == minX) it + 0.5f else it }
        RectF(
            originX + minX,
            originY + getLineTop(line),
            originX + maxX,
            originY + getLineBottom(line)
        )
    }
}

private fun RectF.rotatedBounds(bounds: RectF, rotationDegrees: Float): RectF {
    if (rotationDegrees == 0f) return this
    val radians = Math.toRadians(rotationDegrees.toDouble())
    val cosValue = cos(radians).toFloat()
    val sinValue = sin(radians).toFloat()
    val cx = bounds.centerX()
    val cy = bounds.centerY()
    val points = arrayOf(
        left to top,
        right to top,
        right to bottom,
        left to bottom
    ).map { (x, y) ->
        val dx = x - cx
        val dy = y - cy
        PointF(cx + dx * cosValue - dy * sinValue, cy + dx * sinValue + dy * cosValue)
    }
    return RectF(
        points.minOf { it.x },
        points.minOf { it.y },
        points.maxOf { it.x },
        points.maxOf { it.y }
    )
}

private fun layoutTableCells(table: PptxTableElement): List<LaidOutTableCell> {
    if (table.rows.isEmpty() || table.bounds.width() <= 0f || table.bounds.height() <= 0f) return emptyList()
    val explicitHeight = table.rows
        .mapNotNull { it.heightPoint?.takeIf { h -> h > 0f } }
        .sumOf { it.toDouble() }
        .toFloat()
    val missingRows = table.rows.count { it.heightPoint == null || it.heightPoint <= 0f }
    val fallbackHeight = if (missingRows > 0) {
        ((table.bounds.height() - explicitHeight).coerceAtLeast(1f)) / missingRows
    } else {
        table.bounds.height() / table.rows.size
    }

    val cells = mutableListOf<LaidOutTableCell>()
    var y = table.bounds.top
    table.rows.forEach { row ->
        val rowHeight = row.heightPoint?.takeIf { it > 0f } ?: fallbackHeight
        val explicitWidth = row.cells
            .mapNotNull { it.widthPoint?.takeIf { w -> w > 0f } }
            .sumOf { it.toDouble() }
            .toFloat()
        val missingCells = row.cells.count { it.widthPoint == null || it.widthPoint <= 0f }
        val fallbackWidth = if (missingCells > 0) {
            ((table.bounds.width() - explicitWidth).coerceAtLeast(1f)) / missingCells
        } else if (row.cells.isNotEmpty()) {
            table.bounds.width() / row.cells.size
        } else {
            table.bounds.width()
        }
        var x = table.bounds.left
        row.cells.forEach { cell ->
            val cellWidth = cell.widthPoint?.takeIf { it > 0f } ?: fallbackWidth
            cells += LaidOutTableCell(
                rect = RectF(x, y, x + cellWidth, y + rowHeight),
                cell = cell
            )
            x += cellWidth
        }
        y += rowHeight
    }
    return cells
}

private inline fun Canvas.withRotation(bounds: RectF, rotationDegrees: Float, block: () -> Unit) {
    withSave {
        try {
            if (rotationDegrees != 0f) {
                rotate(rotationDegrees, bounds.centerX(), bounds.centerY())
            }
            block()
        } finally {
        }
    }
}

private fun drawPresetShape(
    canvas: Canvas,
    bounds: RectF,
    preset: String,
    customGeometry: PptxCustomGeometry?,
    paint: Paint
) {
    if (customGeometry != null && preset != "line") {
        canvas.drawPath(customGeometry.toPath(bounds), paint)
        return
    }
    when (preset) {
        "line" -> canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
        "ellipse" -> canvas.drawOval(bounds, paint)
        "roundrect", "roundRect" -> canvas.drawRoundRect(bounds, bounds.width() * 0.08f, bounds.height() * 0.08f, paint)
        "triangle" -> canvas.drawPath(Path().apply {
            moveTo(bounds.centerX(), bounds.top)
            lineTo(bounds.right, bounds.bottom)
            lineTo(bounds.left, bounds.bottom)
            close()
        }, paint)
        "diamond" -> canvas.drawPath(Path().apply {
            moveTo(bounds.centerX(), bounds.top)
            lineTo(bounds.right, bounds.centerY())
            lineTo(bounds.centerX(), bounds.bottom)
            lineTo(bounds.left, bounds.centerY())
            close()
        }, paint)
        else -> canvas.drawRect(bounds, paint)
    }
}

private fun PptxGradientFill.toShader(bounds: RectF): Shader {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = cos(radians).toFloat() * bounds.width()
    val dy = sin(radians).toFloat() * bounds.height()
    return LinearGradient(
        bounds.centerX() - dx / 2f,
        bounds.centerY() - dy / 2f,
        bounds.centerX() + dx / 2f,
        bounds.centerY() + dy / 2f,
        startColor,
        endColor,
        Shader.TileMode.CLAMP
    )
}

private fun Element.textDefaults(theme: PptxTheme): PptxTextDefaults {
    val txStyles = firstByLocalTag("txStyles") ?: return PptxTextDefaults()
    return PptxTextDefaults(
        title = txStyles.childrenByLocalTag("titleStyle").firstOrNull()?.paragraphStyles(theme).orEmpty(),
        body = txStyles.childrenByLocalTag("bodyStyle").firstOrNull()?.paragraphStyles(theme).orEmpty(),
        other = txStyles.childrenByLocalTag("otherStyle").firstOrNull()?.paragraphStyles(theme).orEmpty()
    )
}

private fun Element.paragraphStyles(theme: PptxTheme): Map<Int, PptxParagraphStyle> {
    return children().mapNotNull { child ->
        val tag = child.localTag()
        val level = when {
            tag == "defppr" -> 0
            tag.startsWith("lvl") && tag.endsWith("ppr") -> {
                tag.removePrefix("lvl").removeSuffix("ppr").toIntOrNull()?.minus(1)
            }
            else -> null
        } ?: return@mapNotNull null
        level.coerceAtLeast(0) to child.paragraphStyle(theme)
    }.toMap()
}

private fun Element.paragraphStyle(theme: PptxTheme): PptxParagraphStyle {
    val autoNumber = firstDirectByLocalTag("buAutoNum")
    val bulletTypeface = firstDirectByLocalTag("buFont")?.xmlAttr("typeface")
    val bullet = when {
        firstDirectByLocalTag("buNone") != null -> null
        autoNumber != null -> null
        else -> firstDirectByLocalTag("buChar")?.xmlAttr("char")?.normalizeBulletGlyph(bulletTypeface)
    }
    return PptxParagraphStyle(
        alignment = when (xmlAttr("algn")) {
            "l", "just", "justLow", "dist", "thaiDist" -> PptxTextAlign.START
            "ctr" -> PptxTextAlign.CENTER
            "r" -> PptxTextAlign.END
            else -> null
        },
        bullet = bullet,
        autoNumberType = autoNumber?.xmlAttr("type"),
        autoNumberStartAt = autoNumber?.xmlInt("startAt"),
        bulletExplicit = hasBulletDefinition(),
        marginLeftPt = xmlFloat("marL")?.emuToPoint(),
        indentPt = xmlFloat("indent")?.emuToPoint(),
        spaceBeforePt = firstDirectByLocalTag("spcBef")?.spacingPoints(),
        spaceAfterPt = firstDirectByLocalTag("spcAft")?.spacingPoints(),
        lineSpacingMultiple = firstDirectByLocalTag("lnSpc")?.spacingMultiple(),
        run = childrenByLocalTag("defRPr").firstOrNull()?.runStyle(theme) ?: PptxRunStyle()
    )
}

private fun Element.runStyle(theme: PptxTheme): PptxRunStyle {
    return PptxRunStyle(
        sizePt = xmlFloat("sz")?.let { it / 100f },
        color = solidFillColor(theme),
        bold = xmlAttr("b")?.isTruthyXmlFlag(),
        italic = xmlAttr("i")?.isTruthyXmlFlag(),
        typeface = typefaceName(theme),
        baseline = xmlFloat("baseline")?.let { it / 100_000f }
    )
}

private fun PptxParagraphStyle.resolvedBullet(
    level: Int,
    counters: IntArray,
    counterStarted: BooleanArray
): String? {
    val numberType = autoNumberType
    if (numberType != null) {
        val index = level.coerceIn(0, MAX_NUMBERING_LEVELS - 1)
        if (!counterStarted[index]) {
            counters[index] = (autoNumberStartAt ?: 1).coerceAtLeast(1)
            counterStarted[index] = true
        } else {
            counters[index] += 1
        }
        for (resetIndex in index + 1 until MAX_NUMBERING_LEVELS) {
            counterStarted[resetIndex] = false
            counters[resetIndex] = 0
        }
        return formatAutoNumber(counters[index], numberType)
    }
    return bullet
}

private fun formatAutoNumber(number: Int, type: String): String {
    val normalized = type.lowercase(Locale.ROOT)
    val value = when {
        normalized.startsWith("alphalc") -> number.toAlphabeticLabel().lowercase(Locale.ROOT)
        normalized.startsWith("alphauc") -> number.toAlphabeticLabel()
        normalized.startsWith("romanlc") -> number.toRomanNumeral().lowercase(Locale.ROOT)
        normalized.startsWith("romanuc") -> number.toRomanNumeral()
        else -> number.toString()
    }
    return when {
        "parenboth" in normalized -> "($value)"
        "parenr" in normalized -> "$value)"
        "period" in normalized -> "$value."
        else -> value
    }
}

private fun Int.toAlphabeticLabel(): String {
    var value = coerceAtLeast(1)
    val result = StringBuilder()
    while (value > 0) {
        value -= 1
        result.insert(0, ('A'.code + (value % 26)).toChar())
        value /= 26
    }
    return result.toString()
}

private fun Int.toRomanNumeral(): String {
    var value = coerceIn(1, 3999)
    val numerals = listOf(
        1000 to "M",
        900 to "CM",
        500 to "D",
        400 to "CD",
        100 to "C",
        90 to "XC",
        50 to "L",
        40 to "XL",
        10 to "X",
        9 to "IX",
        5 to "V",
        4 to "IV",
        1 to "I"
    )
    return buildString {
        numerals.forEach { (amount, numeral) ->
            while (value >= amount) {
                append(numeral)
                value -= amount
            }
        }
    }
}

private fun String.normalizeBulletGlyph(typeface: String?): String {
    val family = typeface.orEmpty().lowercase(Locale.ROOT)
    if ("wingdings" !in family && "symbol" !in family) return this
    return when (this) {
        "\u00A7", "\u00D8", "\u00B7", "\uF0B7" -> "\u2022"
        "\u00FC", "\uF0FC" -> "\u2713"
        "\u00A8", "\uF0A8" -> "\u25E6"
        else -> this
    }
}

private fun Map<Int, PptxParagraphStyle>.mergeStyles(
    overrides: Map<Int, PptxParagraphStyle>
): Map<Int, PptxParagraphStyle> {
    if (isEmpty()) return overrides
    if (overrides.isEmpty()) return this
    return buildMap {
        putAll(this@mergeStyles)
        overrides.forEach { (level, style) ->
            put(level, this@mergeStyles[level]?.merge(style) ?: style)
        }
    }
}

private fun scaledTextSize(sizePt: Float?, fontScale: Float): Int {
    return ((sizePt ?: DEFAULT_TEXT_SIZE_PT) * fontScale.coerceIn(0.4f, 2f))
        .roundToInt()
        .coerceAtLeast(1)
}

private fun Element.imageCrop(): PptxImageCrop {
    return PptxImageCrop(
        left = xmlFloat("l")?.let { it / 100_000f }?.coerceIn(0f, 1f) ?: 0f,
        top = xmlFloat("t")?.let { it / 100_000f }?.coerceIn(0f, 1f) ?: 0f,
        right = xmlFloat("r")?.let { it / 100_000f }?.coerceIn(0f, 1f) ?: 0f,
        bottom = xmlFloat("b")?.let { it / 100_000f }?.coerceIn(0f, 1f) ?: 0f
    )
}

private fun Element.imageOpacity(): Float {
    firstByLocalTag("alphaModFix")?.xmlFloat("amt")?.let { return (it / 100_000f).coerceIn(0f, 1f) }
    firstByLocalTag("alphaMod")?.xmlFloat("amt")?.let { return (it / 100_000f).coerceIn(0f, 1f) }
    firstByLocalTag("alpha")?.xmlFloat("val")?.let { return (it / 100_000f).coerceIn(0f, 1f) }
    return 1f
}

private fun PptxImageCrop.sourceRect(width: Int, height: Int): Rect {
    val leftPx = (width * left).roundToInt().coerceIn(0, width - 1)
    val topPx = (height * top).roundToInt().coerceIn(0, height - 1)
    val rightPx = (width * (1f - right)).roundToInt().coerceIn(leftPx + 1, width)
    val bottomPx = (height * (1f - bottom)).roundToInt().coerceIn(topPx + 1, height)
    return Rect(leftPx, topPx, rightPx, bottomPx)
}

private fun Element.customGeometry(): PptxCustomGeometry? {
    val paths = firstByLocalTag("pathLst")?.childrenByLocalTag("path").orEmpty()
    if (paths.isEmpty()) return null
    val width = paths.first().xmlFloat("w")?.takeIf { it > 0f } ?: return null
    val height = paths.first().xmlFloat("h")?.takeIf { it > 0f } ?: return null
    val commands = paths.flatMap { path ->
        path.children().mapNotNull { command ->
            when (command.localTag()) {
                "moveto" -> command.firstDirectByLocalTag("pt")?.pathPoint()?.let { PptxPathCommand.MoveTo(it.x, it.y) }
                "lnto" -> command.firstDirectByLocalTag("pt")?.pathPoint()?.let { PptxPathCommand.LineTo(it.x, it.y) }
                "quadbezto" -> {
                    val points = command.childrenByLocalTag("pt").mapNotNull { it.pathPoint() }
                    if (points.size >= 2) {
                        PptxPathCommand.QuadTo(points[0].x, points[0].y, points[1].x, points[1].y)
                    } else {
                        null
                    }
                }
                "cubicbezto" -> {
                    val points = command.childrenByLocalTag("pt").mapNotNull { it.pathPoint() }
                    if (points.size >= 3) {
                        PptxPathCommand.CubicTo(
                            points[0].x,
                            points[0].y,
                            points[1].x,
                            points[1].y,
                            points[2].x,
                            points[2].y
                        )
                    } else {
                        null
                    }
                }
                "close" -> PptxPathCommand.Close
                else -> null
            }
        }
    }
    return PptxCustomGeometry(width = width, height = height, commands = commands)
        .takeIf { it.commands.isNotEmpty() }
}

private fun Element.pathPoint(): PointF? {
    val x = xmlFloat("x") ?: return null
    val y = xmlFloat("y") ?: return null
    return PointF(x, y)
}

private fun Element.gradientFill(theme: PptxTheme): PptxGradientFill? {
    val gradFill = childrenByLocalTag("gradFill").firstOrNull() ?: return null
    val stops = gradFill.firstByLocalTag("gsLst")
        ?.childrenByLocalTag("gs")
        ?.mapNotNull { stop -> stop.solidLikeColor(theme)?.let { stop.xmlInt("pos").orZero() to it } }
        ?.sortedBy { it.first }
        .orEmpty()
    if (stops.size < 2) return null
    val angle = gradFill.firstByLocalTag("lin")?.xmlFloat("ang")?.let { it / 60_000f } ?: 0f
    return PptxGradientFill(
        startColor = stops.first().second,
        endColor = stops.last().second,
        angleDegrees = angle
    )
}

private fun Element.solidLikeColor(theme: PptxTheme): Int? {
    firstByLocalTag("srgbClr")?.let { color ->
        return color.xmlAttr("val")?.toColorOrNull()?.applyLuminance(color)
    }
    firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.color(scheme)?.applyLuminance(color)
    }
    firstByLocalTag("prstClr")?.let { color ->
        return color.xmlAttr("val")?.presetColorOrNull()?.applyLuminance(color)
    }
    firstByLocalTag("sysClr")?.let { color ->
        return color.xmlAttr("lastClr")?.toColorOrNull()?.applyLuminance(color)
    }
    return null
}

private fun Element.textInsets(): PptxTextInsets {
    return PptxTextInsets(
        left = (xmlFloat("lIns") ?: xmlFloat("marL"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT,
        top = (xmlFloat("tIns") ?: xmlFloat("marT"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT,
        right = (xmlFloat("rIns") ?: xmlFloat("marR"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT,
        bottom = (xmlFloat("bIns") ?: xmlFloat("marB"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT
    )
}

private fun Element.verticalAnchor(): PptxVerticalAnchor {
    return when (xmlAttr("anchor")) {
        "ctr" -> PptxVerticalAnchor.MIDDLE
        "b" -> PptxVerticalAnchor.BOTTOM
        else -> PptxVerticalAnchor.TOP
    }
}

private fun Element.tableCellLineColor(theme: PptxTheme): Int? {
    val line = children().firstNotNullOfOrNull { child ->
        when (child.localTag()) {
            "lnl", "lnr", "lnt", "lnb", "ln" -> child
            "left", "right", "top", "bottom", "insideh", "insidev" -> child.firstDirectByLocalTag("ln")
            else -> null
        }
    }
    return line?.solidFillColor(theme)
}

private fun Element.tableStylePart(theme: PptxTheme): PptxTableCellStyle {
    val tcStyle = childrenByLocalTag("tcStyle").firstOrNull()
    return PptxTableCellStyle(
        fillColor = tcStyle?.firstDirectByLocalTag("fill")?.solidFillColor(theme),
        lineColor = tcStyle?.firstDirectByLocalTag("tcBdr")?.tableCellLineColor(theme),
        run = childrenByLocalTag("tcTxStyle").firstOrNull()?.tableTextRunStyle(theme) ?: PptxRunStyle()
    )
}

private fun Element.tableTextRunStyle(theme: PptxTheme): PptxRunStyle {
    return PptxRunStyle(
        color = solidLikeColor(theme),
        bold = xmlAttr("b")?.isTruthyXmlFlag(),
        italic = xmlAttr("i")?.isTruthyXmlFlag(),
        typeface = typefaceName(theme)
    )
}

private fun Element.spacingPoints(): Float? {
    firstByLocalTag("spcPts")?.xmlFloat("val")?.let { return it / 100f }
    firstByLocalTag("spcPct")?.xmlFloat("val")?.let { return DEFAULT_TEXT_SIZE_PT * (it / 100_000f) }
    return null
}

private fun Element.spacingMultiple(): Float? {
    firstByLocalTag("spcPct")?.xmlFloat("val")?.let { return it / 100_000f }
    return null
}

private fun Element.autoFitFontScale(): Float? {
    return firstDirectByLocalTag("normAutofit")
        ?.xmlFloat("fontScale")
        ?.let { it / 100_000f }
        ?.coerceIn(0.4f, 2f)
}

private fun Element.autoFitLineSpacingReduction(): Float? {
    return firstDirectByLocalTag("normAutofit")
        ?.xmlFloat("lnSpcReduction")
        ?.let { it / 100_000f }
        ?.coerceIn(0f, 0.5f)
}

private fun Element.autoFitMode(): PptxAutoFitMode {
    return when {
        firstDirectByLocalTag("normAutofit") != null -> PptxAutoFitMode.NORMAL
        firstDirectByLocalTag("spAutoFit") != null -> PptxAutoFitMode.SHAPE
        else -> PptxAutoFitMode.NONE
    }
}

private fun Element.typefaceName(theme: PptxTheme): String? {
    val raw = firstByLocalTag("latin")?.xmlAttr("typeface")
        ?: firstByLocalTag("ea")?.xmlAttr("typeface")
        ?: firstByLocalTag("cs")?.xmlAttr("typeface")
    val resolved = when {
        raw == null -> null
        raw.startsWith("+mj") -> theme.majorTypeface ?: raw
        raw.startsWith("+mn") -> theme.minorTypeface ?: raw
        else -> raw
    }
    return resolved?.takeIf { it.isNotBlank() }
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

private fun Int?.orZero(): Int = this ?: 0

private fun Element.rotationDegreesFromTransform(): Float {
    val xfrm = childrenByLocalTag("xfrm").firstOrNull() ?: firstByLocalTag("xfrm")
    return xfrm?.xmlFloat("rot")?.let { it / 60_000f } ?: 0f
}

private fun Element.solidFillColor(theme: PptxTheme): Int? {
    val solid = childrenByLocalTag("solidFill").firstOrNull() ?: return null
    solid.firstByLocalTag("srgbClr")?.let { color ->
        return color.xmlAttr("val")?.toColorOrNull()?.applyLuminance(color)
    }
    solid.firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.color(scheme)?.applyLuminance(color)
    }
    solid.firstByLocalTag("prstClr")?.let { color ->
        return color.xmlAttr("val")?.presetColorOrNull()?.applyLuminance(color)
    }
    solid.firstByLocalTag("sysClr")?.let { color ->
        return color.xmlAttr("lastClr")?.toColorOrNull()?.applyLuminance(color)
    }
    return null
}

private fun Element.schemeColor(theme: PptxTheme): Int? {
    firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.color(scheme)?.applyLuminance(color)
    }
    firstByLocalTag("prstClr")?.let { color ->
        return color.xmlAttr("val")?.presetColorOrNull()?.applyLuminance(color)
    }
    return xmlAttr("idx")?.let { theme.color(it) }
}

private fun Int.applyLuminance(colorElement: Element): Int {
    val shade = colorElement.firstByLocalTag("shade")?.xmlFloat("val")?.let { it / 100_000f }
    val tint = colorElement.firstByLocalTag("tint")?.xmlFloat("val")?.let { it / 100_000f }
    val mod = colorElement.firstByLocalTag("lumMod")?.xmlFloat("val")?.let { it / 100_000f } ?: 1f
    val off = colorElement.firstByLocalTag("lumOff")?.xmlFloat("val")?.let { it / 100_000f } ?: 0f
    val alpha = colorElement.firstByLocalTag("alpha")?.xmlFloat("val")?.let { it / 100_000f } ?: 1f
    fun channel(value: Int): Int {
        var next = value.toFloat()
        shade?.let { next *= it }
        tint?.let { next += (255f - next) * it }
        next = (next * mod) + (255f * off)
        return next.roundToInt().coerceIn(0, 255)
    }
    return Color.argb(
        (Color.alpha(this) * alpha).roundToInt().coerceIn(0, 255),
        channel(Color.red(this)),
        channel(Color.green(this)),
        channel(Color.blue(this))
    )
}

private fun String.toColorOrNull(): Int? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6) return null
    return runCatching { Color.rgb(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16)) }.getOrNull()
}

private fun String.presetColorOrNull(): Int? {
    return when (lowercase(Locale.ROOT)) {
        "black" -> Color.BLACK
        "white" -> Color.WHITE
        "red" -> Color.RED
        "green" -> Color.GREEN
        "blue" -> Color.BLUE
        "yellow" -> Color.YELLOW
        "cyan" -> Color.CYAN
        "magenta" -> Color.MAGENTA
        "gray", "grey" -> Color.GRAY
        "dkgray", "dkgrey" -> Color.DKGRAY
        "ltgray", "ltgrey" -> Color.LTGRAY
        "orange" -> Color.rgb(255, 165, 0)
        "purple" -> Color.rgb(128, 0, 128)
        "brown" -> Color.rgb(165, 42, 42)
        else -> null
    }
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
    return this == "1" || equals("true", ignoreCase = true) || equals("on", ignoreCase = true)
}

private fun String.placeholderFamily(): String {
    return when (this.lowercase(Locale.ROOT)) {
        "ctrtitle" -> "title"
        "subttl" -> "subtitle"
        else -> this.lowercase(Locale.ROOT)
    }
}

private fun Element.hasBulletDefinition(): Boolean {
    return firstDirectByLocalTag("buNone") != null ||
        firstDirectByLocalTag("buChar") != null ||
        firstDirectByLocalTag("buAutoNum") != null
}

private fun Element.hasTextColor(): Boolean {
    return firstDirectByLocalTag("solidFill") != null ||
        firstDirectByLocalTag("gradFill") != null ||
        firstDirectByLocalTag("noFill") != null
}

private fun Element.hasTypeface(): Boolean {
    return firstByLocalTag("latin") != null ||
        firstByLocalTag("ea") != null ||
        firstByLocalTag("cs") != null
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
    if (type != null && other.type != null && type.placeholderFamily() == other.type.placeholderFamily()) return true
    return index == null && other.index == null && type == null && other.type == null
}

private fun Element.childrenByLocalTag(tag: String): List<Element> {
    val local = tag.lowercase(Locale.ROOT)
    return children().filter { it.localTag() == local }
}

private fun Element.firstDirectByLocalTag(tag: String): Element? = childrenByLocalTag(tag).firstOrNull()

private fun Element.firstByLocalTag(tag: String): Element? {
    val local = tag.lowercase(Locale.ROOT)
    return allElements.firstOrNull { it.localTag() == local }
}

private fun Element.allByLocalTag(tag: String): List<Element> {
    val local = tag.lowercase(Locale.ROOT)
    return allElements.filter { it.localTag() == local }
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
