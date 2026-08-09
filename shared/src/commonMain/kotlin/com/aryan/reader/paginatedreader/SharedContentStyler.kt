package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.shared.reader.resolvePaginatedReaderTextAlign

class SharedContentStyler(
    private val baseTextStyle: TextStyle,
    private val fontFamilyMap: Map<String, FontFamily>,
    private val density: Density,
    private val isDarkTheme: Boolean,
    private val themeBackgroundColor: Color,
    private val themeTextColor: Color,
    private val userTextAlign: TextAlign?,
    private val paragraphGapMultiplier: Float,
    private val adaptThemeColors: Boolean = true,
    private val applyThemeToSvg: (String) -> String,
    private val embedImagesInSvg: (String) -> String,
    private val onUnsupportedBlock: (SemanticBlock) -> Unit = {},
    private val onStyledText: (SemanticTextBlock, AnnotatedString) -> Unit = { _, _ -> }
) {

    fun style(semanticBlocks: List<SemanticBlock>): List<ContentBlock> {
        return groupReaderFloatingBlocks(semanticBlocks.mapNotNull { styleBlock(it) })
    }

    private fun styleBlock(block: SemanticBlock): ContentBlock? {
        val themedStyle = applyThemeToStyle(block.style)

        val finalBlockStyle = readerContentBlockStyle(
            style = themedStyle,
            isParagraph = block is SemanticParagraph,
            paragraphGapMultiplier = paragraphGapMultiplier
        )

        return when (block) {
            is SemanticParagraph -> {
                val computedTextAlign = resolvePaginatedReaderTextAlign(
                    cssTextAlign = themedStyle.paragraphStyle.textAlign,
                    userTextAlign = userTextAlign
                )

                ParagraphBlock(
                    content = buildAnnotatedString(block, themedStyle),
                    textAlign = computedTextAlign,
                    style = finalBlockStyle,
                    elementId = block.elementId,
                    cfi = block.cfi,
                    startCharOffsetInSource = block.startCharOffsetInSource,
                    blockIndex = block.blockIndex
                )
            }

            is SemanticHeader -> HeaderBlock(
                level = block.level,
                content = buildAnnotatedString(block, themedStyle),
                textAlign = themedStyle.paragraphStyle.textAlign,
                style = themedStyle.blockStyle,
                elementId = block.elementId,
                cfi = block.cfi,
                startCharOffsetInSource = block.startCharOffsetInSource,
                blockIndex = block.blockIndex
            )

            is SemanticImage -> {
                ImageBlock(
                    path = block.path,
                    altText = block.altText,
                    intrinsicWidth = block.intrinsicWidth,
                    intrinsicHeight = block.intrinsicHeight,
                    style = readerImageBlockStyle(themedStyle),
                    elementId = block.elementId,
                    cfi = block.cfi,
                    invertOnDarkTheme = shouldInvertReaderImage(themedStyle),
                    blockIndex = block.blockIndex
                )
            }

            is SemanticMath -> {
                MathBlock(
                    svgContent = readerMathSvgContent(
                        svgContent = block.svgContent,
                        isFromMathJax = block.isFromMathJax,
                        adaptThemeColors = adaptThemeColors,
                        applyTheme = applyThemeToSvg,
                        embedImages = embedImagesInSvg
                    ),
                    altText = block.altText,
                    style = themedStyle.blockStyle,
                    elementId = block.elementId,
                    cfi = block.cfi,
                    svgWidth = block.svgWidth,
                    svgHeight = block.svgHeight,
                    svgViewBox = block.svgViewBox,
                    isFromMathJax = block.isFromMathJax,
                    blockIndex = block.blockIndex
                )
            }

            is SemanticList -> styleList(block, themedStyle)
            is SemanticTable -> styleTable(block, themedStyle)
            is SemanticSpacer -> {
                val height = if (block.isExplicitLineBreak) with(density) { baseTextStyle.fontSize.toDp() } else 8.dp
                SpacerBlock(height = height, style = themedStyle.blockStyle, elementId = block.elementId, cfi = block.cfi, blockIndex = block.blockIndex)
            }
            is SemanticFlexContainer -> if (block.style.blockStyle.display == "reader-chant-flow") {
                styleChantScore(block, themedStyle)
            } else {
                FlexContainerBlock(
                    children = block.children.mapNotNull { styleBlock(it) },
                    style = themedStyle.blockStyle,
                    elementId = block.elementId,
                    cfi = block.cfi,
                    blockIndex = block.blockIndex
                )
            }
            is SemanticWrappingBlock -> {
                val styledImage = styleBlock(block.floatedImage) as? ImageBlock
                val styledParagraphs = block.paragraphsToWrap.mapNotNull { styleBlock(it) as? ParagraphBlock }
                if (styledImage != null) {
                    WrappingContentBlock(
                        floatedImage = styledImage,
                        paragraphsToWrap = styledParagraphs,
                        elementId = block.elementId,
                        cfi = block.cfi,
                        blockIndex = block.blockIndex
                    )
                } else {
                    null
                }
            }
            else -> {
                onUnsupportedBlock(block)
                null
            }
        }
    }

    private fun styleChantScore(block: SemanticFlexContainer, themedStyle: CssStyle): ChantScoreBlock {
        return ChantScoreBlock(
            units = readerChantUnits(block) { styleBlock(it) as? TextContentBlock },
            style = themedStyle.blockStyle,
            elementId = block.elementId,
            cfi = block.cfi,
            blockIndex = block.blockIndex
        )
    }

    private fun applyThemeToStyle(style: CssStyle): CssStyle {
        return applyReaderThemeDuringContentStyling(
            style = style,
            adaptThemeColors = adaptThemeColors,
            isDarkTheme = isDarkTheme,
            themeBackgroundColor = themeBackgroundColor,
            themeTextColor = themeTextColor
        )
    }

    private fun buildAnnotatedString(
        block: SemanticTextBlock,
        blockStyle: CssStyle
    ): AnnotatedString {
        val builtString = buildAnnotatedString {
            val rootFontFamily = resolveReaderFontFamily(blockStyle.fontFamilies, fontFamilyMap)
            val isParagraph = block is SemanticParagraph
            val finalParagraphStyle = resolveReaderParagraphStyle(
                baseTextStyle = baseTextStyle,
                cssStyle = blockStyle,
                isParagraph = isParagraph,
                userTextAlign = userTextAlign
            )

            val effectiveBlockFontFamily = resolveReaderBlockFontFamily(rootFontFamily, baseTextStyle.fontFamily)

            var initialSpanStyle = baseTextStyle.toSpanStyle()
                .merge(blockStyle.spanStyle)
                .copy(fontFamily = effectiveBlockFontFamily)
            if (blockStyle.fontSize.isSpecified) {
                initialSpanStyle = initialSpanStyle.copy(fontSize = blockStyle.fontSize)
            }

            withStyle(finalParagraphStyle) {
                withStyle(initialSpanStyle) {
                    append(block.text)
                    val linkSpans = mutableListOf<SemanticSpan>()
                    block.spans.sortedBy { it.start }.forEach { span ->
                        val spanStart = span.start.coerceIn(0, block.text.length)
                        val spanEnd = span.end.coerceIn(spanStart, block.text.length)
                        val themedSpanStyle = applyThemeToStyle(span.style)
                        val spanFontFamily = resolveReaderFontFamily(themedSpanStyle.fontFamilies, fontFamilyMap)
                        val effectiveSpanFontFamily = resolveReaderSpanFontFamily(spanFontFamily, baseTextStyle.fontFamily)

                        var finalSpanStyle = themedSpanStyle.spanStyle.copy(
                            fontFamily = effectiveSpanFontFamily,
                            baselineShift = readerBaselineShift(span.tag)
                        )
                        if (themedSpanStyle.fontSize.isSpecified) {
                            finalSpanStyle = finalSpanStyle.copy(fontSize = themedSpanStyle.fontSize)
                        }

                        if (!span.linkHref.isNullOrBlank()) {
                            linkSpans.add(span)
                            finalSpanStyle = finalSpanStyle.withReaderLinkStyle(
                                isDarkTheme = isDarkTheme,
                                themeBackgroundColor = themeBackgroundColor,
                                themeTextColor = themeTextColor
                            )
                        }

                        val underlinePolicy = readerCustomUnderlinePolicy(finalSpanStyle, themedSpanStyle)
                        finalSpanStyle = underlinePolicy.spanStyle
                        val underlineAnnotation = underlinePolicy.annotationData
                        if (underlineAnnotation != null && spanStart < spanEnd) {
                            addStringAnnotation("CustomUnderline", underlineAnnotation, spanStart, spanEnd)
                        }

                        if (spanStart < spanEnd) {
                            addStyle(initialSpanStyle.merge(finalSpanStyle), spanStart, spanEnd)
                        }

                        val ws = themedSpanStyle.wordSpacing
                        readerWordSpacingOffsets(block.text, spanStart, spanEnd, themedSpanStyle).forEach { offset ->
                            addStyle(SpanStyle(letterSpacing = ws), offset, offset + 1)
                        }

                        span.linkHref?.takeIf { it.isNotBlank() }?.let { linkHref ->
                            if (spanStart < spanEnd) {
                                addStringAnnotation("URL", linkHref, spanStart, spanEnd)
                            }
                        }
                        span.elementId?.let { elementId ->
                            addStringAnnotation("ID", elementId, spanStart, spanEnd)
                        }
                    }

                    val forcedLinkStyle = readerLinkSpanStyle(
                        isDarkTheme = isDarkTheme,
                        themeBackgroundColor = themeBackgroundColor,
                        themeTextColor = themeTextColor
                    )
                    linkSpans.forEach { span ->
                        val start = span.start.coerceIn(0, block.text.length)
                        val end = span.end.coerceIn(start, block.text.length)
                        if (start < end) {
                            addStyle(forcedLinkStyle, start, end)
                        }
                    }
                }
            }
        }
        val adjusted = builtString.adjustReaderLineHeightForEmphasis()
        onStyledText(block, adjusted)
        return adjusted
    }

    private fun styleList(list: SemanticList, listStyle: CssStyle): ContentBlock {
        var itemCounter = 1

        val items = list.items.map { item ->
            val itemThemedStyle = applyThemeToStyle(item.style)
            val mergedBlockStyle = listStyle.blockStyle.merge(itemThemedStyle.blockStyle)

            val marker = getListMarker(
                listStyleType = mergedBlockStyle.listStyleType,
                counter = itemCounter,
                isOrdered = list.isOrdered
            )
            itemCounter++
            ListItemBlock(
                content = buildAnnotatedString(item, itemThemedStyle),
                itemMarker = marker,
                itemMarkerImage = item.itemMarkerImage,
                style = mergedBlockStyle,
                elementId = item.elementId,
                cfi = item.cfi,
                startCharOffsetInSource = item.startCharOffsetInSource,
                blockIndex = item.blockIndex
            )
        }
        return FlexContainerBlock(items, listStyle.blockStyle, list.elementId, list.cfi, list.blockIndex)
    }

    private fun styleTable(table: SemanticTable, tableStyle: CssStyle): TableBlock {
        val rows = table.rows.map { row ->
            row.map { cell ->
                val cellCssStyle = applyThemeToStyle(cell.style)
                TableCell(
                    content = cell.content.mapNotNull { styleBlock(it) },
                    isHeader = cell.isHeader,
                    style = cellCssStyle,
                    colspan = cell.colspan
                )
            }
        }
        return TableBlock(
            rows = rows,
            style = tableStyle.blockStyle,
            elementId = table.elementId,
            cfi = table.cfi,
            blockIndex = table.blockIndex
        )
    }

    private fun getListMarker(listStyleType: String?, counter: Int, isOrdered: Boolean): String? {
        return readerListMarker(listStyleType, counter, isOrdered)
    }
}
