package com.aryan.reader.shared.pptx

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val SHARED_PPTX_PARSER_VERSION = 7
private const val DEFAULT_TEXT_MARGIN_PT = 91_440f / SharedPptxOoXml.EMU_PER_POINT
private const val DEFAULT_LINE_SPACING_MULTIPLE = 1.0f

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
        return when (key?.type?.let(SharedPptxOoXml::placeholderFamily)) {
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
            val childWidth = chExt.xmlFloat("cx")?.let(SharedPptxOoXml::emuToPoint)?.takeIf { it != 0f } ?: return IDENTITY
            val childHeight = chExt.xmlFloat("cy")?.let(SharedPptxOoXml::emuToPoint)?.takeIf { it != 0f } ?: return IDENTITY
            val scaleX = (ext?.xmlFloat("cx")?.let(SharedPptxOoXml::emuToPoint) ?: childWidth) / childWidth
            val scaleY = (ext?.xmlFloat("cy")?.let(SharedPptxOoXml::emuToPoint) ?: childHeight) / childHeight
            val childX = chOff?.xmlFloat("x")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
            val childY = chOff?.xmlFloat("y")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
            val offX = off?.xmlFloat("x")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
            val offY = off?.xmlFloat("y")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
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
    private const val MAX_TOTAL_IMAGE_BYTES = 64L * 1024L * 1024L

    private data class Entry(
        val deck: SharedPptxDeck,
        val length: Long,
        val lastModified: Long,
        val imageBytes: Long,
    )

    private val cache = LinkedHashMap<String, Entry>()
    private val fastIndex = mutableMapOf<String, String>()

    fun load(file: File): SharedPptxDeck {
        val length = file.length()
        val lastModified = file.lastModified()
        val identity = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        // Cheap stat-only fast path: avoids re-hashing large decks on every open.
        val fastKey = "$identity:$length:$lastModified:$SHARED_PPTX_PARSER_VERSION"
        synchronized(cache) {
            fastIndex[fastKey]?.let { key ->
                cache[key]?.let { entry ->
                    if (entry.length == length && entry.lastModified == lastModified) {
                        cache.remove(key)
                        cache[key] = entry
                        return entry.deck
                    }
                }
            }
        }
        val key = "${file.contentHash()}:$length:$SHARED_PPTX_PARSER_VERSION"
        synchronized(cache) {
            cache[key]?.let { entry ->
                cache.remove(key)
                cache[key] = entry
                fastIndex[fastKey] = key
                return entry.deck
            }
        }
        val parsed = SharedPptxDocumentParser.parse(file)
        synchronized(cache) {
            val imageBytes = parsed.slides.sumOf { slide ->
                slide.elements.sumOf { element ->
                    (element as? SharedPptxImageElement)?.bytes?.size?.toLong() ?: 0L
                }
            }
            cache[key] = Entry(parsed, length, lastModified, imageBytes)
            fastIndex[fastKey] = key
            while (cache.size > MAX_ENTRIES || cache.values.sumOf { it.imageBytes } > MAX_TOTAL_IMAGE_BYTES) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
                fastIndex.entries.removeAll { it.value == oldest }
            }
        }
        return parsed
    }
}

object SharedPptxDocumentParser {
    fun parse(file: File): SharedPptxDeck {
        ZipFile(file).use { zip ->
            val caches = ParseCaches(zip)
            val presentation = caches.xml("ppt/presentation.xml")
                ?: error("ppt/presentation.xml not found in SharedPptx archive.")
            val presentationRels = caches.relationshipsFor("ppt/presentation.xml")
            val width = (presentation.firstByLocalTag("sldSz")?.xmlFloat("cx") ?: SharedPptxOoXml.DEFAULT_SLIDE_WIDTH_EMU.toFloat()).let(SharedPptxOoXml::emuToPointInt)
            val height = (presentation.firstByLocalTag("sldSz")?.xmlFloat("cy") ?: SharedPptxOoXml.DEFAULT_SLIDE_HEIGHT_EMU.toFloat()).let(SharedPptxOoXml::emuToPointInt)
            val slidePaths = presentation.allByLocalTag("sldId")
                .mapNotNull { slideId -> slideId.xmlAttr("r:id") }
                .mapNotNull { relId -> presentationRels.byId[relId]?.resolvedTarget }
                .ifEmpty {
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter(SharedPptxOoXml::isSlidePart)
                        .sortedWith(SharedPptxOoXml.slidePathComparator)
                        .toList()
                }

            val slides = slidePaths.mapNotNull { slidePath ->
                runCatching { parseSlide(caches, presentation, slidePath, width, height) }
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
        caches: ParseCaches,
        presentation: Element,
        slidePath: String,
        width: Int,
        height: Int
    ): SharedPptxSlide {
        val slideXml = caches.xml(slidePath) ?: error("Missing slide part: $slidePath")
        val slideRels = caches.relationshipsFor(slidePath)
        val layoutPath = slideRels.byId.values
            .firstOrNull { it.type.endsWith("/slideLayout", ignoreCase = true) }
            ?.resolvedTarget
        val layoutRels = layoutPath?.let { caches.relationshipsFor(it) }
        val masterPath = layoutRels?.byId?.values
            ?.firstOrNull { it.type.endsWith("/slideMaster", ignoreCase = true) }
            ?.resolvedTarget
        val masterRels = masterPath?.let { caches.relationshipsFor(it) }
        val themePath = masterRels?.byId?.values
            ?.firstOrNull { it.type.endsWith("/theme", ignoreCase = true) }
            ?.resolvedTarget
        val theme = themePath?.let { caches.theme(it) } ?: SharedPptxTheme()
        val presentationDefaults = presentation.presentationTextDefaults(theme)

        val master = masterPath?.let { path ->
            caches.xml(path)?.let {
                parsePart(
                    caches = caches,
                    document = it,
                    relationships = caches.relationshipsFor(path),
                    theme = theme,
                    renderPlaceholderText = false,
                    inheritedTextDefaults = presentationDefaults
                )
            }
        } ?: ParsedPart()
        val layout = layoutPath?.let { path ->
            caches.xml(path)?.let {
                parsePart(
                    caches = caches,
                    document = it,
                    relationships = caches.relationshipsFor(path),
                    theme = theme,
                    renderPlaceholderText = false,
                    inheritedTextDefaults = master.textDefaults
                )
            }
        } ?: ParsedPart()
        val slide = parsePart(
            caches = caches,
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
        caches: ParseCaches,
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
        // Table-style XML is deck-global: inflate/parse once per open, then resolve
        // per part-theme (color maps differ between master/layout/slide).
        val tableStyles = caches.tableStyles(partTheme)
        val elements = mutableListOf<SharedPptxElement>()
        val tree = document.firstByLocalTag("spTree") ?: document
        tree.children().forEach { child ->
            parseDrawingElement(
                caches = caches,
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
        caches: ParseCaches,
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
            "pic" -> parseImage(caches, element, relationships)
                ?.let { output += transform.apply(it) }
            "grpsp" -> element.children().forEach { child ->
                val childTransform = transform.then(SharedPptxGroupTransform.fromGroup(element))
                parseDrawingElement(
                    caches = caches,
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
        val useBackgroundFill = SharedPptxOoXml.isTruthyFlag(element.xmlAttr("useBgFill"))
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
        val lineWidth = line?.xmlFloat("w")?.let(SharedPptxOoXml::emuToPoint) ?: 0.75f
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
        caches: ParseCaches,
        element: Element,
        relationships: SharedPptxRelationships
    ): SharedPptxImageElement? {
        val blip = element.firstByLocalTag("blip") ?: return null
        val relId = blip.xmlAttr("r:embed") ?: blip.xmlAttr("r:link") ?: return null
        val rel = relationships.byId[relId] ?: return null
        val target = rel.resolvedTarget
        val bytes = caches.imageBytes(target) ?: return null
        val crop = element.firstByLocalTag("srcRect")?.imageCrop() ?: SharedPptxImageCrop()
        val bounds = element.childrenByLocalTag("spPr").firstOrNull()?.boundsFromTransform()
            ?: element.boundsFromTransform()
        return SharedPptxImageElement(
            bounds = bounds,
            bytes = bytes,
            contentType = SharedPptxOoXml.imageContentTypeForTarget(target),
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
            firstRow = tblPr?.xmlAttr("firstRow").let(SharedPptxOoXml::isTruthyFlag),
            lastRow = tblPr?.xmlAttr("lastRow").let(SharedPptxOoXml::isTruthyFlag),
            firstColumn = tblPr?.xmlAttr("firstCol").let(SharedPptxOoXml::isTruthyFlag),
            lastColumn = tblPr?.xmlAttr("lastCol").let(SharedPptxOoXml::isTruthyFlag),
            bandRow = tblPr?.xmlAttr("bandRow").let(SharedPptxOoXml::isTruthyFlag)
        )
        val gridWidths = table.firstByLocalTag("tblGrid")
            ?.childrenByLocalTag("gridCol")
            ?.map { it.xmlFloat("w")?.let(SharedPptxOoXml::emuToPoint) }
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
                heightPoint = row.xmlFloat("h")?.let(SharedPptxOoXml::emuToPoint),
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
        val numberCounters = IntArray(SharedPptxOoXml.MAX_NUMBERING_LEVELS)
        val numberCounterStarted = BooleanArray(SharedPptxOoXml.MAX_NUMBERING_LEVELS)
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
            val value = colorNode.firstByLocalTag("srgbClr")?.xmlAttr("val")?.let(SharedPptxOoXml::hexColorOrNull)
                ?: colorNode.firstByLocalTag("sysClr")?.xmlAttr("lastClr")?.let(SharedPptxOoXml::hexColorOrNull)
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
        val relsPath = SharedPptxOoXml.relationshipsPathFor(partPath)
        val document = xml(relsPath) ?: return SharedPptxRelationships(emptyMap())
        val rels = document.allByLocalTag("Relationship").mapNotNull { rel ->
            val id = rel.xmlAttr("Id") ?: return@mapNotNull null
            val target = rel.xmlAttr("Target") ?: return@mapNotNull null
            val type = rel.xmlAttr("Type").orEmpty()
            SharedPptxRelationship(
                id = id,
                target = target,
                resolvedTarget = SharedPptxOoXml.resolveRelationshipTarget(partPath, target, rel.xmlAttr("TargetMode")),
                type = type,
                targetMode = rel.xmlAttr("TargetMode")
            )
        }.associateBy { it.id }
        return SharedPptxRelationships(rels)
    }

    /**
     * Per-open caches: master/layout/theme/rels/table-style parts are shared across
     * slides, so inflate + parse each backing entry once instead of O(slides) times.
     */
    private class ParseCaches(private val zip: ZipFile) {
        private val xml = mutableMapOf<String, Element?>()
        private val rels = mutableMapOf<String, SharedPptxRelationships>()
        private val themes = mutableMapOf<String, SharedPptxTheme>()
        private var tableStylesDocument: Element? = null
        private var tableStylesLoaded = false
        private val tableStylesByTheme = mutableMapOf<SharedPptxTheme, Map<String, SharedPptxTableStyle>>()

        fun xml(path: String): Element? = xml.getOrPut(path) { zip.xml(path) }

        fun relationshipsFor(partPath: String): SharedPptxRelationships =
            rels.getOrPut(partPath) { zip.relationshipsFor(partPath) }

        fun theme(path: String): SharedPptxTheme =
            themes.getOrPut(path) { xml(path)?.let { parseTheme(it) } ?: SharedPptxTheme() }

        fun imageBytes(target: String): ByteArray? {
            val entry = zip.getEntry(target) ?: return null
            return zip.getInputStream(entry).use { it.readBytes() }
        }

        fun tableStyles(theme: SharedPptxTheme): Map<String, SharedPptxTableStyle> =
            tableStylesByTheme.getOrPut(theme) {
                if (!tableStylesLoaded) {
                    tableStylesDocument = zip.xml("ppt/tableStyles.xml")
                    tableStylesLoaded = true
                }
                resolveTableStyles(tableStylesDocument, theme)
            }
    }

    private fun resolveTableStyles(
        document: Element?,
        theme: SharedPptxTheme
    ): Map<String, SharedPptxTableStyle> {
        if (document == null) return emptyMap()
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
        else -> firstDirectByLocalTag("buChar")?.xmlAttr("char")?.let { SharedPptxOoXml.normalizeBulletGlyph(it, bulletTypeface) }
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
        marginLeftPt = xmlFloat("marL")?.let(SharedPptxOoXml::emuToPoint),
        indentPt = xmlFloat("indent")?.let(SharedPptxOoXml::emuToPoint),
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
        bold = xmlAttr("b")?.let(SharedPptxOoXml::isTruthyFlag),
        italic = xmlAttr("i")?.let(SharedPptxOoXml::isTruthyFlag),
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
        val index = level.coerceIn(0, SharedPptxOoXml.MAX_NUMBERING_LEVELS - 1)
        if (!counterStarted[index]) {
            counters[index] = (autoNumberStartAt ?: 1).coerceAtLeast(1)
            counterStarted[index] = true
        } else {
            counters[index] += 1
        }
        for (resetIndex in index + 1 until SharedPptxOoXml.MAX_NUMBERING_LEVELS) {
            counterStarted[resetIndex] = false
            counters[resetIndex] = 0
        }
        return SharedPptxOoXml.formatAutoNumber(counters[index], numberType)
    }
    return bullet
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
        return color.xmlAttr("val")?.let(SharedPptxOoXml::hexColorOrNull)?.applyLuminance(color)
    }
    firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.color(scheme)?.applyLuminance(color)
    }
    firstByLocalTag("prstClr")?.let { color ->
        return color.xmlAttr("val")?.let(SharedPptxOoXml::presetColor)?.applyLuminance(color)
    }
    firstByLocalTag("sysClr")?.let { color ->
        return color.xmlAttr("lastClr")?.let(SharedPptxOoXml::hexColorOrNull)?.applyLuminance(color)
    }
    return null
}

private fun Element.textInsets(): SharedPptxTextInsets {
    return SharedPptxTextInsets(
        left = (xmlFloat("lIns") ?: xmlFloat("marL"))?.let(SharedPptxOoXml::emuToPoint) ?: DEFAULT_TEXT_MARGIN_PT,
        top = (xmlFloat("tIns") ?: xmlFloat("marT"))?.let(SharedPptxOoXml::emuToPoint) ?: DEFAULT_TEXT_MARGIN_PT,
        right = (xmlFloat("rIns") ?: xmlFloat("marR"))?.let(SharedPptxOoXml::emuToPoint) ?: DEFAULT_TEXT_MARGIN_PT,
        bottom = (xmlFloat("bIns") ?: xmlFloat("marB"))?.let(SharedPptxOoXml::emuToPoint) ?: DEFAULT_TEXT_MARGIN_PT
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
        bold = xmlAttr("b")?.let(SharedPptxOoXml::isTruthyFlag),
        italic = xmlAttr("i")?.let(SharedPptxOoXml::isTruthyFlag),
        typeface = typefaceName(theme)
    )
}

private fun Element.spacingPoints(): Float? {
    firstByLocalTag("spcPts")?.xmlFloat("val")?.let { return it / 100f }
    firstByLocalTag("spcPct")?.xmlFloat("val")?.let { return SharedPptxOoXml.DEFAULT_TEXT_SIZE_PT * (it / 100_000f) }
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
    val x = off?.xmlFloat("x")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
    val y = off?.xmlFloat("y")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
    val cx = ext?.xmlFloat("cx")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
    val cy = ext?.xmlFloat("cy")?.let(SharedPptxOoXml::emuToPoint) ?: 0f
    return SharedPptxRect(x, y, x + cx, y + cy)
}

private fun Int?.orZero(): Int = this ?: 0

private fun Element.rotationDegreesFromTransform(): Float {
    val xfrm = childrenByLocalTag("xfrm").firstOrNull() ?: firstByLocalTag("xfrm")
    return xfrm?.xmlFloat("rot")?.let { it / 60_000f } ?: 0f
}

private fun Element.solidFillColor(theme: SharedPptxTheme): Int? {
    val solid = childrenByLocalTag("solidFill").firstOrNull() ?: return null
    solid.firstByLocalTag("srgbClr")?.let { color ->
        return color.xmlAttr("val")?.let(SharedPptxOoXml::hexColorOrNull)?.applyLuminance(color)
    }
    solid.firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.color(scheme)?.applyLuminance(color)
    }
    solid.firstByLocalTag("prstClr")?.let { color ->
        return color.xmlAttr("val")?.let(SharedPptxOoXml::presetColor)?.applyLuminance(color)
    }
    solid.firstByLocalTag("sysClr")?.let { color ->
        return color.xmlAttr("lastClr")?.let(SharedPptxOoXml::hexColorOrNull)?.applyLuminance(color)
    }
    return null
}

private fun Element.schemeColor(theme: SharedPptxTheme): Int? {
    firstByLocalTag("schemeClr")?.let { color ->
        val scheme = color.xmlAttr("val") ?: return null
        return theme.color(scheme)?.applyLuminance(color)
    }
    firstByLocalTag("prstClr")?.let { color ->
        return color.xmlAttr("val")?.let(SharedPptxOoXml::presetColor)?.applyLuminance(color)
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
    if (type != null && other.type != null && SharedPptxOoXml.placeholderFamily(type) == SharedPptxOoXml.placeholderFamily(other.type)) return true
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
