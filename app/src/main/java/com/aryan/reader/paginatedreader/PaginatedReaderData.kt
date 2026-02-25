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
@file:OptIn(ExperimentalSerializationApi::class)
package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.aryan.reader.paginatedreader.serialization.AnnotatedStringSerializer
import com.aryan.reader.paginatedreader.serialization.ColorSerializer
import com.aryan.reader.paginatedreader.serialization.DpSerializer
import com.aryan.reader.paginatedreader.serialization.ParagraphStyleSerializer
import com.aryan.reader.paginatedreader.serialization.SpanStyleSerializer
import com.aryan.reader.paginatedreader.serialization.TextAlignSerializer
import com.aryan.reader.paginatedreader.serialization.TextUnitSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BlockStyle(
    @ProtoNumber(1) val margin: BoxBorders = BoxBorders(),
    @ProtoNumber(2) val padding: BoxBorders = BoxBorders(),
    @ProtoNumber(3) @Serializable(with = DpSerializer::class) val width: Dp = Dp.Unspecified,
    @ProtoNumber(4) @Serializable(with = DpSerializer::class) val maxWidth: Dp = Dp.Unspecified,
    @ProtoNumber(5) @Serializable(with = DpSerializer::class) val height: Dp = Dp.Unspecified,
    @ProtoNumber(6) @Serializable(with = ColorSerializer::class) val backgroundColor: Color = Color.Unspecified,
    @ProtoNumber(7) val border: BorderStyle? = null,
    @ProtoNumber(8) val listStyleType: String? = null,
    @ProtoNumber(9) val listStyleImage: String? = null,
    @ProtoNumber(10) val pageBreakInsideAvoid: Boolean = false,
    @ProtoNumber(11) val pageBreakAfterAvoid: Boolean = false,
    @ProtoNumber(12) val boxSizing: String? = null,
    @ProtoNumber(13) val float: String? = null,
    @ProtoNumber(14) val clear: String? = null,
    @ProtoNumber(15) val position: String? = null,
    @ProtoNumber(16) @Serializable(with = DpSerializer::class) val top: Dp = Dp.Unspecified,
    @ProtoNumber(17) @Serializable(with = DpSerializer::class) val right: Dp = Dp.Unspecified,
    @ProtoNumber(18) @Serializable(with = DpSerializer::class) val bottom: Dp = Dp.Unspecified,
    @ProtoNumber(19) @Serializable(with = DpSerializer::class) val left: Dp = Dp.Unspecified,
    @ProtoNumber(20) val display: String? = null,
    @ProtoNumber(21) val flexDirection: String? = null,
    @ProtoNumber(22) val justifyContent: String? = null,
    @ProtoNumber(23) val alignItems: String? = null,
    @ProtoNumber(24) val horizontalAlign: String? = null,
    @ProtoNumber(25) val filter: String? = null,
    @ProtoNumber(26) val borderCollapse: String? = null,
    @ProtoNumber(27) @Serializable(with = DpSerializer::class) val borderSpacing: Dp = 0.dp,
    @ProtoNumber(28) @Serializable(with = DpSerializer::class) val borderRadius: Dp = 0.dp
) {
    fun merge(other: BlockStyle): BlockStyle {
        return BlockStyle(
            margin = BoxBorders(
                top = if (other.margin.top != 0.dp) other.margin.top else this.margin.top,
                bottom = if (other.margin.bottom != 0.dp) other.margin.bottom else this.margin.bottom,
                left = if (other.margin.left != 0.dp) other.margin.left else this.margin.left,
                right = if (other.margin.right != 0.dp) other.margin.right else this.margin.right
            ),
            padding = BoxBorders(
                top = if (other.padding.top != 0.dp) other.padding.top else this.padding.top,
                bottom = if (other.padding.bottom != 0.dp) other.padding.bottom else this.padding.bottom,
                left = if (other.padding.left != 0.dp) other.padding.left else this.padding.left,
                right = if (other.padding.right != 0.dp) other.padding.right else this.padding.right
            ),
            width = if (other.width != Dp.Unspecified) other.width else this.width,
            maxWidth = if (other.maxWidth != Dp.Unspecified) other.maxWidth else this.maxWidth,
            height = if (other.height != Dp.Unspecified) other.height else this.height,
            backgroundColor = if (other.backgroundColor.isSpecified) other.backgroundColor else this.backgroundColor,
            border = other.border ?: this.border,
            listStyleType = other.listStyleType ?: this.listStyleType,
            listStyleImage = other.listStyleImage ?: this.listStyleImage,
            pageBreakInsideAvoid = this.pageBreakInsideAvoid || other.pageBreakInsideAvoid,
            pageBreakAfterAvoid = this.pageBreakAfterAvoid || other.pageBreakAfterAvoid,
            boxSizing = other.boxSizing ?: this.boxSizing,
            float = other.float ?: this.float,
            clear = other.clear ?: this.clear,
            position = other.position ?: this.position,
            top = if (other.top.isSpecified) other.top else this.top,
            right = if (other.right.isSpecified) other.right else this.right,
            bottom = if (other.bottom.isSpecified) other.bottom else this.bottom,
            left = if (other.left.isSpecified) other.left else this.left,
            display = other.display ?: this.display,
            flexDirection = other.flexDirection ?: this.flexDirection,
            justifyContent = other.justifyContent ?: this.justifyContent,
            alignItems = other.alignItems ?: this.alignItems,
            horizontalAlign = other.horizontalAlign ?: this.horizontalAlign,
            filter = other.filter ?: this.filter,
            borderCollapse = other.borderCollapse ?: this.borderCollapse,
            borderSpacing = if (other.borderSpacing != 0.dp) other.borderSpacing else this.borderSpacing,
            borderRadius = if (other.borderRadius != 0.dp) other.borderRadius else this.borderRadius
        )
    }
}

@Serializable
data class BoxBorders(
    @ProtoNumber(1) @Serializable(with = DpSerializer::class) val top: Dp = 0.dp,
    @ProtoNumber(2) @Serializable(with = DpSerializer::class) val right: Dp = 0.dp,
    @ProtoNumber(3) @Serializable(with = DpSerializer::class) val bottom: Dp = 0.dp,
    @ProtoNumber(4) @Serializable(with = DpSerializer::class) val left: Dp = 0.dp
)

@Serializable
data class BorderStyle(
    @ProtoNumber(1) @Serializable(with = DpSerializer::class) val width: Dp = 0.dp,
    @ProtoNumber(2) @Serializable(with = ColorSerializer::class) val color: Color = Color.Transparent,
    @ProtoNumber(3) val style: String = "solid"
)

@Serializable
sealed interface ContentBlock {
    val style: BlockStyle
    val elementId: String?
    val cfi: String?
    val blockIndex: Int
}

sealed interface TextContentBlock : ContentBlock {
    val content: AnnotatedString
    val startCharOffsetInSource: Int
    val endCharOffsetInSource: Int
}

@Serializable
data class ParagraphBlock(
    @ProtoNumber(1) @Serializable(with = AnnotatedStringSerializer::class) override val content: AnnotatedString,
    @ProtoNumber(2) @Serializable(with = TextAlignSerializer::class) val textAlign: TextAlign? = null,
    @ProtoNumber(3) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(4) override val elementId: String? = null,
    @ProtoNumber(5) override val cfi: String? = null,
    @ProtoNumber(6) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(7) override val endCharOffsetInSource: Int = -1,
    @ProtoNumber(8) override val blockIndex: Int
) : TextContentBlock

@Serializable
data class ImageBlock(
    @ProtoNumber(1) val path: String,
    @ProtoNumber(2) val altText: String?,
    @ProtoNumber(3) val intrinsicWidth: Float? = null,
    @ProtoNumber(4) val intrinsicHeight: Float? = null,
    @ProtoNumber(5) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(6) override val elementId: String? = null,
    @ProtoNumber(7) override val cfi: String? = null,
    @ProtoNumber(8) val invertOnDarkTheme: Boolean = false,
    @ProtoNumber(9) override val blockIndex: Int
) : ContentBlock

@Serializable
data class HeaderBlock(
    @ProtoNumber(1) val level: Int,
    @ProtoNumber(2) @Serializable(with = AnnotatedStringSerializer::class) override val content: AnnotatedString,
    @ProtoNumber(3) @Serializable(with = TextAlignSerializer::class) val textAlign: TextAlign? = null,
    @ProtoNumber(4) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(5) override val elementId: String? = null,
    @ProtoNumber(6) override val cfi: String? = null,
    @ProtoNumber(7) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(8) override val endCharOffsetInSource: Int = -1,
    @ProtoNumber(9) override val blockIndex: Int,
) : TextContentBlock

@Serializable
data class SpacerBlock(
    @ProtoNumber(1) @Serializable(with = DpSerializer::class) val height: Dp = 8.dp,
    @ProtoNumber(2) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(3) override val elementId: String? = null,
    @ProtoNumber(4) override val cfi: String? = null,
    @ProtoNumber(5) override val blockIndex: Int
) : ContentBlock

@Serializable
data class QuoteBlock(
    @ProtoNumber(1) @Serializable(with = AnnotatedStringSerializer::class) override val content: AnnotatedString,
    @ProtoNumber(2) @Serializable(with = TextAlignSerializer::class) val textAlign: TextAlign? = null,
    @ProtoNumber(3) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(4) override val elementId: String? = null,
    @ProtoNumber(5) override val cfi: String? = null,
    @ProtoNumber(6) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(7) override val endCharOffsetInSource: Int = -1,
    @ProtoNumber(8) override val blockIndex: Int
) : TextContentBlock

@Serializable
data class ListItemBlock(
    @ProtoNumber(1) @Serializable(with = AnnotatedStringSerializer::class) override val content: AnnotatedString,
    @ProtoNumber(2) val itemMarker: String?,
    @ProtoNumber(3) val itemMarkerImage: String? = null,
    @ProtoNumber(4) override val style: BlockStyle,
    @ProtoNumber(5) override val elementId: String? = null,
    @ProtoNumber(6) override val cfi: String? = null,
    @ProtoNumber(7) override val startCharOffsetInSource: Int = 0,
    @ProtoNumber(8) override val endCharOffsetInSource: Int = -1,
    @ProtoNumber(9) override val blockIndex: Int
) : TextContentBlock

@Serializable
data class TableCell(
    @ProtoNumber(1) val content: List<ContentBlock>,
    @ProtoNumber(2) val isHeader: Boolean = false,
    @ProtoNumber(3) val style: CssStyle = CssStyle(),
    @ProtoNumber(4) val colspan: Int = 1
)

@Serializable
data class TableBlock(
    @ProtoNumber(1) val rows: List<List<TableCell>>,
    @ProtoNumber(2) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(3) override val elementId: String? = null,
    @ProtoNumber(4) override val cfi: String? = null,
    @ProtoNumber(5) override val blockIndex: Int
) : ContentBlock

@Serializable
data class MathBlock(
    @ProtoNumber(1) val svgContent: String?,
    @ProtoNumber(2) val altText: String?,
    @ProtoNumber(3) override val style: BlockStyle,
    @ProtoNumber(4) override val elementId: String?,
    @ProtoNumber(5) override val cfi: String?,
    @ProtoNumber(6) val svgWidth: String? = null,
    @ProtoNumber(7) val svgHeight: String? = null,
    @ProtoNumber(8) val svgViewBox: String? = null,
    @ProtoNumber(9) val isFromMathJax: Boolean = false,
    @ProtoNumber(10) override val blockIndex: Int
) : ContentBlock

@Serializable
data class WrappingContentBlock(
    @ProtoNumber(1) val floatedImage: ImageBlock,
    @ProtoNumber(2) val paragraphsToWrap: List<ParagraphBlock>,
    @ProtoNumber(3) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(4) override val elementId: String? = null,
    @ProtoNumber(5) override val cfi: String? = null,
    @ProtoNumber(6) override val blockIndex: Int
) : ContentBlock

@Serializable
data class TextEmphasis(
    @ProtoNumber(1) val style: String? = null,
    @ProtoNumber(2) val fill: String? = null,
    @ProtoNumber(3) @Serializable(with = ColorSerializer::class) val color: Color = Color.Unspecified,
    @ProtoNumber(4) val position: String? = null
)

@Serializable
data class CssStyle(
    @ProtoNumber(1) @Serializable(with = SpanStyleSerializer::class) val spanStyle: SpanStyle = SpanStyle(),
    @ProtoNumber(2) @Serializable(with = ParagraphStyleSerializer::class) val paragraphStyle: ParagraphStyle = ParagraphStyle(),
    @ProtoNumber(3) val blockStyle: BlockStyle = BlockStyle(),
    @ProtoNumber(4) val fontFamilies: List<String> = emptyList(),
    @ProtoNumber(5) val display: String? = null,
    @ProtoNumber(6) @Serializable(with = TextUnitSerializer::class) val fontSize: TextUnit = TextUnit.Unspecified,
    @ProtoNumber(7) val textTransform: String? = null,
    @ProtoNumber(8) val boxSizing: String? = null,
    @ProtoNumber(9) val content: String? = null,
    @ProtoNumber(10) val hyphens: String? = null,
    @ProtoNumber(11) val fontVariantNumeric: String? = null,
    @ProtoNumber(12) val textEmphasis: TextEmphasis? = null
) {
    fun merge(other: CssStyle): CssStyle {
        return CssStyle(
            spanStyle = this.spanStyle.merge(other.spanStyle),
            paragraphStyle = this.paragraphStyle.merge(other.paragraphStyle),
            blockStyle = this.blockStyle.merge(other.blockStyle),
            fontFamilies = other.fontFamilies.takeIf { it.isNotEmpty() } ?: this.fontFamilies,
            display = other.display ?: this.display,
            fontSize = if (other.fontSize.isSpecified) other.fontSize else this.fontSize,
            textTransform = other.textTransform ?: this.textTransform,
            boxSizing = other.boxSizing ?: this.boxSizing,
            content = other.content ?: this.content,
            hyphens = other.hyphens ?: this.hyphens,
            fontVariantNumeric = other.fontVariantNumeric ?: this.fontVariantNumeric,
            textEmphasis = other.textEmphasis ?: this.textEmphasis
        )
    }
}

@Serializable
data class CssSelector(
    @ProtoNumber(1) val selector: String,
    @ProtoNumber(2) val specificity: Int
)

@Serializable
data class CssRule(
    @ProtoNumber(1) val selector: CssSelector,
    @ProtoNumber(2) val style: CssStyle
)

@Serializable
data class FontFaceInfo(
    @ProtoNumber(1) val fontFamily: String,
    @ProtoNumber(2) val src: String,
    @ProtoNumber(3) @Serializable(with = com.aryan.reader.paginatedreader.serialization.FontWeightSerializer::class) val fontWeight: FontWeight?,
    @ProtoNumber(4) @Serializable(with = com.aryan.reader.paginatedreader.serialization.FontStyleSerializer::class) val fontStyle: FontStyle?
)

@Serializable
data class Page(
    @ProtoNumber(1) val content: List<ContentBlock>
)

@Serializable
data class FlexContainerBlock(
    @ProtoNumber(1) val children: List<ContentBlock>,
    @ProtoNumber(2) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(3) override val elementId: String? = null,
    @ProtoNumber(4) override val cfi: String? = null,
    @ProtoNumber(5) override val blockIndex: Int
) : ContentBlock

@Serializable
data class OptimizedCssRules(
    @ProtoNumber(1) val byTag: Map<String, List<CssRule>> = emptyMap(),
    @ProtoNumber(2) val byClass: Map<String, List<CssRule>> = emptyMap(),
    @ProtoNumber(3) val byId: Map<String, List<CssRule>> = emptyMap(),
    @ProtoNumber(4) val otherComplex: List<CssRule> = emptyList()
) {
    fun merge(other: OptimizedCssRules): OptimizedCssRules {
        val mergedByTag = (this.byTag.asSequence() + other.byTag.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten() }

        val mergedByClass = (this.byClass.asSequence() + other.byClass.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten() }

        val mergedById = (this.byId.asSequence() + other.byId.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten() }

        val mergedOtherComplex = this.otherComplex + other.otherComplex

        return OptimizedCssRules(mergedByTag, mergedByClass, mergedById, mergedOtherComplex)
    }

    fun toFlatList(): List<CssRule> {
        return byTag.values.flatten() + byClass.values.flatten() + byId.values.flatten() + otherComplex
    }
}

data class OptimizedCssParseResult(
    val rules: OptimizedCssRules,
    val fontFaces: List<FontFaceInfo>
)