@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.pptx.SharedPptxAutoFitMode
import com.aryan.reader.shared.pptx.SharedPptxCharBox
import com.aryan.reader.shared.pptx.SharedPptxColor
import com.aryan.reader.shared.pptx.SharedPptxCustomGeometry
import com.aryan.reader.shared.pptx.SharedPptxDeck
import com.aryan.reader.shared.pptx.SharedPptxElement
import com.aryan.reader.shared.pptx.SharedPptxGradientFill
import com.aryan.reader.shared.pptx.SharedPptxImageCrop
import com.aryan.reader.shared.pptx.SharedPptxImageElement
import com.aryan.reader.shared.pptx.SharedPptxParagraph
import com.aryan.reader.shared.pptx.SharedPptxPathCommand
import com.aryan.reader.shared.pptx.SharedPptxPlaceholderKey
import com.aryan.reader.shared.pptx.SharedPptxRect
import com.aryan.reader.shared.pptx.SharedPptxShapeElement
import com.aryan.reader.shared.pptx.SharedPptxSlide
import com.aryan.reader.shared.pptx.SharedPptxTableCell
import com.aryan.reader.shared.pptx.SharedPptxTableElement
import com.aryan.reader.shared.pptx.SharedPptxTableRow
import com.aryan.reader.shared.pptx.SharedPptxTextAlign
import com.aryan.reader.shared.pptx.SharedPptxTextInsets
import com.aryan.reader.shared.pptx.SharedPptxTextRun
import com.aryan.reader.shared.pptx.SharedPptxVerticalAnchor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val EMU_PER_POINT = 12_700f
private const val DEFAULT_SLIDE_WIDTH_EMU = 12_192_000
private const val DEFAULT_SLIDE_HEIGHT_EMU = 6_858_000
private const val DEFAULT_TEXT_SIZE_PT = 18f

/** iOS PPTX loading entry point. The resulting chapters are intentionally one slide each. */
internal fun loadIosPptxBook(book: BookItem): SharedEpubBook {
    val path = book.path.resolveIosEpubSourcePath() ?: error("PPTX path is unavailable")
    val startedAt = currentTimestamp()
    val archive = IosZipEpubArchive(path)
    var slideCount = 0
    var elementCount = 0
    var imageBytes = 0L
    var peakSlideImageBytes = 0L
    val chapters = buildList {
        IosPptxParser(archive).forEachSlide { index, slide ->
            slideCount++
            elementCount += slide.elements.size
            val slideImageBytes = slide.elements
                .asSequence()
                .filterIsInstance<SharedPptxImageElement>()
                .sumOf { it.bytes.size.toLong() }
            imageBytes += slideImageBytes
            peakSlideImageBytes = max(peakSlideImageBytes, slideImageBytes)
            add(
                SharedEpubChapter(
                    id = "${book.id}-slide-${index + 1}",
                    title = "Slide ${index + 1}",
                    plainText = slide.text,
                    htmlContent = IosPptxHtmlRenderer.render(slide, index + 1),
                )
            )
        }
    }
    iosEpubLoadLog {
        "PPTX visual loader finished id=${book.id} slides=$slideCount " +
            "elements=$elementCount imageBytes=$imageBytes peakSlideImageBytes=$peakSlideImageBytes " +
            "elapsed=${currentTimestamp() - startedAt}ms"
    }
    val title = book.title?.takeIf { it.isNotBlank() }
        ?: book.displayName.substringBeforeLast('.').ifBlank { book.displayName }
    return SharedEpubBook(
        id = book.id,
        fileName = book.displayName,
        title = title,
        author = book.author,
        chapters = chapters,
    )
}

internal class IosPptxParser(private val archive: IosZipEpubArchive) {
    fun parse(): SharedPptxDeck {
        val slides = mutableListOf<SharedPptxSlide>()
        val summary = forEachSlide { _, slide -> slides += slide }
        return SharedPptxDeck(
            widthPoint = summary.width,
            heightPoint = summary.height,
            slides = slides,
        )
    }

    /**
     * Parses one slide at a time. The old deck-first path retained every embedded image byte
     * array while also creating base64 HTML for every slide. Streaming the callback keeps only
     * the current slide's decoded image bytes alive during rendering, which materially lowers
     * peak memory for image-heavy presentations while preserving the same slide model.
     */
    fun forEachSlide(onSlide: (index: Int, slide: SharedPptxSlide) -> Unit): IosPptxParseSummary {
        val presentationPath = "ppt/presentation.xml"
        val presentation = archive.readText(presentationPath)?.let(IosPptxXml::parse)
            ?: error("ppt/presentation.xml not found in PPTX archive")
        val width = (presentation.first("sldSz")?.floatAttr("cx") ?: DEFAULT_SLIDE_WIDTH_EMU.toFloat())
            .emuToPointInt()
        val height = (presentation.first("sldSz")?.floatAttr("cy") ?: DEFAULT_SLIDE_HEIGHT_EMU.toFloat())
            .emuToPointInt()
        val presentationRelationships = relationshipsFor(presentationPath)
        val slidePaths = presentation.descendants("sldId")
            .mapNotNull { it.attr("r:id") ?: it.attr("id") }
            .mapNotNull { presentationRelationships[it]?.resolvedTarget }
            .ifEmpty {
                archive.entryPaths
                    .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml", RegexOption.IGNORE_CASE)) }
                    .sortedWith(naturalPathComparator)
            }
        var slideCount = 0
        slidePaths.forEach { path ->
            runCatching { parseSlide(presentation, path, width, height) }
                .onFailure { error -> iosEpubLoadLog { "PPTX slide parse failed path=$path error=${error.message}" } }
                .getOrNull()
                ?.let { slide ->
                    onSlide(slideCount++, slide)
                }
        }
        if (slideCount == 0) {
            onSlide(0, SharedPptxSlide(width, height, SharedPptxColor.WHITE, emptyList(), "", emptyList()))
            slideCount = 1
        }
        return IosPptxParseSummary(width, height, slideCount)
    }

    private fun parseSlide(
        presentation: IosPptxXml,
        slidePath: String,
        width: Int,
        height: Int,
    ): SharedPptxSlide {
        val slide = archive.readText(slidePath)?.let(IosPptxXml::parse)
            ?: error("Missing slide part: $slidePath")
        val slideRels = relationshipsFor(slidePath)
        val layoutPath = slideRels.values.firstOrNull { it.type.endsWith("/slideLayout", true) }?.resolvedTarget
        val layoutRels = layoutPath?.let(::relationshipsFor)
        val masterPath = layoutRels?.values?.firstOrNull { it.type.endsWith("/slideMaster", true) }?.resolvedTarget
        val masterRels = masterPath?.let(::relationshipsFor)
        val themePath = masterRels?.values?.firstOrNull { it.type.endsWith("/theme", true) }?.resolvedTarget
        val theme = themePath?.let { archive.readText(it)?.let(IosPptxXml::parse)?.let(::parseTheme) }
            ?: IosPptxTheme()
        val tableStyles = parseTableStyles(theme)

        val master = masterPath?.let { path ->
            archive.readText(path)?.let(IosPptxXml::parse)?.let {
                parsePart(it, relationshipsFor(path), theme, renderPlaceholderText = false)
            }
        } ?: ParsedIosPart()
        val layout = layoutPath?.let { path ->
            archive.readText(path)?.let(IosPptxXml::parse)?.let {
                parsePart(it, relationshipsFor(path), theme, renderPlaceholderText = false)
            }
        } ?: ParsedIosPart()
        val current = parsePart(slide, slideRels, theme, renderPlaceholderText = true, tableStyles = tableStyles)
        val inheritedElements = master.elements + layout.elements
        val elements = inheritedElements + current.elements.map { element ->
            val shape = element as? SharedPptxShapeElement
            val placeholder = shape?.placeholderKey
            if (shape == null || placeholder == null) {
                element
            } else {
                val inherited = inheritedElements.asSequence()
                    .filterIsInstance<SharedPptxShapeElement>()
                    .lastOrNull { it.placeholderKey == placeholder }
                shape.mergeInherited(inherited)
            }
        }
        val background = current.backgroundColor ?: layout.backgroundColor ?: master.backgroundColor ?: SharedPptxColor.WHITE
        val text = buildString {
            elements.forEachIndexed { index, element ->
                if (index > 0 && isNotEmpty()) append('\n')
                when (element) {
                    is SharedPptxShapeElement -> if (element.renderText) element.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                        if (paragraphIndex > 0) append('\n')
                        if (paragraph.bullet != null) append(paragraph.bullet).append(' ')
                        paragraph.runs.forEach { append(it.text) }
                    }
                    is SharedPptxTableElement -> element.rows.forEach { row ->
                        if (isNotEmpty()) append('\n')
                        row.cells.forEachIndexed { cellIndex, cell ->
                            if (cellIndex > 0) append('\t')
                            cell.paragraphs.forEach { paragraph -> paragraph.runs.forEach { append(it.text) } }
                        }
                    }
                    is SharedPptxImageElement -> Unit
                }
            }
        }.trim()
        val boxes = buildIosCharBoxes(elements)
        return SharedPptxSlide(width, height, background, elements, text, boxes)
    }

    private fun parsePart(
        document: IosPptxXml,
        relationships: Map<String, IosPptxRelationship>,
        theme: IosPptxTheme,
        renderPlaceholderText: Boolean,
        tableStyles: Map<String, IosPptxTableStyle> = emptyMap(),
    ): ParsedIosPart {
        val root = document.descendant("spTree") ?: document
        val elements = mutableListOf<SharedPptxElement>()
        root.children.forEach { child ->
            parseDrawingElement(child, relationships, theme, renderPlaceholderText, elements, tableStyles = tableStyles)
        }
        val background = document.descendant("bgPr")?.let { solidColor(it, theme) }
            ?: document.descendant("bgRef")?.let { solidColor(it, theme) }
        return ParsedIosPart(background, elements)
    }

    private fun parseDrawingElement(
        element: IosPptxXml,
        relationships: Map<String, IosPptxRelationship>,
        theme: IosPptxTheme,
        renderPlaceholderText: Boolean,
        output: MutableList<SharedPptxElement>,
        transform: IosPptxTransform = IosPptxTransform.IDENTITY,
        tableStyles: Map<String, IosPptxTableStyle> = emptyMap(),
    ) {
        when (element.localName) {
            "sp", "cxnSp" -> parseShape(element, relationships, theme, renderPlaceholderText)?.let { output += transform.apply(it) }
            "pic" -> parseImage(element, relationships)?.let { output += transform.apply(it) }
            "grpSp" -> element.children.forEach { child ->
                parseDrawingElement(
                    child,
                    relationships,
                    theme,
                    renderPlaceholderText,
                    output,
                    transform.then(element.descendant("xfrm")?.let(IosPptxTransform::fromXml) ?: IosPptxTransform.IDENTITY),
                    tableStyles,
                )
            }
            "graphicFrame" -> parseGraphicFrame(element, relationships, theme, tableStyles)?.let { output += transform.apply(it) }
        }
    }

    private fun parseShape(
        element: IosPptxXml,
        relationships: Map<String, IosPptxRelationship>,
        theme: IosPptxTheme,
        renderPlaceholderText: Boolean,
    ): SharedPptxShapeElement? {
        val spPr = element.first("spPr")
        val placeholder = element.descendant("ph")?.let { SharedPptxPlaceholderKey(it.attr("type"), it.attr("idx")) }
        val bounds = spPr?.first("xfrm")?.rectFromTransform()
            ?: element.first("xfrm")?.rectFromTransform()
            ?: placeholder?.let { SharedPptxRect(0f, 0f, 0f, 0f) }
            ?: return null
        val body = element.first("txBody")
        val bodyPr = body?.first("bodyPr")
        val preset = spPr?.first("prstGeom")?.attr("prst")
            ?: if (element.localName.equals("cxnSp", true)) "line" else "rect"
        val line = spPr?.first("ln")
        val style = element.first("style")
        val usesBackgroundFill = element.attr("useBgFill")?.let { it == "1" || it.equals("true", true) } == true
        val fill = when {
            usesBackgroundFill -> null
            spPr?.first("noFill") != null -> null
            else -> spPr?.first("solidFill")?.let { solidColor(it, theme) }
                ?: style?.first("fillRef")?.let { solidColor(it, theme) }
        }
        val gradient = spPr?.first("gradFill")?.let { gradientColor(it, theme) }
        val lineColor = when {
            line?.first("noFill") != null -> null
            else -> line?.first("solidFill")?.let { solidColor(it, theme) }
                ?: style?.first("lnRef")?.let { solidColor(it, theme) }
        }
        val hyperlink = element.descendant("hlinkClick")?.attr("r:id")?.let { relationships[it] }
            ?.let { if (it.targetMode.equals("External", true)) it.target else it.resolvedTarget }
        val paragraphs = parseTextBody(body, theme)
        if (bounds.width() <= 0f && bounds.height() <= 0f && paragraphs.isEmpty()) return null
        return SharedPptxShapeElement(
            bounds = bounds,
            preset = preset.lowercase(),
            fillColor = fill,
            gradientFill = gradient,
            lineColor = lineColor,
            lineWidthPoint = line?.floatAttr("w")?.emuToPoint()
                ?: style?.first("lnRef")?.floatAttr("w")?.emuToPoint()
                ?: 0.75f,
            paragraphs = paragraphs,
            hyperlink = hyperlink,
            placeholderKey = placeholder,
            textInsets = bodyPr?.textInsets() ?: SharedPptxTextInsets(),
            verticalAnchor = bodyPr?.verticalAnchor() ?: SharedPptxVerticalAnchor.TOP,
            rotationDegrees = spPr?.first("xfrm")?.floatAttr("rot")?.div(60_000f) ?: 0f,
            renderText = placeholder == null || renderPlaceholderText,
            fontScale = bodyPr?.first("normAutofit")?.floatAttr("fontScale")?.div(100_000f)?.coerceIn(0.4f, 2f) ?: 1f,
            lineSpacingReduction = bodyPr?.first("normAutofit")?.floatAttr("lnSpcReduction")?.div(100_000f)?.coerceIn(0f, 0.5f) ?: 0f,
            autoFitMode = when {
                bodyPr?.first("normAutofit") != null -> SharedPptxAutoFitMode.NORMAL
                bodyPr?.first("spAutoFit") != null -> SharedPptxAutoFitMode.SHAPE
                else -> SharedPptxAutoFitMode.NONE
            },
            customGeometry = spPr?.first("custGeom")?.let(::parseCustomGeometry),
        )
    }

    private fun parseImage(
        element: IosPptxXml,
        relationships: Map<String, IosPptxRelationship>,
    ): SharedPptxImageElement? {
        val blip = element.descendant("blip") ?: return null
        val rel = (blip.attr("r:embed") ?: blip.attr("r:link"))?.let(relationships::get) ?: return null
        val bytes = archive.readBytes(rel.resolvedTarget) ?: return null
        val spPr = element.first("spPr")
        return SharedPptxImageElement(
            bounds = spPr?.first("xfrm")?.rectFromTransform() ?: element.first("xfrm")?.rectFromTransform() ?: return null,
            bytes = bytes,
            contentType = rel.resolvedTarget.substringAfterLast('.', "").lowercase().toImageContentType(),
            crop = element.first("srcRect")?.let {
                SharedPptxImageCrop(
                    left = it.floatAttr("l")?.div(100_000f)?.coerceIn(0f, 1f) ?: 0f,
                    top = it.floatAttr("t")?.div(100_000f)?.coerceIn(0f, 1f) ?: 0f,
                    right = it.floatAttr("r")?.div(100_000f)?.coerceIn(0f, 1f) ?: 0f,
                    bottom = it.floatAttr("b")?.div(100_000f)?.coerceIn(0f, 1f) ?: 0f,
                )
            } ?: SharedPptxImageCrop(),
            rotationDegrees = spPr?.first("xfrm")?.floatAttr("rot")?.div(60_000f) ?: 0f,
            opacity = (
                blip.first("alphaModFix")?.floatAttr("amt")
                    ?: blip.first("alphaMod")?.floatAttr("amt")
                    ?: blip.first("alpha")?.floatAttr("val")
            )?.div(100_000f)?.coerceIn(0f, 1f) ?: 1f,
        )
    }

    private fun parseTable(
        element: IosPptxXml,
        theme: IosPptxTheme,
        tableStyles: Map<String, IosPptxTableStyle>,
    ): SharedPptxTableElement? {
        val table = element.descendant("tbl") ?: return null
        val bounds = element.descendant("xfrm")?.rectFromTransform() ?: return null
        val widths = table.first("tblGrid")?.all("gridCol")?.map { it.floatAttr("w")?.emuToPoint() } ?: emptyList()
        val tableProperties = table.first("tblPr")
        val styleId = tableProperties?.first("tableStyleId")?.textContent()?.trim()
        val style = styleId?.let(tableStyles::get)
        val firstRowStyled = tableProperties?.boolAttr("firstRow") == true
        val lastRowStyled = tableProperties?.boolAttr("lastRow") == true
        val firstColumnStyled = tableProperties?.boolAttr("firstCol") == true
        val lastColumnStyled = tableProperties?.boolAttr("lastCol") == true
        val rows = table.all("tr").mapIndexed { rowIndex, row ->
            SharedPptxTableRow(
                heightPoint = row.floatAttr("h")?.emuToPoint(),
                cells = row.all("tc").mapIndexed { index, cell ->
                    val props = cell.first("tcPr")
                    val isFirstRow = firstRowStyled && rowIndex == 0
                    val isLastRow = lastRowStyled && rowIndex == table.all("tr").lastIndex
                    val isFirstColumn = firstColumnStyled && index == 0
                    val isLastColumn = lastColumnStyled && index == row.all("tc").lastIndex
                    val fallbackStyle = when {
                        isFirstRow -> style?.firstRow
                        isLastRow -> style?.lastRow
                        isFirstColumn -> style?.firstColumn
                        isLastColumn -> style?.lastColumn
                        else -> style?.whole
                    }
                    SharedPptxTableCell(
                        widthPoint = widths.getOrNull(index),
                        fillColor = props?.first("solidFill")?.let { solidColor(it, theme) } ?: fallbackStyle?.fillColor,
                        lineColor = props?.first("ln")?.first("solidFill")?.let { solidColor(it, theme) } ?: fallbackStyle?.lineColor,
                        paragraphs = parseTextBody(cell.first("txBody"), theme),
                        textInsets = props?.textInsets() ?: SharedPptxTextInsets(left = 3.6f, top = 3.6f, right = 3.6f, bottom = 3.6f),
                        verticalAnchor = props?.verticalAnchor() ?: SharedPptxVerticalAnchor.TOP,
                    )
                },
            )
        }
        return rows.takeIf { it.isNotEmpty() }?.let { SharedPptxTableElement(bounds, it, element.first("xfrm")?.floatAttr("rot")?.div(60_000f) ?: 0f) }
    }

    private fun parseGraphicFrame(
        element: IosPptxXml,
        relationships: Map<String, IosPptxRelationship>,
        theme: IosPptxTheme,
        tableStyles: Map<String, IosPptxTableStyle>,
    ): SharedPptxElement? {
        element.descendant("tbl")?.let { return parseTable(element, theme, tableStyles) }
        val bounds = element.descendant("xfrm")?.rectFromTransform() ?: return null
        val chartRel = element.descendant("chart")?.attr("r:id")
        val diagramRel = element.descendant("relIds")?.attr("r:dm")
        val mediaRel = element.descendant("videoFile")?.attr("r:link")
            ?: element.descendant("audioFile")?.attr("r:link")
        val relationId = chartRel ?: diagramRel ?: mediaRel
        val label = when {
            chartRel != null -> "Chart"
            diagramRel != null -> "SmartArt"
            mediaRel != null -> "Media"
            else -> return null
        }
        val target = relationId?.let { relationships[it]?.resolvedTarget }
        return SharedPptxShapeElement(
            bounds = bounds,
            preset = "rect",
            fillColor = SharedPptxColor.rgb(245, 246, 248),
            gradientFill = null,
            lineColor = theme.colors["tx1"] ?: SharedPptxColor.GRAY,
            lineWidthPoint = 0.75f,
            paragraphs = listOf(
                SharedPptxParagraph(
                    runs = listOf(SharedPptxTextRun(target?.let { "$label: ${it.substringAfterLast('/')}" } ?: label)),
                    alignment = SharedPptxTextAlign.CENTER,
                ),
            ),
            hyperlink = null,
            placeholderKey = null,
            textInsets = SharedPptxTextInsets(left = 8f, top = 8f, right = 8f, bottom = 8f),
            verticalAnchor = SharedPptxVerticalAnchor.MIDDLE,
        )
    }

    private fun parseTextBody(body: IosPptxXml?, theme: IosPptxTheme): List<SharedPptxParagraph> {
        if (body == null) return emptyList()
        val listStyle = body.first("lstStyle")
        val defaults = listStyle?.first("defPPr")
        val numberCounters = IntArray(9)
        return body.all("p").mapNotNull { paragraph ->
            val pPr = paragraph.first("pPr")
            val level = (pPr?.intAttr("lvl")?.coerceAtLeast(0) ?: 0).coerceAtMost(numberCounters.lastIndex)
            val levelDefaults = listStyle?.first("lvl${level + 1}pPr") ?: defaults
            val runDefaults = levelDefaults?.first("defRPr") ?: defaults?.first("defRPr")
            val runs = buildList {
                paragraph.children.forEach { child ->
                    when (child.localName) {
                        "r", "fld" -> {
                            val text = child.first("t")?.textContent().orEmpty()
                            if (text.isNotEmpty()) add(textRun(text, child.first("rPr"), theme, runDefaults))
                        }
                        "br" -> add(textRun("\n", child.first("rPr"), theme, runDefaults))
                        "tab" -> add(textRun("\t", child.first("rPr"), theme, runDefaults))
                    }
                }
            }
            if (runs.isEmpty()) return@mapNotNull null
            val align = when (pPr?.attr("algn") ?: levelDefaults?.attr("algn")) {
                "ctr" -> SharedPptxTextAlign.CENTER
                "r" -> SharedPptxTextAlign.END
                else -> SharedPptxTextAlign.START
            }
            val explicitBullet = pPr?.first("buChar")?.attr("char")
                ?: levelDefaults?.first("buChar")?.attr("char")
            val autoNumber = pPr?.first("buAutoNum") ?: levelDefaults?.first("buAutoNum")
            val bullet = explicitBullet ?: autoNumber?.let {
                val counter = numberCounters[level] + 1
                numberCounters[level] = counter
                numberCounters.fill(0, level + 1, numberCounters.size)
                "$counter."
            }
            SharedPptxParagraph(
                runs = runs,
                alignment = align,
                bullet = bullet,
                level = level,
                marginLeftPt = (pPr?.floatAttr("marL") ?: levelDefaults?.floatAttr("marL"))?.emuToPoint(),
                indentPt = (pPr?.floatAttr("indent") ?: levelDefaults?.floatAttr("indent"))?.emuToPoint(),
                spaceBeforePt = pPr?.first("spcBef")?.spacingPoints()
                    ?: levelDefaults?.first("spcBef")?.spacingPoints()
                    ?: 0f,
                spaceAfterPt = pPr?.first("spcAft")?.spacingPoints()
                    ?: levelDefaults?.first("spcAft")?.spacingPoints()
                    ?: 0f,
                lineSpacingMultiple = pPr?.first("lnSpc")?.spacingMultiple()
                    ?: levelDefaults?.first("lnSpc")?.spacingMultiple()
                    ?: 1f,
                alignmentExplicit = pPr?.attr("algn") != null,
                bulletExplicit = pPr?.first("buChar") != null || pPr?.first("buAutoNum") != null,
                spaceBeforeExplicit = pPr?.first("spcBef") != null,
                spaceAfterExplicit = pPr?.first("spcAft") != null,
                lineSpacingExplicit = pPr?.first("lnSpc") != null,
            )
        }
    }

    private fun parseCustomGeometry(node: IosPptxXml): SharedPptxCustomGeometry? {
        val path = node.descendant("path") ?: return null
        val width = path.floatAttr("w") ?: return null
        val height = path.floatAttr("h") ?: return null
        val commands = buildList {
            path.children.forEach { command ->
                when (command.localName) {
                    "moveTo" -> command.first("pt")?.let { add(SharedPptxPathCommand.MoveTo(it.floatAttr("x") ?: 0f, it.floatAttr("y") ?: 0f)) }
                    "lnTo" -> command.first("pt")?.let { add(SharedPptxPathCommand.LineTo(it.floatAttr("x") ?: 0f, it.floatAttr("y") ?: 0f)) }
                    "quadBezTo" -> {
                        val points = command.all("pt")
                        if (points.size >= 2) add(
                            SharedPptxPathCommand.QuadTo(
                                points[0].floatAttr("x") ?: 0f,
                                points[0].floatAttr("y") ?: 0f,
                                points[1].floatAttr("x") ?: 0f,
                                points[1].floatAttr("y") ?: 0f,
                            )
                        )
                    }
                    "cubicBezTo" -> {
                        val points = command.all("pt")
                        if (points.size >= 3) add(
                            SharedPptxPathCommand.CubicTo(
                                points[0].floatAttr("x") ?: 0f,
                                points[0].floatAttr("y") ?: 0f,
                                points[1].floatAttr("x") ?: 0f,
                                points[1].floatAttr("y") ?: 0f,
                                points[2].floatAttr("x") ?: 0f,
                                points[2].floatAttr("y") ?: 0f,
                            )
                        )
                    }
                    "close" -> add(SharedPptxPathCommand.Close)
                }
            }
        }
        return SharedPptxCustomGeometry(width, height, commands)
    }

    private fun textRun(text: String, rPr: IosPptxXml?, theme: IosPptxTheme, fallback: IosPptxXml?): SharedPptxTextRun {
        val size = (rPr?.floatAttr("sz") ?: fallback?.floatAttr("sz"))?.div(100f)
        val colorNode = rPr?.first("solidFill") ?: fallback?.first("solidFill")
        val typeface = rPr?.first("latin")?.attr("typeface")
            ?: rPr?.first("ea")?.attr("typeface")
            ?: rPr?.first("cs")?.attr("typeface")
            ?: fallback?.first("latin")?.attr("typeface")
            ?: fallback?.first("ea")?.attr("typeface")
            ?: fallback?.first("cs")?.attr("typeface")
        return SharedPptxTextRun(
            text = text,
            sizePt = size ?: DEFAULT_TEXT_SIZE_PT,
            color = colorNode?.let { solidColor(it, theme) },
            bold = rPr?.boolAttr("b") ?: fallback?.boolAttr("b") ?: false,
            italic = rPr?.boolAttr("i") ?: fallback?.boolAttr("i") ?: false,
            typeface = typeface?.resolvePptxTypeface(theme),
            baseline = (rPr?.floatAttr("baseline") ?: fallback?.floatAttr("baseline"))?.div(100_000f) ?: 0f,
            sizeExplicit = rPr?.attr("sz") != null,
            colorExplicit = rPr?.first("solidFill") != null,
            boldExplicit = rPr?.attr("b") != null,
            italicExplicit = rPr?.attr("i") != null,
            typefaceExplicit = rPr?.first("latin") != null,
            baselineExplicit = rPr?.attr("baseline") != null,
        )
    }

    private fun relationshipsFor(partPath: String): Map<String, IosPptxRelationship> {
        val relPath = partPath.substringBeforeLast('/', "")
            .let { parent -> if (parent.isBlank()) "_rels/${partPath.substringAfterLast('/')}.rels" else "$parent/_rels/${partPath.substringAfterLast('/')}.rels" }
        val root = archive.readText(relPath)?.let(IosPptxXml::parse) ?: return emptyMap()
        return root.all("Relationship").mapNotNull { node ->
            val id = node.attr("Id") ?: return@mapNotNull null
            val target = node.attr("Target") ?: return@mapNotNull null
            id to IosPptxRelationship(
                target = target,
                resolvedTarget = resolvePath(partPath, target),
                type = node.attr("Type").orEmpty(),
                targetMode = node.attr("TargetMode"),
            )
        }.toMap()
    }

    private fun parseTheme(xml: IosPptxXml): IosPptxTheme {
        val scheme = xml.descendant("clrScheme") ?: return IosPptxTheme()
        val colors = scheme.children.associateNotNull { child ->
            val value = child.first("srgbClr")?.attr("val")?.hexColor()
                ?: child.first("sysClr")?.attr("lastClr")?.hexColor()
                ?: child.first("prstClr")?.attr("val")?.let(::presetColor)
            value?.let { child.localName to it }
        }.toMutableMap().apply {
            this["bg1"] = this["lt1"] ?: this["bg1"] ?: SharedPptxColor.WHITE
            this["tx1"] = this["dk1"] ?: this["tx1"] ?: SharedPptxColor.BLACK
            this["bg2"] = this["lt2"] ?: this["bg2"] ?: SharedPptxColor.WHITE
            this["tx2"] = this["dk2"] ?: this["tx2"] ?: SharedPptxColor.BLACK
        }
        val fonts = xml.descendant("fontScheme")
        return IosPptxTheme(
        colors = colors,
            majorTypeface = fonts?.first("majorFont")?.first("latin")?.attr("typeface"),
            minorTypeface = fonts?.first("minorFont")?.first("latin")?.attr("typeface"),
        )
    }

    private fun parseTableStyles(theme: IosPptxTheme): Map<String, IosPptxTableStyle> {
        val root = archive.readText("ppt/tableStyles.xml")?.let(IosPptxXml::parse) ?: return emptyMap()
        return root.all("tblStyle").mapNotNull { style ->
            val id = style.attr("styleId")?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            fun parsePart(name: String): IosPptxTableCellStyle? {
                val node = style.first(name) ?: return null
                val fill = node.first("tcStyle")?.first("fill")?.first("solidFill")?.let { solidColor(it, theme) }
                val line = node.first("tcStyle")?.first("tcBdr")?.descendant("ln")
                    ?.first("solidFill")?.let { solidColor(it, theme) }
                return IosPptxTableCellStyle(fill, line)
            }
            id to IosPptxTableStyle(
                whole = parsePart("wholeTbl"),
                firstRow = parsePart("firstRow"),
                lastRow = parsePart("lastRow"),
                firstColumn = parsePart("firstCol"),
                lastColumn = parsePart("lastCol"),
            )
        }.toMap()
    }
}

internal data class IosPptxParseSummary(
    val width: Int,
    val height: Int,
    val slideCount: Int,
)

private data class ParsedIosPart(val backgroundColor: Int? = null, val elements: List<SharedPptxElement> = emptyList())

private data class IosPptxTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val rotationDegrees: Float = 0f,
) {
    fun then(child: IosPptxTransform): IosPptxTransform {
        return IosPptxTransform(
            scaleX = scaleX * child.scaleX,
            scaleY = scaleY * child.scaleY,
            dx = dx + child.dx * scaleX,
            dy = dy + child.dy * scaleY,
            rotationDegrees = rotationDegrees + child.rotationDegrees,
        )
    }

    fun apply(element: SharedPptxElement): SharedPptxElement {
        val source = element.bounds
        val mapped = SharedPptxRect(
            left = source.left * scaleX + dx,
            top = source.top * scaleY + dy,
            right = source.right * scaleX + dx,
            bottom = source.bottom * scaleY + dy,
        )
        return when (element) {
            is SharedPptxShapeElement -> element.copy(
                bounds = mapped,
                lineWidthPoint = element.lineWidthPoint * ((abs(scaleX) + abs(scaleY)) / 2f),
                rotationDegrees = element.rotationDegrees + rotationDegrees,
            )
            is SharedPptxImageElement -> element.copy(
                bounds = mapped,
                rotationDegrees = element.rotationDegrees + rotationDegrees,
            )
            is SharedPptxTableElement -> element.copy(
                bounds = mapped,
                rotationDegrees = element.rotationDegrees + rotationDegrees,
            )
        }
    }

    companion object {
        val IDENTITY = IosPptxTransform()

        fun fromXml(xfrm: IosPptxXml): IosPptxTransform {
            val off = xfrm.first("off")
            val ext = xfrm.first("ext")
            val childOff = xfrm.first("chOff")
            val childExt = xfrm.first("chExt")
            val offX = off?.floatAttr("x") ?: 0f
            val offY = off?.floatAttr("y") ?: 0f
            val extX = ext?.floatAttr("cx") ?: 1f
            val extY = ext?.floatAttr("cy") ?: 1f
            val childOffX = childOff?.floatAttr("x") ?: 0f
            val childOffY = childOff?.floatAttr("y") ?: 0f
            val childExtX = childExt?.floatAttr("cx")?.takeIf { it != 0f } ?: extX
            val childExtY = childExt?.floatAttr("cy")?.takeIf { it != 0f } ?: extY
            val sx = extX / childExtX
            val sy = extY / childExtY
            return IosPptxTransform(
                scaleX = sx,
                scaleY = sy,
                dx = (offX - childOffX * sx).emuToPoint(),
                dy = (offY - childOffY * sy).emuToPoint(),
                rotationDegrees = (xfrm.floatAttr("rot") ?: 0f) / 60_000f,
            )
        }
    }
}

private fun SharedPptxShapeElement.mergeInherited(inherited: SharedPptxShapeElement?): SharedPptxShapeElement {
    if (inherited == null) return this
    val hasBounds = bounds.width() > 0f || bounds.height() > 0f
    return copy(
        bounds = if (hasBounds) bounds else inherited.bounds,
        fillColor = fillColor ?: inherited.fillColor,
        gradientFill = gradientFill ?: inherited.gradientFill,
        lineColor = lineColor ?: inherited.lineColor,
        lineWidthPoint = if (lineWidthPoint != 0.75f) lineWidthPoint else inherited.lineWidthPoint,
        paragraphs = paragraphs.inheritTextStyles(inherited.paragraphs),
        textInsets = if (textInsets == SharedPptxTextInsets()) inherited.textInsets else textInsets,
        verticalAnchor = if (verticalAnchor == SharedPptxVerticalAnchor.TOP) inherited.verticalAnchor else verticalAnchor,
        rotationDegrees = if (rotationDegrees == 0f) inherited.rotationDegrees else rotationDegrees,
        fontScale = if (fontScale == 1f) inherited.fontScale else fontScale,
        lineSpacingReduction = if (lineSpacingReduction == 0f) inherited.lineSpacingReduction else lineSpacingReduction,
        autoFitMode = if (autoFitMode == SharedPptxAutoFitMode.NONE) inherited.autoFitMode else autoFitMode,
        customGeometry = customGeometry ?: inherited.customGeometry,
    )
}

private fun List<SharedPptxParagraph>.inheritTextStyles(
    fallback: List<SharedPptxParagraph>,
): List<SharedPptxParagraph> {
    if (isEmpty() || fallback.isEmpty()) return this
    return mapIndexed { index, paragraph ->
        val fallbackParagraph = fallback.firstOrNull { it.level == paragraph.level }
            ?: fallback.getOrNull(index)
            ?: fallback.first()
        val fallbackRun = fallbackParagraph.runs.firstOrNull()
        paragraph.copy(
            runs = paragraph.runs.map { run ->
                run.copy(
                    sizePt = if (run.sizeExplicit) run.sizePt else fallbackRun?.sizePt ?: run.sizePt,
                    color = if (run.colorExplicit) run.color else fallbackRun?.color ?: run.color,
                    bold = if (run.boldExplicit) run.bold else fallbackRun?.bold ?: run.bold,
                    italic = if (run.italicExplicit) run.italic else fallbackRun?.italic ?: run.italic,
                    typeface = if (run.typefaceExplicit) run.typeface else fallbackRun?.typeface ?: run.typeface,
                    baseline = if (run.baselineExplicit) run.baseline else fallbackRun?.baseline ?: run.baseline,
                )
            },
            alignment = if (paragraph.alignmentExplicit) paragraph.alignment else fallbackParagraph.alignment,
            bullet = if (paragraph.bulletExplicit) paragraph.bullet else fallbackParagraph.bullet,
            marginLeftPt = paragraph.marginLeftPt ?: fallbackParagraph.marginLeftPt,
            indentPt = paragraph.indentPt ?: fallbackParagraph.indentPt,
            spaceBeforePt = if (paragraph.spaceBeforeExplicit) paragraph.spaceBeforePt else fallbackParagraph.spaceBeforePt,
            spaceAfterPt = if (paragraph.spaceAfterExplicit) paragraph.spaceAfterPt else fallbackParagraph.spaceAfterPt,
            lineSpacingMultiple = if (paragraph.lineSpacingExplicit) paragraph.lineSpacingMultiple else fallbackParagraph.lineSpacingMultiple,
        )
    }
}

private data class IosPptxTheme(
    val colors: Map<String, Int> = emptyMap(),
    val majorTypeface: String? = null,
    val minorTypeface: String? = null,
)

private data class IosPptxTableStyle(
    val whole: IosPptxTableCellStyle? = null,
    val firstRow: IosPptxTableCellStyle? = null,
    val lastRow: IosPptxTableCellStyle? = null,
    val firstColumn: IosPptxTableCellStyle? = null,
    val lastColumn: IosPptxTableCellStyle? = null,
)

private data class IosPptxTableCellStyle(
    val fillColor: Int?,
    val lineColor: Int?,
)

private data class IosPptxRelationship(
    val target: String,
    val resolvedTarget: String,
    val type: String,
    val targetMode: String?,
)

private class IosPptxXml(
    val name: String,
    val attributes: Map<String, String>,
    val children: MutableList<IosPptxXml> = mutableListOf(),
    private val textParts: StringBuilder = StringBuilder(),
) {
    val localName: String get() = name.substringAfterLast(':')
    fun attr(name: String): String? = attributes[name] ?: attributes.entries.firstOrNull { it.key.substringAfterLast(':') == name }?.value
    fun first(localName: String): IosPptxXml? = children.firstOrNull { it.localName.equals(localName, true) }
    fun all(localName: String): List<IosPptxXml> = children.filter { it.localName.equals(localName, true) }
    fun descendants(localName: String): List<IosPptxXml> = children.flatMap { child ->
        buildList {
            if (child.localName.equals(localName, true)) add(child)
            addAll(child.descendants(localName))
        }
    }
    fun descendant(localName: String): IosPptxXml? = children.firstOrNull { child ->
        child.localName.equals(localName, true) || child.descendant(localName) != null
    }?.let { child -> if (child.localName.equals(localName, true)) child else child.descendant(localName) }
    fun textContent(): String = buildString {
        append(textParts)
        children.forEach { append(it.textContent()) }
    }.decodeXmlEntities()

    companion object {
        fun parse(source: String): IosPptxXml {
            val root = IosPptxXml("__root__", emptyMap())
            val stack = ArrayDeque<IosPptxXml>()
            stack.addLast(root)
            var index = 0
            while (index < source.length) {
                val open = source.indexOf('<', index)
                if (open < 0) {
                    stack.last().textParts.append(source.substring(index))
                    break
                }
                if (open > index) stack.last().textParts.append(source.substring(index, open))
                val close = findXmlTagEnd(source, open + 1)
                if (close < 0) break
                val token = source.substring(open + 1, close).trim()
                when {
                    token.startsWith("!--") -> Unit
                    token.startsWith("?") || token.startsWith("!") -> Unit
                    token.startsWith("/") -> if (stack.size > 1) stack.removeLast()
                    else -> {
                        val selfClosing = token.endsWith('/')
                        val body = token.removeSuffix("/").trim()
                        val tagName = body.takeWhile { !it.isWhitespace() }
                        if (tagName.isNotBlank()) {
                            val node = IosPptxXml(tagName, parseAttributes(body.removePrefix(tagName)))
                            stack.last().children += node
                            if (!selfClosing) stack.addLast(node)
                        }
                    }
                }
                index = close + 1
            }
            return root.children.firstOrNull() ?: root
        }
    }
}

private fun findXmlTagEnd(source: String, start: Int): Int {
    var quote: Char? = null
    for (index in start until source.length) {
        val char = source[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else if (char == '\'' || char == '"') {
            quote = char
        } else if (char == '>') {
            return index
        }
    }
    return -1
}

private fun parseAttributes(source: String): Map<String, String> {
    val output = linkedMapOf<String, String>()
    val expression = Regex("([A-Za-z_][A-Za-z0-9_.:-]*)\\s*=\\s*([\\\"'])(.*?)\\2", setOf(RegexOption.DOT_MATCHES_ALL))
    expression.findAll(source).forEach { match -> output[match.groupValues[1]] = match.groupValues[3].decodeXmlEntities() }
    return output
}

private fun String.decodeXmlEntities(): String = replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
    .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
        match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
    }
    .replace(Regex("&#([0-9]+);")) { match ->
        match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
    }

private fun IosPptxXml.floatAttr(name: String): Float? = attr(name)?.toFloatOrNull()
private fun IosPptxXml.intAttr(name: String): Int? = attr(name)?.toIntOrNull()
private fun IosPptxXml.boolAttr(name: String): Boolean? = attr(name)?.let { it == "1" || it.equals("true", true) }
private fun IosPptxXml.rectFromTransform(): SharedPptxRect? {
    val off = first("off") ?: return null
    val ext = first("ext") ?: return null
    val left = off.floatAttr("x")?.emuToPoint() ?: return null
    val top = off.floatAttr("y")?.emuToPoint() ?: return null
    val width = ext.floatAttr("cx")?.emuToPoint() ?: return null
    val height = ext.floatAttr("cy")?.emuToPoint() ?: return null
    return SharedPptxRect(left, top, left + width, top + height)
}
private fun IosPptxXml.textInsets(): SharedPptxTextInsets = SharedPptxTextInsets(
    left = (floatAttr("lIns") ?: floatAttr("marL"))?.emuToPoint() ?: SharedPptxTextInsets().left,
    top = (floatAttr("tIns") ?: floatAttr("marT"))?.emuToPoint() ?: SharedPptxTextInsets().top,
    right = (floatAttr("rIns") ?: floatAttr("marR"))?.emuToPoint() ?: SharedPptxTextInsets().right,
    bottom = (floatAttr("bIns") ?: floatAttr("marB"))?.emuToPoint() ?: SharedPptxTextInsets().bottom,
)
private fun IosPptxXml.verticalAnchor(): SharedPptxVerticalAnchor = when (attr("anchor")) {
    "ctr" -> SharedPptxVerticalAnchor.MIDDLE
    "b" -> SharedPptxVerticalAnchor.BOTTOM
    else -> SharedPptxVerticalAnchor.TOP
}
private fun IosPptxXml.spacingPoints(): Float? = first("spcPts")?.floatAttr("val")?.div(100f)
    ?: first("spcPct")?.floatAttr("val")?.let { DEFAULT_TEXT_SIZE_PT * it / 100_000f }
private fun IosPptxXml.spacingMultiple(): Float = first("spcPct")?.floatAttr("val")?.div(100_000f) ?: 1f
private fun Float.emuToPoint(): Float = this / EMU_PER_POINT
private fun Float.emuToPointInt(): Int = (this / EMU_PER_POINT).toInt().coerceAtLeast(1)
private fun String.toImageContentType(): String = when (this) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    else -> "application/octet-stream"
}

private fun resolvePath(basePath: String, target: String): String {
    if (target.startsWith("http://") || target.startsWith("https://")) return target
    val parent = basePath.substringBeforeLast('/', "")
    val parts = (if (parent.isBlank()) target else "$parent/$target").replace('\\', '/').split('/')
    val normalized = mutableListOf<String>()
    parts.forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
            else -> normalized += part
        }
    }
    return normalized.joinToString("/")
}

private val naturalPathComparator = Comparator<String> { left, right ->
    val l = left.substringAfterLast('/').replace(Regex("\\d+")) { it.value.padStart(8, '0') }
    val r = right.substringAfterLast('/').replace(Regex("\\d+")) { it.value.padStart(8, '0') }
    l.compareTo(r, true)
}

private fun solidColor(node: IosPptxXml, theme: IosPptxTheme): Int? {
    val colorNode = node.descendant("srgbClr") ?: node.descendant("schemeClr") ?: node.descendant("sysClr") ?: node.descendant("prstClr") ?: return null
    val base = when (colorNode.localName) {
        "srgbClr" -> colorNode.attr("val")?.hexColor()
        "schemeClr" -> colorNode.attr("val")?.let { theme.colors[it] ?: theme.colors[it.lowercase()] }
        "sysClr" -> colorNode.attr("lastClr")?.hexColor()
        else -> colorNode.attr("val")?.let(::presetColor)
    } ?: return null
    val shade = colorNode.first("shade")?.floatAttr("val")?.div(100_000f)
    val tint = colorNode.first("tint")?.floatAttr("val")?.div(100_000f)
    val luminanceMultiplier = colorNode.first("lumMod")?.floatAttr("val")?.div(100_000f) ?: 1f
    val luminanceOffset = colorNode.first("lumOff")?.floatAttr("val")?.div(100_000f) ?: 0f
    val alpha = colorNode.first("alpha")?.floatAttr("val")?.div(100_000f)?.coerceIn(0f, 1f) ?: 1f
    fun channel(value: Int): Int {
        var next = value.toFloat()
        shade?.let { next *= it }
        tint?.let { next += (255f - next) * it }
        next = next * luminanceMultiplier + 255f * luminanceOffset
        return next.toInt().coerceIn(0, 255)
    }
    return SharedPptxColor.argb(
        (SharedPptxColor.alpha(base) * alpha).toInt(),
        channel(SharedPptxColor.red(base)),
        channel(SharedPptxColor.green(base)),
        channel(SharedPptxColor.blue(base)),
    )
}

private fun gradientColor(node: IosPptxXml, theme: IosPptxTheme): SharedPptxGradientFill? {
    val stops = node.first("gsLst")?.all("gs").orEmpty().mapNotNull { stop -> solidColor(stop, theme) }
    if (stops.size < 2) return null
    val angle = node.first("lin")?.floatAttr("ang")?.div(60_000f) ?: 0f
    return SharedPptxGradientFill(stops.first(), stops.last(), angle)
}

private fun String.hexColor(): Int? {
    val value = trim().removePrefix("#")
    if (value.length != 6) return null
    return value.toLongOrNull(16)?.toInt()?.let { SharedPptxColor.rgb(it shr 16, it shr 8 and 0xff, it and 0xff) }
}

private fun String.resolvePptxTypeface(theme: IosPptxTheme): String = when (lowercase()) {
    "+mj-lt", "+mj-ea", "+mj-cs" -> theme.majorTypeface ?: this
    "+mn-lt", "+mn-ea", "+mn-cs" -> theme.minorTypeface ?: this
    else -> this
}

private fun presetColor(name: String): Int? = when (name.lowercase()) {
    "black" -> SharedPptxColor.BLACK
    "white" -> SharedPptxColor.WHITE
    "red" -> SharedPptxColor.RED
    "green" -> SharedPptxColor.GREEN
    "blue" -> SharedPptxColor.BLUE
    "yellow" -> SharedPptxColor.YELLOW
    "cyan", "aqua" -> SharedPptxColor.CYAN
    "magenta", "fuchsia" -> SharedPptxColor.MAGENTA
    "gray", "grey" -> SharedPptxColor.GRAY
    else -> null
}

private fun <T, R : Any> Iterable<T>.associateNotNull(transform: (T) -> Pair<String, R>?): Map<String, R> = buildMap {
    for (item in this@associateNotNull) transform(item)?.let { put(it.first, it.second) }
}

private fun buildIosCharBoxes(elements: List<SharedPptxElement>): List<SharedPptxCharBox> {
    val output = mutableListOf<SharedPptxCharBox>()
    elements.forEach { element ->
        if (element is SharedPptxShapeElement && element.renderText) {
            var y = element.bounds.top + element.textInsets.top
            element.paragraphs.forEach { paragraph ->
                val text = paragraph.runs.joinToString("") { it.text }
                val size = paragraph.runs.firstOrNull()?.sizePt ?: DEFAULT_TEXT_SIZE_PT
                val charWidth = size * 0.52f
                var x = element.bounds.left + element.textInsets.left + (paragraph.marginLeftPt ?: 0f)
                text.forEach { char ->
                    if (char == '\n') {
                        y += size * 1.2f
                        x = element.bounds.left + element.textInsets.left
                    } else {
                        output += SharedPptxCharBox(char, SharedPptxRect(x, y, x + charWidth, y + size * 1.2f))
                        x += charWidth
                    }
                }
                y += size * 1.2f * paragraph.lineSpacingMultiple
            }
        }
    }
    return output
}

internal object IosPptxHtmlRenderer {
    fun render(slide: SharedPptxSlide, slideNumber: Int): String = buildString {
        append("<pptx-slide class=\"pptx-slide\" data-slide=\"").append(slideNumber).append("\" style=\"")
        append("display:block;position:relative;overflow:hidden;width:100%;height:auto;aspect-ratio:")
            .append(slide.widthPoint).append('/').append(slide.heightPoint).append(';')
        append("container-type:inline-size;background:").append(cssColor(slide.backgroundColor ?: SharedPptxColor.WHITE)).append(";page-break-after:always;\">")
        append("<pptx-canvas style=\"display:block;position:absolute;left:0;top:0;width:${slide.widthPoint}pt;height:${slide.heightPoint}pt;max-width:none!important;max-height:none!important;transform:scale(min(1,calc(100cqw / ${slide.widthPoint}pt)));transform-origin:top left;\">")
        slide.elements.forEach { element -> append(renderElement(element)) }
        append("</pptx-canvas></pptx-slide>")
    }

    private fun renderElement(element: SharedPptxElement): String = when (element) {
        is SharedPptxShapeElement -> renderShape(element)
        is SharedPptxImageElement -> renderImage(element)
        is SharedPptxTableElement -> renderTable(element)
    }

    private fun renderShape(shape: SharedPptxShapeElement): String {
        val b = shape.bounds
        val rotation = if (shape.rotationDegrees == 0f) "" else "transform:rotate(${shape.rotationDegrees}deg);"
        val fill = shape.gradientFill?.let { "background:${gradientCss(it)};" }
            ?: shape.fillColor?.let { "background:${cssColor(it)};" }.orEmpty()
        val border = shape.lineColor?.let { "border:${max(shape.lineWidthPoint, 0.25f)}pt solid ${cssColor(it)};" }.orEmpty()
        val radius = when (shape.preset) {
            "ellipse" -> "border-radius:50%;"
            "roundrect" -> "border-radius:8pt;"
            else -> ""
        }
        val clip = when (shape.preset.lowercase()) {
            "triangle", "rttriangle" -> "clip-path:polygon(50% 0,100% 100%,0 100%);"
            "diamond" -> "clip-path:polygon(50% 0,100% 50%,50% 100%,0 50%);"
            "parallelogram" -> "clip-path:polygon(20% 0,100% 0,80% 100%,0 100%);"
            "hexagon" -> "clip-path:polygon(25% 0,75% 0,100% 50%,75% 100%,25% 100%,0 50%);"
            "line" -> "border:0!important;border-top:${max(shape.lineWidthPoint, 0.25f)}pt solid ${cssColor(shape.lineColor ?: SharedPptxColor.BLACK)};height:0!important;"
            else -> ""
        }
        val geometry = shape.customGeometry?.let { geometrySvg(it, shape.fillColor, shape.lineColor, shape.lineWidthPoint) }
        val text = if (shape.renderText) renderText(shape) else ""
        val content = geometry ?: text
        val linkPrefix = shape.hyperlink?.takeIf { it.isNotBlank() }?.let { "<a href=\"${escapeHtml(it)}\" style=\"display:block;position:absolute;inset:0;\">" }.orEmpty()
        val linkSuffix = if (linkPrefix.isNotEmpty()) "</a>" else ""
        return "<pptx-shape class=\"pptx-shape\" style=\"display:block;position:absolute;left:${b.left}pt;top:${b.top}pt;width:${b.width()}pt;height:${b.height()}pt;max-width:none!important;max-height:none!important;box-sizing:border-box;$fill$border$radius$clip$rotation\">$linkPrefix$content$linkSuffix</pptx-shape>"
    }

    private fun renderText(shape: SharedPptxShapeElement): String {
        val align = when (shape.paragraphs.firstOrNull()?.alignment) {
            SharedPptxTextAlign.CENTER -> "center"
            SharedPptxTextAlign.END -> "right"
            else -> "left"
        }
        val vertical = when (shape.verticalAnchor) {
            SharedPptxVerticalAnchor.MIDDLE -> "center"
            SharedPptxVerticalAnchor.BOTTOM -> "flex-end"
            else -> "flex-start"
        }
        return "<pptx-text class=\"pptx-text\" style=\"display:flex;flex-direction:column;justify-content:$vertical;width:100%;height:100%;box-sizing:border-box;padding:${shape.textInsets.top}pt ${shape.textInsets.right}pt ${shape.textInsets.bottom}pt ${shape.textInsets.left}pt;overflow:${if (shape.autoFitMode == SharedPptxAutoFitMode.SHAPE) "visible" else "hidden"};text-align:$align;\">" +
            shape.paragraphs.joinToString("") { paragraph ->
                val pAlign = when (paragraph.alignment) {
                    SharedPptxTextAlign.CENTER -> "center"
                    SharedPptxTextAlign.END -> "right"
                    else -> "left"
                }
                val bullet = paragraph.bullet?.let { "<span class=\"pptx-bullet\">${escapeHtml(it)} </span>" }.orEmpty()
                "<pptx-paragraph class=\"pptx-paragraph\" style=\"display:block;text-align:$pAlign;margin-left:${(paragraph.marginLeftPt ?: 0f) + (paragraph.indentPt ?: 0f) * paragraph.level}pt;line-height:${(paragraph.lineSpacingMultiple * (1f - shape.lineSpacingReduction)).coerceAtLeast(0.5f)};margin-top:${paragraph.spaceBeforePt}pt;margin-bottom:${paragraph.spaceAfterPt}pt;\">$bullet${paragraph.runs.joinToString("") { run -> renderRun(run, shape.fontScale) }}</pptx-paragraph>"
            } + "</pptx-text>"
    }

    private fun renderRun(run: SharedPptxTextRun, fontScale: Float = 1f): String {
        val style = buildString {
            append("font-size:").append((run.sizePt ?: DEFAULT_TEXT_SIZE_PT) * fontScale.coerceIn(0.4f, 2f)).append("pt;")
            run.color?.let { append("color:").append(cssColor(it)).append(';') }
            if (run.bold) append("font-weight:700;")
            if (run.italic) append("font-style:italic;")
            run.typeface?.takeIf { it.isNotBlank() }?.let { append("font-family:").append(escapeCss(it)).append(';') }
            if (run.baseline != 0f) append("position:relative;top:").append(-run.baseline).append("pt;")
        }
        return "<span style=\"$style\">${escapeHtml(run.text).replace("\n", "<br/>")}</span>"
    }

    private fun renderImage(image: SharedPptxImageElement): String {
        val b = image.bounds
        val data = image.bytes.toBase64()
        val source = "data:${image.contentType ?: "application/octet-stream"};base64,$data"
        val rotation = if (image.rotationDegrees == 0f) "" else "transform:rotate(${image.rotationDegrees}deg);"
        val crop = image.crop
        val visibleWidth = (1f - crop.left - crop.right).coerceAtLeast(0.01f)
        val visibleHeight = (1f - crop.top - crop.bottom).coerceAtLeast(0.01f)
        val cropStyle = if (crop.left == 0f && crop.top == 0f && crop.right == 0f && crop.bottom == 0f) {
            "background-size:100% 100%;background-position:0 0;"
        } else {
            "background-size:${100f / visibleWidth}% ${100f / visibleHeight}%;background-position:${-crop.left / visibleWidth * 100f}% ${-crop.top / visibleHeight * 100f}%;"
        }
        return "<pptx-image class=\"pptx-image\" style=\"display:block;position:absolute;left:${b.left}pt;top:${b.top}pt;width:${b.width()}pt;height:${b.height()}pt;max-width:none!important;max-height:none!important;overflow:hidden;opacity:${image.opacity.coerceIn(0f, 1f)};$rotation;background-image:url('$source');background-repeat:no-repeat;$cropStyle\"></pptx-image>"
    }

    private fun renderTable(table: SharedPptxTableElement): String {
        val b = table.bounds
        var y = 0f
        return "<pptx-table class=\"pptx-table\" style=\"display:flex;position:absolute;left:${b.left}pt;top:${b.top}pt;width:${b.width()}pt;height:${b.height()}pt;max-width:none!important;max-height:none!important;flex-direction:column;transform:rotate(${table.rotationDegrees}deg);\">" +
            table.rows.joinToString("") { row ->
                val height = row.heightPoint ?: (b.height() / table.rows.size.coerceAtLeast(1))
                y += height
                "<pptx-row style=\"display:flex;height:${height}pt;\">" + row.cells.joinToString("") { cell ->
                    val width = cell.widthPoint ?: (b.width() / row.cells.size.coerceAtLeast(1))
                    val vertical = when (cell.verticalAnchor) {
                        SharedPptxVerticalAnchor.MIDDLE -> "center"
                        SharedPptxVerticalAnchor.BOTTOM -> "flex-end"
                        else -> "flex-start"
                    }
                    val text = cell.paragraphs.joinToString("") { paragraph ->
                        val pAlign = when (paragraph.alignment) {
                            SharedPptxTextAlign.CENTER -> "center"
                            SharedPptxTextAlign.END -> "right"
                            else -> "left"
                        }
                        val bullet = paragraph.bullet?.let { "<span class=\"pptx-bullet\">${escapeHtml(it)} </span>" }.orEmpty()
                        "<pptx-paragraph class=\"pptx-paragraph\" style=\"display:block;text-align:$pAlign;margin:0;line-height:${paragraph.lineSpacingMultiple};\">$bullet${paragraph.runs.joinToString("") { run -> renderRun(run) }}</pptx-paragraph>"
                    }
                    "<pptx-cell style=\"display:flex;flex-direction:column;justify-content:$vertical;width:${width}pt;box-sizing:border-box;padding:${cell.textInsets.top}pt ${cell.textInsets.right}pt ${cell.textInsets.bottom}pt ${cell.textInsets.left}pt;background:${cell.fillColor?.let(::cssColor) ?: "transparent"};border:0.5pt solid ${cell.lineColor?.let(::cssColor) ?: "transparent"};overflow:hidden;\">$text</pptx-cell>"
                } + "</pptx-row>"
            } + "</pptx-table>"
    }

    private fun geometrySvg(
        geometry: SharedPptxCustomGeometry,
        fillColor: Int?,
        lineColor: Int?,
        lineWidthPoint: Float,
    ): String {
        val path = buildString {
            geometry.commands.forEach { command ->
                when (command) {
                    is SharedPptxPathCommand.MoveTo -> append("M ${command.x} ${command.y} ")
                    is SharedPptxPathCommand.LineTo -> append("L ${command.x} ${command.y} ")
                    is SharedPptxPathCommand.QuadTo -> append("Q ${command.x1} ${command.y1} ${command.x2} ${command.y2} ")
                    is SharedPptxPathCommand.CubicTo -> append("C ${command.x1} ${command.y1} ${command.x2} ${command.y2} ${command.x3} ${command.y3} ")
                    SharedPptxPathCommand.Close -> append("Z ")
                }
            }
        }
        val fill = fillColor?.let(::cssColor) ?: "none"
        val stroke = lineColor?.let(::cssColor) ?: "none"
        val strokeWidth = max(lineWidthPoint, 0.25f)
        return "<svg viewBox=\"0 0 ${geometry.width} ${geometry.height}\" preserveAspectRatio=\"none\" style=\"position:absolute;inset:0;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;\"><path d=\"$path\" fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"$strokeWidth\" vector-effect=\"non-scaling-stroke\"/></svg>"
    }

    private fun cssColor(color: Int): String = "rgba(${SharedPptxColor.red(color)},${SharedPptxColor.green(color)},${SharedPptxColor.blue(color)},${SharedPptxColor.alpha(color) / 255f})"
    private fun gradientCss(fill: SharedPptxGradientFill): String = "linear-gradient(${fill.angleDegrees}deg,${cssColor(fill.startColor)},${cssColor(fill.endColor)})"
    private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun escapeCss(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun ByteArray.toBase64(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = StringBuilder((size + 2) / 3 * 4)
    var index = 0
    while (index < size) {
        val first = this[index++].toInt() and 0xff
        val second = if (index < size) this[index++].toInt() and 0xff else -1
        val third = if (index < size) this[index++].toInt() and 0xff else -1
        output.append(alphabet[first shr 2])
        output.append(alphabet[((first and 3) shl 4) or if (second >= 0) second shr 4 else 0])
        output.append(if (second >= 0) alphabet[((second and 15) shl 2) or if (third >= 0) third shr 6 else 0] else '=')
        output.append(if (third >= 0) alphabet[third and 63] else '=')
    }
    return output.toString()
}
