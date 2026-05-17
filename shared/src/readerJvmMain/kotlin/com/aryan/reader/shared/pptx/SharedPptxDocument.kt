package com.aryan.reader.shared.pptx

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class SharedPptxRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    constructor(source: SharedPptxRect) : this(source.left, source.top, source.right, source.bottom)

    fun width(): Float = (right - left).coerceAtLeast(0f)
    fun height(): Float = (bottom - top).coerceAtLeast(0f)
    fun centerX(): Float = left + width() / 2f
    fun centerY(): Float = top + height() / 2f
    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    fun expanded(dx: Float, dy: Float): SharedPptxRect = SharedPptxRect(left - dx, top - dy, right + dx, bottom + dy)
    fun union(other: SharedPptxRect): SharedPptxRect = SharedPptxRect(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom)
    )
}

data class SharedPptxPoint(val x: Float, val y: Float)

object SharedPptxColor {
    const val TRANSPARENT: Int = 0x00000000
    const val BLACK: Int = -0x1000000
    const val WHITE: Int = -0x1
    const val RED: Int = -0x10000
    const val GREEN: Int = -0xff0100
    const val BLUE: Int = -0xffff01
    const val YELLOW: Int = -0x100
    const val CYAN: Int = -0xff0001
    const val MAGENTA: Int = -0xff01
    const val GRAY: Int = -0x7f7f80
    const val DKGRAY: Int = -0xbbbbbc
    const val LTGRAY: Int = -0x333334

    fun rgb(red: Int, green: Int, blue: Int): Int = argb(255, red, green, blue)

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return ((alpha and 0xff) shl 24) or
            ((red and 0xff) shl 16) or
            ((green and 0xff) shl 8) or
            (blue and 0xff)
    }

    fun alpha(color: Int): Int = color ushr 24
    fun red(color: Int): Int = color shr 16 and 0xff
    fun green(color: Int): Int = color shr 8 and 0xff
    fun blue(color: Int): Int = color and 0xff
}
const val SHARED_PPTX_PARSER_VERSION = 6
private const val EMU_PER_POINT = 12_700f
private const val DEFAULT_SLIDE_WIDTH_EMU = 12_192_000
private const val DEFAULT_SLIDE_HEIGHT_EMU = 6_858_000
private const val DEFAULT_TEXT_SIZE_PT = 18f
private const val DEFAULT_TEXT_MARGIN_PT = 91_440f / EMU_PER_POINT
private const val DEFAULT_LINE_SPACING_MULTIPLE = 1.0f
private const val MAX_NUMBERING_LEVELS = 9

data class SharedPptxDeck(
    val widthPoint: Int,
    val heightPoint: Int,
    val slides: List<SharedPptxSlide>
)

data class SharedPptxSlide(
    val widthPoint: Int,
    val heightPoint: Int,
    val backgroundColor: Int?,
    val elements: List<SharedPptxElement>,
    val text: String,
    val charBoxes: List<SharedPptxCharBox>
)

data class SharedPptxCharBox(
    val char: Char,
    val bounds: SharedPptxRect
)

sealed interface SharedPptxElement {
    val bounds: SharedPptxRect
}

data class SharedPptxShapeElement(
    override val bounds: SharedPptxRect,
    val preset: String,
    val fillColor: Int?,
    val gradientFill: SharedPptxGradientFill? = null,
    val lineColor: Int?,
    val lineWidthPoint: Float,
    val paragraphs: List<SharedPptxParagraph>,
    val hyperlink: String?,
    val placeholderKey: SharedPptxPlaceholderKey?,
    val textInsets: SharedPptxTextInsets = SharedPptxTextInsets(),
    val verticalAnchor: SharedPptxVerticalAnchor = SharedPptxVerticalAnchor.TOP,
    val rotationDegrees: Float = 0f,
    val renderText: Boolean = true,
    val fontScale: Float = 1f,
    val lineSpacingReduction: Float = 0f,
    val autoFitMode: SharedPptxAutoFitMode = SharedPptxAutoFitMode.NONE,
    val customGeometry: SharedPptxCustomGeometry? = null
) : SharedPptxElement

data class SharedPptxImageElement(
    override val bounds: SharedPptxRect,
    val bytes: ByteArray,
    val contentType: String?,
    val crop: SharedPptxImageCrop = SharedPptxImageCrop(),
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f
) : SharedPptxElement

data class SharedPptxImageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

data class SharedPptxTableElement(
    override val bounds: SharedPptxRect,
    val rows: List<SharedPptxTableRow>,
    val rotationDegrees: Float = 0f
) : SharedPptxElement

data class SharedPptxTableRow(
    val heightPoint: Float?,
    val cells: List<SharedPptxTableCell>
)

data class SharedPptxTableCell(
    val widthPoint: Float?,
    val fillColor: Int?,
    val lineColor: Int?,
    val paragraphs: List<SharedPptxParagraph>,
    val textInsets: SharedPptxTextInsets = SharedPptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f),
    val verticalAnchor: SharedPptxVerticalAnchor = SharedPptxVerticalAnchor.TOP
)

data class SharedPptxParagraph(
    val runs: List<SharedPptxTextRun>,
    val alignment: SharedPptxTextAlign = SharedPptxTextAlign.START,
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

data class SharedPptxTextRun(
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

enum class SharedPptxTextAlign {
    START,
    CENTER,
    END
}

enum class SharedPptxVerticalAnchor {
    TOP,
    MIDDLE,
    BOTTOM
}

enum class SharedPptxAutoFitMode {
    NONE,
    NORMAL,
    SHAPE
}

data class SharedPptxTextInsets(
    val left: Float = DEFAULT_TEXT_MARGIN_PT,
    val top: Float = DEFAULT_TEXT_MARGIN_PT,
    val right: Float = DEFAULT_TEXT_MARGIN_PT,
    val bottom: Float = DEFAULT_TEXT_MARGIN_PT
)

data class SharedPptxGradientFill(
    val startColor: Int,
    val endColor: Int,
    val angleDegrees: Float = 0f
)

data class SharedPptxCustomGeometry(
    val width: Float,
    val height: Float,
    val commands: List<SharedPptxPathCommand>
)

sealed interface SharedPptxPathCommand {
    data class MoveTo(val x: Float, val y: Float) : SharedPptxPathCommand
    data class LineTo(val x: Float, val y: Float) : SharedPptxPathCommand
    data class QuadTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : SharedPptxPathCommand
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x3: Float,
        val y3: Float
    ) : SharedPptxPathCommand
    object Close : SharedPptxPathCommand
}

data class SharedPptxPlaceholderKey(
    val type: String?,
    val index: String?
)

private data class SharedPptxRelationships(
    val byId: Map<String, SharedPptxRelationship>
)

private data class SharedPptxRelationship(
    val id: String,
    val target: String,
    val resolvedTarget: String,
    val type: String,
    val targetMode: String?
)

private data class SharedPptxTheme(
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
    val elements: List<SharedPptxElement> = emptyList(),
    val textDefaults: SharedPptxTextDefaults = SharedPptxTextDefaults()
)

private data class SharedPptxTextDefaults(
    val title: Map<Int, SharedPptxParagraphStyle> = emptyMap(),
    val body: Map<Int, SharedPptxParagraphStyle> = emptyMap(),
    val other: Map<Int, SharedPptxParagraphStyle> = emptyMap()
) {
    fun merge(override: SharedPptxTextDefaults): SharedPptxTextDefaults {
        return SharedPptxTextDefaults(
            title = title.mergeStyles(override.title),
            body = body.mergeStyles(override.body),
            other = other.mergeStyles(override.other)
        )
    }

    fun forPlaceholder(key: SharedPptxPlaceholderKey?): Map<Int, SharedPptxParagraphStyle> {
        return when (key?.type?.placeholderFamily()) {
            "title" -> title
            "body", null -> if (key != null) body else other
            "subtitle" -> other.ifEmpty { body }
            else -> other
        }
    }
}

private data class SharedPptxParagraphStyle(
    val alignment: SharedPptxTextAlign? = null,
    val bullet: String? = null,
    val autoNumberType: String? = null,
    val autoNumberStartAt: Int? = null,
    val bulletExplicit: Boolean = false,
    val marginLeftPt: Float? = null,
    val indentPt: Float? = null,
    val spaceBeforePt: Float? = null,
    val spaceAfterPt: Float? = null,
    val lineSpacingMultiple: Float? = null,
    val run: SharedPptxRunStyle = SharedPptxRunStyle()
) {
    fun merge(override: SharedPptxParagraphStyle): SharedPptxParagraphStyle {
        return SharedPptxParagraphStyle(
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

private data class SharedPptxRunStyle(
    val sizePt: Float? = null,
    val color: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val typeface: String? = null,
    val baseline: Float? = null
) {
    fun merge(override: SharedPptxRunStyle): SharedPptxRunStyle {
        return SharedPptxRunStyle(
            sizePt = override.sizePt ?: sizePt,
            color = override.color ?: color,
            bold = override.bold ?: bold,
            italic = override.italic ?: italic,
            typeface = override.typeface ?: typeface,
            baseline = override.baseline ?: baseline
        )
    }
}

private data class SharedPptxTableStyle(
    val whole: SharedPptxTableCellStyle = SharedPptxTableCellStyle(),
    val firstRow: SharedPptxTableCellStyle = SharedPptxTableCellStyle(),
    val lastRow: SharedPptxTableCellStyle = SharedPptxTableCellStyle(),
    val firstColumn: SharedPptxTableCellStyle = SharedPptxTableCellStyle(),
    val lastColumn: SharedPptxTableCellStyle = SharedPptxTableCellStyle(),
    val band1Horizontal: SharedPptxTableCellStyle = SharedPptxTableCellStyle(),
    val band2Horizontal: SharedPptxTableCellStyle = SharedPptxTableCellStyle()
) {
    fun cellStyle(
        rowIndex: Int,
        columnIndex: Int,
        rowCount: Int,
        columnCount: Int,
        options: SharedPptxTableStyleOptions
    ): SharedPptxTableCellStyle {
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

private data class SharedPptxTableCellStyle(
    val fillColor: Int? = null,
    val lineColor: Int? = null,
    val run: SharedPptxRunStyle = SharedPptxRunStyle()
) {
    fun merge(override: SharedPptxTableCellStyle): SharedPptxTableCellStyle {
        return SharedPptxTableCellStyle(
            fillColor = override.fillColor ?: fillColor,
            lineColor = override.lineColor ?: lineColor,
            run = run.merge(override.run)
        )
    }
}

private data class SharedPptxTableStyleOptions(
    val firstRow: Boolean = false,
    val lastRow: Boolean = false,
    val firstColumn: Boolean = false,
    val lastColumn: Boolean = false,
    val bandRow: Boolean = false
)

private data class SharedPptxGroupTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotationDegrees: Float = 0f
) {
    fun then(child: SharedPptxGroupTransform): SharedPptxGroupTransform {
        return SharedPptxGroupTransform(
            scaleX = scaleX * child.scaleX,
            scaleY = scaleY * child.scaleY,
            dx = dx + child.dx * scaleX,
            dy = dy + child.dy * scaleY,
            rotationDegrees = rotationDegrees + child.rotationDegrees
        )
    }

    fun apply(element: SharedPptxElement): SharedPptxElement {
        if (this == IDENTITY) return element
        return when (element) {
            is SharedPptxShapeElement -> element.copy(
                bounds = mapRect(element.bounds),
                lineWidthPoint = element.lineWidthPoint * averageScale(),
                rotationDegrees = element.rotationDegrees + rotationDegrees
            )
            is SharedPptxImageElement -> element.copy(
                bounds = mapRect(element.bounds),
                rotationDegrees = element.rotationDegrees + rotationDegrees
            )
            is SharedPptxTableElement -> element.copy(
                bounds = mapRect(element.bounds),
                rotationDegrees = element.rotationDegrees + rotationDegrees
            )
        }
    }

    private fun mapRect(rect: SharedPptxRect): SharedPptxRect {
        val left = rect.left * scaleX + dx
        val right = rect.right * scaleX + dx
        val top = rect.top * scaleY + dy
        val bottom = rect.bottom * scaleY + dy
        return SharedPptxRect(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
    }

    private fun averageScale(): Float = ((scaleX + scaleY) / 2f).coerceAtLeast(0.01f)

    companion object {
        val IDENTITY = SharedPptxGroupTransform()

        fun fromGroup(group: Element): SharedPptxGroupTransform {
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
            return SharedPptxGroupTransform(
                scaleX = scaleX,
                scaleY = scaleY,
                dx = offX - childX * scaleX,
                dy = offY - childY * scaleY,
                rotationDegrees = xfrm.xmlFloat("rot")?.let { it / 60_000f } ?: 0f
            )
        }
    }
}

object SharedPptxDeckCache {
    private const val MAX_ENTRIES = 4
    private val cache = LinkedHashMap<String, SharedPptxDeck>()

    fun load(file: File): SharedPptxDeck {
        val key = "${file.contentHash()}:${file.length()}:$SHARED_PPTX_PARSER_VERSION"
        synchronized(cache) {
            cache[key]?.let { cached ->
                cache.remove(key)
                cache[key] = cached
                return cached
            }
        }
        val parsed = SharedPptxDocumentParser.parse(file)
        synchronized(cache) {
            cache[key] = parsed
            while (cache.size > MAX_ENTRIES) {
                cache.remove(cache.keys.firstOrNull() ?: break)
            }
        }
        return parsed
    }
}

object SharedPptxDocumentParser {
    fun parse(file: File): SharedPptxDeck {
        ZipFile(file).use { zip ->
            val presentation = zip.xml("ppt/presentation.xml")
                ?: error("ppt/presentation.xml not found in SharedPptx archive.")
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
                    .getOrNull()
            }

            return SharedPptxDeck(
                widthPoint = width,
                heightPoint = height,
                slides = slides.ifEmpty {
                    listOf(
                        SharedPptxSlide(
                            widthPoint = width,
                            heightPoint = height,
                            backgroundColor = SharedPptxColor.WHITE,
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
    ): SharedPptxSlide {
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
        val theme = themePath?.let { path -> zip.xml(path)?.let(::parseTheme) } ?: SharedPptxTheme()
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
        val backgroundColor = slide.backgroundColor ?: layout.backgroundColor ?: master.backgroundColor ?: SharedPptxColor.WHITE
        val textIndex = SharedPptxTextIndexer.index(elements)

        return SharedPptxSlide(
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
        relationships: SharedPptxRelationships,
        theme: SharedPptxTheme,
        renderPlaceholderText: Boolean,
        inheritedTextDefaults: SharedPptxTextDefaults = SharedPptxTextDefaults()
    ): ParsedPart {
        val partTheme = theme.withColorMap(document.colorMapElement())
        val textDefaults = inheritedTextDefaults.merge(document.textDefaults(partTheme))
        val background = document.firstByLocalTag("bgPr")?.solidFillColor(partTheme)
            ?: document.firstByLocalTag("bgRef")?.schemeColor(partTheme)
        val tableStyles = zip.tableStyles(partTheme)
        val elements = mutableListOf<SharedPptxElement>()
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
        relationships: SharedPptxRelationships,
        theme: SharedPptxTheme,
        renderPlaceholderText: Boolean,
        textDefaults: SharedPptxTextDefaults,
        tableStyles: Map<String, SharedPptxTableStyle>,
        output: MutableList<SharedPptxElement>,
        transform: SharedPptxGroupTransform = SharedPptxGroupTransform.IDENTITY
    ) {
        when (element.localTag()) {
            "sp", "cxnsp" -> parseShape(element, relationships, theme, renderPlaceholderText, textDefaults)
                ?.let { output += transform.apply(it) }
            "pic" -> parseImage(zip, element, relationships)
                ?.let { output += transform.apply(it) }
            "grpsp" -> element.children().forEach { child ->
                val childTransform = transform.then(SharedPptxGroupTransform.fromGroup(element))
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
        relationships: SharedPptxRelationships,
        theme: SharedPptxTheme,
        renderPlaceholderText: Boolean,
        textDefaults: SharedPptxTextDefaults
    ): SharedPptxShapeElement? {
        val spPr = element.childrenByLocalTag("spPr").firstOrNull()
        val bounds = spPr?.boundsFromTransform() ?: element.boundsFromTransform()
        val txBody = element.firstByLocalTag("txBody")
        val bodyPr = txBody?.childrenByLocalTag("bodyPr")?.firstOrNull()
        val preset = spPr?.childrenByLocalTag("prstGeom")?.firstOrNull()?.xmlAttr("prst")
            ?: if (element.localTag() == "cxnsp") "line" else "rect"
        val customGeometry = spPr?.childrenByLocalTag("custGeom")?.firstOrNull()?.customGeometry()
        val placeholderKey = element.firstByLocalTag("ph")?.placeholderKey()
        val style = element.childrenByLocalTag("style").firstOrNull()
        val shapeRunDefaults = SharedPptxRunStyle(color = style?.firstByLocalTag("fontRef")?.solidLikeColor(theme))
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
        return SharedPptxShapeElement(
            bounds = bounds,
            preset = preset.lowercase(Locale.ROOT),
            fillColor = fillColor,
            gradientFill = gradientFill,
            lineColor = lineColor,
            lineWidthPoint = lineWidth,
            paragraphs = paragraphs,
            hyperlink = hyperlink,
            placeholderKey = placeholderKey,
            textInsets = bodyPr?.textInsets() ?: SharedPptxTextInsets(),
            verticalAnchor = bodyPr?.verticalAnchor() ?: SharedPptxVerticalAnchor.TOP,
            rotationDegrees = spPr?.rotationDegreesFromTransform() ?: element.rotationDegreesFromTransform(),
            renderText = placeholderKey == null || renderPlaceholderText,
            fontScale = bodyPr?.autoFitFontScale() ?: 1f,
            lineSpacingReduction = bodyPr?.autoFitLineSpacingReduction() ?: 0f,
            autoFitMode = bodyPr?.autoFitMode() ?: SharedPptxAutoFitMode.NONE,
            customGeometry = customGeometry
        )
    }

    private fun parseImage(
        zip: ZipFile,
        element: Element,
        relationships: SharedPptxRelationships
    ): SharedPptxImageElement? {
        val blip = element.firstByLocalTag("blip") ?: return null
        val relId = blip.xmlAttr("r:embed") ?: blip.xmlAttr("r:link") ?: return null
        val rel = relationships.byId[relId] ?: return null
        val target = rel.resolvedTarget
        val entry = zip.getEntry(target) ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val crop = element.firstByLocalTag("srcRect")?.imageCrop() ?: SharedPptxImageCrop()
        val bounds = element.childrenByLocalTag("spPr").firstOrNull()?.boundsFromTransform()
            ?: element.boundsFromTransform()
        return SharedPptxImageElement(
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
        relationships: SharedPptxRelationships,
        theme: SharedPptxTheme,
        tableStyles: Map<String, SharedPptxTableStyle>
    ): SharedPptxElement? {
        val table = element.firstByLocalTag("tbl") ?: return parseGraphicPlaceholder(element, relationships, theme)
        val bounds = element.boundsFromTransform()
        val tblPr = table.childrenByLocalTag("tblPr").firstOrNull()
        val tableStyle = tblPr
            ?.firstDirectByLocalTag("tableStyleId")
            ?.wholeText()
            ?.trim()
            ?.let { tableStyles[it] }
        val styleOptions = SharedPptxTableStyleOptions(
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
                val textInsets = tcPr?.textInsets() ?: SharedPptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f)
                SharedPptxTableCell(
                    widthPoint = gridWidths.getOrNull(index),
                    fillColor = when {
                        tcPr?.firstDirectByLocalTag("noFill") != null -> null
                        else -> tcPr?.solidFillColor(theme) ?: style?.fillColor
                    },
                    lineColor = tcPr?.tableCellLineColor(theme) ?: style?.lineColor,
                    paragraphs = parseTextBody(
                        txBody = cell.childrenByLocalTag("txBody").firstOrNull(),
                        theme = theme,
                        shapeRunDefaults = style?.run ?: SharedPptxRunStyle()
                    ),
                    textInsets = textInsets,
                    verticalAnchor = tcPr?.verticalAnchor() ?: SharedPptxVerticalAnchor.TOP
                )
            }
            SharedPptxTableRow(
                heightPoint = row.xmlFloat("h")?.emuToPoint(),
                cells = cells
            )
        }
        if (rows.all { row -> row.cells.all { it.paragraphs.isEmpty() } }) return null
        return SharedPptxTableElement(
            bounds = bounds,
            rows = rows,
            rotationDegrees = element.rotationDegreesFromTransform()
        )
    }

    private fun parseGraphicPlaceholder(
        element: Element,
        relationships: SharedPptxRelationships,
        theme: SharedPptxTheme
    ): SharedPptxElement? {
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
        return SharedPptxShapeElement(
            bounds = bounds,
            preset = "rect",
            fillColor = SharedPptxColor.rgb(245, 246, 248),
            gradientFill = null,
            lineColor = theme.color("tx1") ?: SharedPptxColor.GRAY,
            lineWidthPoint = 0.75f,
            paragraphs = listOf(
                SharedPptxParagraph(
                    runs = listOf(SharedPptxTextRun(target?.let { "$label: ${it.substringAfterLast('/')}" } ?: label)),
                    alignment = SharedPptxTextAlign.CENTER
                )
            ),
            hyperlink = null,
            placeholderKey = null,
            textInsets = SharedPptxTextInsets(left = 8f, top = 8f, right = 8f, bottom = 8f),
            verticalAnchor = SharedPptxVerticalAnchor.MIDDLE,
            rotationDegrees = element.rotationDegreesFromTransform()
        )
    }

    private fun parseTextBody(
        txBody: Element?,
        theme: SharedPptxTheme,
        inheritedStyles: Map<Int, SharedPptxParagraphStyle> = emptyMap(),
        shapeRunDefaults: SharedPptxRunStyle = SharedPptxRunStyle()
    ): List<SharedPptxParagraph> {
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
            val paragraphStyle = (styles[level] ?: styles[0] ?: SharedPptxParagraphStyle())
                .merge(pPr?.paragraphStyle(theme) ?: SharedPptxParagraphStyle())
            val paragraphRunStyle = shapeRunDefaults
                .merge(paragraphStyle.run)
                .merge(endParaRunPr?.runStyle(theme) ?: SharedPptxRunStyle())
            val runs = mutableListOf<SharedPptxTextRun>()
            paragraph.children().forEach { child ->
                when (child.localTag()) {
                    "r", "fld" -> {
                        val rPr = child.childrenByLocalTag("rPr").firstOrNull()
                        val runStyle = paragraphRunStyle.merge(rPr?.runStyle(theme) ?: SharedPptxRunStyle())
                        val text = child.firstByLocalTag("t")?.wholeText().orEmpty()
                        if (text.isNotEmpty()) {
                            runs += SharedPptxTextRun(
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
                        val runStyle = paragraphRunStyle.merge(rPr?.runStyle(theme) ?: SharedPptxRunStyle())
                        runs += SharedPptxTextRun(
                            "\n",
                            sizePt = runStyle.sizePt,
                            color = runStyle.color,
                            bold = runStyle.bold ?: false,
                            italic = runStyle.italic ?: false,
                            typeface = runStyle.typeface,
                            baseline = runStyle.baseline ?: 0f
                        )
                    }
                    "tab" -> runs += SharedPptxTextRun(
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
            val safeRuns = runs.ifEmpty { listOf(SharedPptxTextRun("")) }
            if (safeRuns.none { it.text.isNotBlank() }) return@mapNotNull null
            val bullet = paragraphStyle.resolvedBullet(level, numberCounters, numberCounterStarted)
            SharedPptxParagraph(
                runs = safeRuns,
                alignment = paragraphStyle.alignment ?: SharedPptxTextAlign.START,
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

    private fun parseTheme(document: Element): SharedPptxTheme {
        val scheme = document.firstByLocalTag("clrScheme") ?: return SharedPptxTheme()
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
        return SharedPptxTheme(
            colors = aliases,
            majorTypeface = majorTypeface,
            minorTypeface = minorTypeface
        )
    }

    private fun Element.presentationTextDefaults(theme: SharedPptxTheme): SharedPptxTextDefaults {
        val defaults = firstByLocalTag("defaultTextStyle")
            ?.paragraphStyles(theme)
            .orEmpty()
        if (defaults.isEmpty()) return SharedPptxTextDefaults()
        return SharedPptxTextDefaults(title = defaults, body = defaults, other = defaults)
    }

    private fun ZipFile.xml(path: String): Element? {
        val entry = getEntry(path) ?: return null
        return getInputStream(entry).use { input ->
            Jsoup.parse(input, null, "", Parser.xmlParser())
        }
    }

    private fun ZipFile.relationshipsFor(partPath: String): SharedPptxRelationships {
        val relsPath = partPath.relationshipsPath()
        val document = xml(relsPath) ?: return SharedPptxRelationships(emptyMap())
        val rels = document.allByLocalTag("Relationship").mapNotNull { rel ->
            val id = rel.xmlAttr("Id") ?: return@mapNotNull null
            val target = rel.xmlAttr("Target") ?: return@mapNotNull null
            val type = rel.xmlAttr("Type").orEmpty()
            SharedPptxRelationship(
                id = id,
                target = target,
                resolvedTarget = resolveRelationshipTarget(partPath, target, rel.xmlAttr("TargetMode")),
                type = type,
                targetMode = rel.xmlAttr("TargetMode")
            )
        }.associateBy { it.id }
        return SharedPptxRelationships(rels)
    }

    private fun ZipFile.tableStyles(theme: SharedPptxTheme): Map<String, SharedPptxTableStyle> {
        val document = xml("ppt/tableStyles.xml") ?: return emptyMap()
        return document.allByLocalTag("tblStyle").mapNotNull { style ->
            val id = style.xmlAttr("styleId") ?: return@mapNotNull null
            id to SharedPptxTableStyle(
                whole = style.childrenByLocalTag("wholeTbl").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle(),
                firstRow = style.childrenByLocalTag("firstRow").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle(),
                lastRow = style.childrenByLocalTag("lastRow").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle(),
                firstColumn = style.childrenByLocalTag("firstCol").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle(),
                lastColumn = style.childrenByLocalTag("lastCol").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle(),
                band1Horizontal = style.childrenByLocalTag("band1H").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle(),
                band2Horizontal = style.childrenByLocalTag("band2H").firstOrNull()?.tableStylePart(theme) ?: SharedPptxTableCellStyle()
            )
        }.toMap()
    }
}

private fun SharedPptxTheme.withColorMap(mapping: Element?): SharedPptxTheme {
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
    slideElements: List<SharedPptxElement>,
    inheritedElements: List<SharedPptxElement>
): List<SharedPptxElement> {
    val inheritedPlaceholders = inheritedElements
        .filterIsInstance<SharedPptxShapeElement>()
        .filter { it.placeholderKey != null && it.bounds.width() > 0f && it.bounds.height() > 0f }

    if (inheritedPlaceholders.isEmpty()) return slideElements

    return slideElements.map { element ->
        val shape = element as? SharedPptxShapeElement ?: return@map element
        val key = shape.placeholderKey ?: return@map shape

        val inherited = inheritedPlaceholders.lastOrNull { inherited ->
            inherited.placeholderKey?.matches(key) == true
        } ?: return@map shape
        val shouldInheritBounds = shape.bounds.width() <= 0f || shape.bounds.height() <= 0f

        shape.copy(
            bounds = if (shouldInheritBounds) SharedPptxRect(inherited.bounds) else shape.bounds,
            preset = if (shouldInheritBounds && shape.preset == "rect") inherited.preset else shape.preset,
            fillColor = shape.fillColor ?: inherited.fillColor,
            gradientFill = shape.gradientFill ?: inherited.gradientFill,
            lineColor = shape.lineColor ?: inherited.lineColor,
            lineWidthPoint = if (shape.lineWidthPoint == 0.75f) inherited.lineWidthPoint else shape.lineWidthPoint,
            paragraphs = shape.paragraphs.inheritTextStyles(inherited.paragraphs),
            textInsets = if (shape.textInsets == SharedPptxTextInsets()) inherited.textInsets else shape.textInsets,
            verticalAnchor = if (shape.verticalAnchor == SharedPptxVerticalAnchor.TOP) inherited.verticalAnchor else shape.verticalAnchor,
            rotationDegrees = if (shape.rotationDegrees == 0f) inherited.rotationDegrees else shape.rotationDegrees,
            fontScale = if (shape.fontScale == 1f) inherited.fontScale else shape.fontScale,
            lineSpacingReduction = if (shape.lineSpacingReduction == 0f) {
                inherited.lineSpacingReduction
            } else {
                shape.lineSpacingReduction
            },
            autoFitMode = if (shape.autoFitMode == SharedPptxAutoFitMode.NONE) inherited.autoFitMode else shape.autoFitMode,
            customGeometry = shape.customGeometry ?: inherited.customGeometry
        )
    }
}

private fun List<SharedPptxParagraph>.inheritTextStyles(fallback: List<SharedPptxParagraph>): List<SharedPptxParagraph> {
    if (isEmpty() || fallback.isEmpty()) return this
    return mapIndexed { index, paragraph ->
        val fallbackParagraph = fallback.firstOrNull { it.level == paragraph.level }
            ?: fallback.getOrNull(index)
            ?: fallback.first()
        paragraph.inheritTextStyle(fallbackParagraph)
    }
}

private fun SharedPptxParagraph.inheritTextStyle(fallback: SharedPptxParagraph): SharedPptxParagraph {
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

private fun SharedPptxTextRun.inheritTextStyle(fallback: SharedPptxTextRun?): SharedPptxTextRun {
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

private data class SharedPptxTextIndex(
    val text: String,
    val charBoxes: List<SharedPptxCharBox>
)

private object SharedPptxTextIndexer {
    fun index(elements: List<SharedPptxElement>): SharedPptxTextIndex {
        val text = StringBuilder()
        val charBoxes = mutableListOf<SharedPptxCharBox>()
        elements.forEach { element ->
            when (element) {
                is SharedPptxShapeElement -> appendShapeText(element, text, charBoxes)
                is SharedPptxTableElement -> layoutTableCells(element).forEach { cell ->
                    appendShapeText(cell.asShape(), text, charBoxes)
                }
                is SharedPptxImageElement -> Unit
            }
        }
        val indexedText = text.toString().trimEnd()
        return SharedPptxTextIndex(indexedText, charBoxes.take(indexedText.length))
    }

    private fun appendShapeText(
        shape: SharedPptxShapeElement,
        text: StringBuilder,
        charBoxes: MutableList<SharedPptxCharBox>
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
        val effectiveBounds = if (shape.autoFitMode == SharedPptxAutoFitMode.SHAPE && totalHeight > textBounds.height()) {
            textBounds.copy(bottom = textBounds.top + totalHeight)
        } else {
            textBounds
        }
        var y = when (shape.verticalAnchor) {
            SharedPptxVerticalAnchor.TOP -> effectiveBounds.top
            SharedPptxVerticalAnchor.MIDDLE -> effectiveBounds.top + ((effectiveBounds.height() - totalHeight) / 2f).coerceAtLeast(0f)
            SharedPptxVerticalAnchor.BOTTOM -> effectiveBounds.bottom - totalHeight.coerceAtMost(effectiveBounds.height())
        }

        paragraphs.forEachIndexed { index, (paragraph, displayText) ->
            y += paragraph.spaceBeforePt
            displayText.lines().forEach { line ->
                if (shape.autoFitMode != SharedPptxAutoFitMode.SHAPE && y > effectiveBounds.bottom) return@forEach
                appendLine(line, paragraph, shape, effectiveBounds, y, text, charBoxes)
                y += paragraph.approximateLineHeight(shape)
            }
            y += paragraph.spaceAfterPt
            if (index < paragraphs.lastIndex && text.lastOrNull() != '\n') {
                text.append('\n')
                charBoxes += SharedPptxCharBox('\n', SharedPptxRect(shape.bounds.left, shape.bounds.bottom, shape.bounds.left, shape.bounds.bottom))
            }
        }
    }

    private fun appendLine(
        line: String,
        paragraph: SharedPptxParagraph,
        shape: SharedPptxShapeElement,
        textBounds: SharedPptxRect,
        y: Float,
        text: StringBuilder,
        charBoxes: MutableList<SharedPptxCharBox>
    ) {
        if (line.isEmpty()) {
            text.append('\n')
            charBoxes += SharedPptxCharBox('\n', SharedPptxRect(textBounds.left, y, textBounds.left, y))
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
            SharedPptxTextAlign.START -> textBounds.left
            SharedPptxTextAlign.CENTER -> textBounds.left + ((textBounds.width() - lineWidth) / 2f).coerceAtLeast(0f)
            SharedPptxTextAlign.END -> textBounds.right - lineWidth
        }
        val bottom = y + paragraph.approximateLineHeight(shape)

        text.append(line)
        line.forEachIndexed { index, char ->
            val left = startX + index * charAdvance
            val right = if (index == line.lastIndex) startX + lineWidth else left + charAdvance
            charBoxes += SharedPptxCharBox(
                char = char,
                bounds = SharedPptxRect(left, y, right, bottom).rotatedBounds(shape.bounds, shape.rotationDegrees)
            )
        }
    }

    private fun String.lineCount(): Int = lines().size.coerceAtLeast(1)

    private fun SharedPptxParagraph.approximateLineHeight(shape: SharedPptxShapeElement): Float {
        val fontSize = scaledTextSize(runs.firstOrNull()?.sizePt, shape.fontScale).toFloat()
        return (fontSize * 1.2f * effectiveLineSpacing(shape)).coerceAtLeast(1f)
    }
}

private data class LaidOutTableCell(
    val rect: SharedPptxRect,
    val cell: SharedPptxTableCell
)

private fun LaidOutTableCell.asShape(): SharedPptxShapeElement {
    return SharedPptxShapeElement(
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

private fun SharedPptxParagraph.effectiveLineSpacing(shape: SharedPptxShapeElement): Float {
    val reduced = lineSpacingMultiple * (1f - shape.lineSpacingReduction)
    return reduced.coerceIn(0.9f, 2.5f)
}

private fun SharedPptxShapeElement.textBounds(sourceBounds: SharedPptxRect = bounds): SharedPptxRect {
    return SharedPptxRect(
        sourceBounds.left + textInsets.left,
        sourceBounds.top + textInsets.top,
        sourceBounds.right - textInsets.right,
        sourceBounds.bottom - textInsets.bottom
    )
}

private fun SharedPptxParagraph.displayText(): String {
    val text = runs.joinToString("") { it.text }
    val prefix = bullet?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return prefix + text
}

private fun SharedPptxRect.rotatedBounds(bounds: SharedPptxRect, rotationDegrees: Float): SharedPptxRect {
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
        SharedPptxPoint(cx + dx * cosValue - dy * sinValue, cy + dx * sinValue + dy * cosValue)
    }
    return SharedPptxRect(
        points.minOf { it.x },
        points.minOf { it.y },
        points.maxOf { it.x },
        points.maxOf { it.y }
    )
}

private fun layoutTableCells(table: SharedPptxTableElement): List<LaidOutTableCell> {
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
                rect = SharedPptxRect(x, y, x + cellWidth, y + rowHeight),
                cell = cell
            )
            x += cellWidth
        }
        y += rowHeight
    }
    return cells
}

private fun Element.textDefaults(theme: SharedPptxTheme): SharedPptxTextDefaults {
    val txStyles = firstByLocalTag("txStyles") ?: return SharedPptxTextDefaults()
    return SharedPptxTextDefaults(
        title = txStyles.childrenByLocalTag("titleStyle").firstOrNull()?.paragraphStyles(theme).orEmpty(),
        body = txStyles.childrenByLocalTag("bodyStyle").firstOrNull()?.paragraphStyles(theme).orEmpty(),
        other = txStyles.childrenByLocalTag("otherStyle").firstOrNull()?.paragraphStyles(theme).orEmpty()
    )
}

private fun Element.paragraphStyles(theme: SharedPptxTheme): Map<Int, SharedPptxParagraphStyle> {
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

private fun Element.paragraphStyle(theme: SharedPptxTheme): SharedPptxParagraphStyle {
    val autoNumber = firstDirectByLocalTag("buAutoNum")
    val bulletTypeface = firstDirectByLocalTag("buFont")?.xmlAttr("typeface")
    val bullet = when {
        firstDirectByLocalTag("buNone") != null -> null
        autoNumber != null -> null
        else -> firstDirectByLocalTag("buChar")?.xmlAttr("char")?.normalizeBulletGlyph(bulletTypeface)
    }
    return SharedPptxParagraphStyle(
        alignment = when (xmlAttr("algn")) {
            "l", "just", "justLow", "dist", "thaiDist" -> SharedPptxTextAlign.START
            "ctr" -> SharedPptxTextAlign.CENTER
            "r" -> SharedPptxTextAlign.END
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
        run = childrenByLocalTag("defRPr").firstOrNull()?.runStyle(theme) ?: SharedPptxRunStyle()
    )
}

private fun Element.runStyle(theme: SharedPptxTheme): SharedPptxRunStyle {
    return SharedPptxRunStyle(
        sizePt = xmlFloat("sz")?.let { it / 100f },
        color = solidFillColor(theme),
        bold = xmlAttr("b")?.isTruthyXmlFlag(),
        italic = xmlAttr("i")?.isTruthyXmlFlag(),
        typeface = typefaceName(theme),
        baseline = xmlFloat("baseline")?.let { it / 100_000f }
    )
}

private fun SharedPptxParagraphStyle.resolvedBullet(
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

private fun Map<Int, SharedPptxParagraphStyle>.mergeStyles(
    overrides: Map<Int, SharedPptxParagraphStyle>
): Map<Int, SharedPptxParagraphStyle> {
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

private fun Element.imageCrop(): SharedPptxImageCrop {
    return SharedPptxImageCrop(
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

private fun Element.customGeometry(): SharedPptxCustomGeometry? {
    val paths = firstByLocalTag("pathLst")?.childrenByLocalTag("path").orEmpty()
    if (paths.isEmpty()) return null
    val width = paths.first().xmlFloat("w")?.takeIf { it > 0f } ?: return null
    val height = paths.first().xmlFloat("h")?.takeIf { it > 0f } ?: return null
    val commands = paths.flatMap { path ->
        path.children().mapNotNull { command ->
            when (command.localTag()) {
                "moveto" -> command.firstDirectByLocalTag("pt")?.pathPoint()?.let { SharedPptxPathCommand.MoveTo(it.x, it.y) }
                "lnto" -> command.firstDirectByLocalTag("pt")?.pathPoint()?.let { SharedPptxPathCommand.LineTo(it.x, it.y) }
                "quadbezto" -> {
                    val points = command.childrenByLocalTag("pt").mapNotNull { it.pathPoint() }
                    if (points.size >= 2) {
                        SharedPptxPathCommand.QuadTo(points[0].x, points[0].y, points[1].x, points[1].y)
                    } else {
                        null
                    }
                }
                "cubicbezto" -> {
                    val points = command.childrenByLocalTag("pt").mapNotNull { it.pathPoint() }
                    if (points.size >= 3) {
                        SharedPptxPathCommand.CubicTo(
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
                "close" -> SharedPptxPathCommand.Close
                else -> null
            }
        }
    }
    return SharedPptxCustomGeometry(width = width, height = height, commands = commands)
        .takeIf { it.commands.isNotEmpty() }
}

private fun Element.pathPoint(): SharedPptxPoint? {
    val x = xmlFloat("x") ?: return null
    val y = xmlFloat("y") ?: return null
    return SharedPptxPoint(x, y)
}

private fun Element.gradientFill(theme: SharedPptxTheme): SharedPptxGradientFill? {
    val gradFill = childrenByLocalTag("gradFill").firstOrNull() ?: return null
    val stops = gradFill.firstByLocalTag("gsLst")
        ?.childrenByLocalTag("gs")
        ?.mapNotNull { stop -> stop.solidLikeColor(theme)?.let { stop.xmlInt("pos").orZero() to it } }
        ?.sortedBy { it.first }
        .orEmpty()
    if (stops.size < 2) return null
    val angle = gradFill.firstByLocalTag("lin")?.xmlFloat("ang")?.let { it / 60_000f } ?: 0f
    return SharedPptxGradientFill(
        startColor = stops.first().second,
        endColor = stops.last().second,
        angleDegrees = angle
    )
}

private fun Element.solidLikeColor(theme: SharedPptxTheme): Int? {
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

private fun Element.textInsets(): SharedPptxTextInsets {
    return SharedPptxTextInsets(
        left = (xmlFloat("lIns") ?: xmlFloat("marL"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT,
        top = (xmlFloat("tIns") ?: xmlFloat("marT"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT,
        right = (xmlFloat("rIns") ?: xmlFloat("marR"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT,
        bottom = (xmlFloat("bIns") ?: xmlFloat("marB"))?.emuToPoint() ?: DEFAULT_TEXT_MARGIN_PT
    )
}

private fun Element.verticalAnchor(): SharedPptxVerticalAnchor {
    return when (xmlAttr("anchor")) {
        "ctr" -> SharedPptxVerticalAnchor.MIDDLE
        "b" -> SharedPptxVerticalAnchor.BOTTOM
        else -> SharedPptxVerticalAnchor.TOP
    }
}

private fun Element.tableCellLineColor(theme: SharedPptxTheme): Int? {
    val line = children().firstNotNullOfOrNull { child ->
        when (child.localTag()) {
            "lnl", "lnr", "lnt", "lnb", "ln" -> child
            "left", "right", "top", "bottom", "insideh", "insidev" -> child.firstDirectByLocalTag("ln")
            else -> null
        }
    }
    return line?.solidFillColor(theme)
}

private fun Element.tableStylePart(theme: SharedPptxTheme): SharedPptxTableCellStyle {
    val tcStyle = childrenByLocalTag("tcStyle").firstOrNull()
    return SharedPptxTableCellStyle(
        fillColor = tcStyle?.firstDirectByLocalTag("fill")?.solidFillColor(theme),
        lineColor = tcStyle?.firstDirectByLocalTag("tcBdr")?.tableCellLineColor(theme),
        run = childrenByLocalTag("tcTxStyle").firstOrNull()?.tableTextRunStyle(theme) ?: SharedPptxRunStyle()
    )
}

private fun Element.tableTextRunStyle(theme: SharedPptxTheme): SharedPptxRunStyle {
    return SharedPptxRunStyle(
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

private fun Element.autoFitMode(): SharedPptxAutoFitMode {
    return when {
        firstDirectByLocalTag("normAutofit") != null -> SharedPptxAutoFitMode.NORMAL
        firstDirectByLocalTag("spAutoFit") != null -> SharedPptxAutoFitMode.SHAPE
        else -> SharedPptxAutoFitMode.NONE
    }
}

private fun Element.typefaceName(theme: SharedPptxTheme): String? {
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

private fun Element.boundsFromTransform(): SharedPptxRect {
    val xfrm = childrenByLocalTag("xfrm").firstOrNull() ?: firstByLocalTag("xfrm")
    val off = xfrm?.childrenByLocalTag("off")?.firstOrNull()
    val ext = xfrm?.childrenByLocalTag("ext")?.firstOrNull()
    val x = off?.xmlFloat("x")?.emuToPoint() ?: 0f
    val y = off?.xmlFloat("y")?.emuToPoint() ?: 0f
    val cx = ext?.xmlFloat("cx")?.emuToPoint() ?: 0f
    val cy = ext?.xmlFloat("cy")?.emuToPoint() ?: 0f
    return SharedPptxRect(x, y, x + cx, y + cy)
}

private fun Float.emuToPoint(): Float = this / EMU_PER_POINT

private fun Float.emuToPointInt(): Int = emuToPoint().roundToInt().coerceAtLeast(1)

private fun Int?.orZero(): Int = this ?: 0

private fun Element.rotationDegreesFromTransform(): Float {
    val xfrm = childrenByLocalTag("xfrm").firstOrNull() ?: firstByLocalTag("xfrm")
    return xfrm?.xmlFloat("rot")?.let { it / 60_000f } ?: 0f
}

private fun Element.solidFillColor(theme: SharedPptxTheme): Int? {
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

private fun Element.schemeColor(theme: SharedPptxTheme): Int? {
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
    return SharedPptxColor.argb(
        (SharedPptxColor.alpha(this) * alpha).roundToInt().coerceIn(0, 255),
        channel(SharedPptxColor.red(this)),
        channel(SharedPptxColor.green(this)),
        channel(SharedPptxColor.blue(this))
    )
}

private fun String.toColorOrNull(): Int? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6) return null
    return runCatching { SharedPptxColor.rgb(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16)) }.getOrNull()
}

private fun String.presetColorOrNull(): Int? {
    return when (lowercase(Locale.ROOT)) {
        "black" -> SharedPptxColor.BLACK
        "white" -> SharedPptxColor.WHITE
        "red" -> SharedPptxColor.RED
        "green" -> SharedPptxColor.GREEN
        "blue" -> SharedPptxColor.BLUE
        "yellow" -> SharedPptxColor.YELLOW
        "cyan" -> SharedPptxColor.CYAN
        "magenta" -> SharedPptxColor.MAGENTA
        "gray", "grey" -> SharedPptxColor.GRAY
        "dkgray", "dkgrey" -> SharedPptxColor.DKGRAY
        "ltgray", "ltgrey" -> SharedPptxColor.LTGRAY
        "orange" -> SharedPptxColor.rgb(255, 165, 0)
        "purple" -> SharedPptxColor.rgb(128, 0, 128)
        "brown" -> SharedPptxColor.rgb(165, 42, 42)
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

private fun Element.placeholderKey(): SharedPptxPlaceholderKey {
    return SharedPptxPlaceholderKey(
        type = xmlAttr("type")?.lowercase(Locale.ROOT),
        index = xmlAttr("idx")
    )
}

private fun SharedPptxPlaceholderKey.matches(other: SharedPptxPlaceholderKey): Boolean {
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
