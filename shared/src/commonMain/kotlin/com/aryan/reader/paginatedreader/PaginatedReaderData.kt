/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
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
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Bit set of the box-model properties a rule declared with an explicit reset
 * (`padding: 0`, `border: none`, `max-width: none`, …).
 *
 * The style model stores "unspecified" as `0.dp` / `null` (so an absent value
 * never overrides an earlier rule during the cascade), which also makes a real
 * zero indistinguishable from "not declared". These bits restore that
 * distinction for the properties where a reset is meaningful, without changing
 * behaviour for styles built before the field existed (mask `0`).
 */
object BlockStyleReset {
    const val MARGIN_TOP = 1L
    const val MARGIN_RIGHT = 1L shl 1
    const val MARGIN_BOTTOM = 1L shl 2
    const val MARGIN_LEFT = 1L shl 3
    const val PADDING_TOP = 1L shl 4
    const val PADDING_RIGHT = 1L shl 5
    const val PADDING_BOTTOM = 1L shl 6
    const val PADDING_LEFT = 1L shl 7
    const val BORDER_TOP = 1L shl 8
    const val BORDER_RIGHT = 1L shl 9
    const val BORDER_BOTTOM = 1L shl 10
    const val BORDER_LEFT = 1L shl 11
    const val RADIUS_TOP_LEFT = 1L shl 12
    const val RADIUS_TOP_RIGHT = 1L shl 13
    const val RADIUS_BOTTOM_RIGHT = 1L shl 14
    const val RADIUS_BOTTOM_LEFT = 1L shl 15
    const val WIDTH = 1L shl 16
    const val MAX_WIDTH = 1L shl 17
    const val MIN_WIDTH = 1L shl 18
    const val HEIGHT = 1L shl 19
    const val MAX_HEIGHT = 1L shl 20
    const val MIN_HEIGHT = 1L shl 21
}

@Serializable
data class BlockStyle(
    @ProtoNumber(1) val margin: BoxBorders = BoxBorders(),
    @ProtoNumber(2) val padding: BoxBorders = BoxBorders(),
    @ProtoNumber(3) @Serializable(with = DpSerializer::class) val width: Dp = Dp.Unspecified,
    @ProtoNumber(4) @Serializable(with = DpSerializer::class) val maxWidth: Dp = Dp.Unspecified,
    @ProtoNumber(5) @Serializable(with = DpSerializer::class) val height: Dp = Dp.Unspecified,
    @ProtoNumber(6) @Serializable(with = ColorSerializer::class) val backgroundColor: Color = Color.Unspecified,
    @ProtoNumber(7) val borderTop: BorderStyle? = null,
    @ProtoNumber(8) val borderRight: BorderStyle? = null,
    @ProtoNumber(9) val borderBottom: BorderStyle? = null,
    @ProtoNumber(10) val borderLeft: BorderStyle? = null,
    @ProtoNumber(11) val listStyleType: String? = null,
    @ProtoNumber(12) val listStyleImage: String? = null,
    @ProtoNumber(13) val pageBreakInsideAvoid: Boolean = false,
    @ProtoNumber(14) val pageBreakAfterAvoid: Boolean = false,
    @ProtoNumber(15) val boxSizing: String? = null,
    @ProtoNumber(16) val float: String? = null,
    @ProtoNumber(17) val clear: String? = null,
    @ProtoNumber(18) val position: String? = null,
    @ProtoNumber(19) @Serializable(with = DpSerializer::class) val top: Dp = Dp.Unspecified,
    @ProtoNumber(20) @Serializable(with = DpSerializer::class) val right: Dp = Dp.Unspecified,
    @ProtoNumber(21) @Serializable(with = DpSerializer::class) val bottom: Dp = Dp.Unspecified,
    @ProtoNumber(22) @Serializable(with = DpSerializer::class) val left: Dp = Dp.Unspecified,
    @ProtoNumber(23) val display: String? = null,
    @ProtoNumber(24) val flexDirection: String? = null,
    @ProtoNumber(25) val justifyContent: String? = null,
    @ProtoNumber(26) val alignItems: String? = null,
    @ProtoNumber(27) val horizontalAlign: String? = null,
    @ProtoNumber(28) val filter: String? = null,
    @ProtoNumber(29) val borderCollapse: String? = null,
    @ProtoNumber(30) @Serializable(with = DpSerializer::class) val borderTopLeftRadius: Dp = 0.dp,
    @ProtoNumber(31) @Serializable(with = DpSerializer::class) val borderTopRightRadius: Dp = 0.dp,
    @ProtoNumber(32) @Serializable(with = DpSerializer::class) val borderBottomRightRadius: Dp = 0.dp,
    @ProtoNumber(33) @Serializable(with = DpSerializer::class) val borderBottomLeftRadius: Dp = 0.dp,
    @ProtoNumber(34) @Serializable(with = DpSerializer::class) val borderSpacing: Dp = 0.dp,
    @ProtoNumber(35) @Serializable(with = DpSerializer::class) val minWidth: Dp = Dp.Unspecified,
    @ProtoNumber(36) @Serializable(with = DpSerializer::class) val minHeight: Dp = Dp.Unspecified,
    @ProtoNumber(37) @Serializable(with = DpSerializer::class) val maxHeight: Dp = Dp.Unspecified,
    @ProtoNumber(38) val overflow: String? = null,
    @ProtoNumber(39) val breakBefore: String? = null,
    @ProtoNumber(40) val breakAfter: String? = null,
    @ProtoNumber(41) val breakInside: String? = null,
    @ProtoNumber(42) val widows: Int = 2,
    @ProtoNumber(43) val orphans: Int = 2,
    @ProtoNumber(44) val visibility: String? = null,
    @ProtoNumber(45) val objectFit: String? = null,
    @ProtoNumber(46) val objectPosition: String? = null,
    @ProtoNumber(47) val backgroundImage: String? = null,
    /** Normalized publication `writing-mode` for this block (vertical-rl or null). */
    @ProtoNumber(48) val writingMode: String? = null,
    /** Flex `gap` shorthand: space on both axes unless [rowGap]/[columnGap] override it. */
    @ProtoNumber(49) @Serializable(with = DpSerializer::class) val gap: Dp = Dp.Unspecified,
    /** Flex `row-gap`: space between items stacked on the block axis. */
    @ProtoNumber(50) @Serializable(with = DpSerializer::class) val rowGap: Dp = Dp.Unspecified,
    /** Flex `column-gap`: space between items on the inline axis. */
    @ProtoNumber(51) @Serializable(with = DpSerializer::class) val columnGap: Dp = Dp.Unspecified,
    /** Flex `align-self`: this item's cross-axis alignment inside its flex container. */
    @ProtoNumber(52) val alignSelf: String? = null,
    /** See [BlockStyleReset]: properties this rule reset to their zero/none value. */
    @Transient val explicitZero: Long = 0L
) {
    fun merge(other: BlockStyle): BlockStyle {
        // A declared reset wins over an earlier rule even though its value is
        // also the "unspecified" marker (0.dp / null).
        fun reset(bit: Long): Boolean = other.explicitZero and bit != 0L

        /** Length whose "not declared" marker is 0, so a reset must be kept. */
        fun boxed(value: Dp, base: Dp, bit: Long): Dp =
            if (reset(bit) || value != 0.dp) value else base

        /** Length whose "not declared" marker is [Dp.Unspecified]. */
        fun sized(value: Dp, base: Dp, bit: Long): Dp =
            if (reset(bit) || value != Dp.Unspecified) value else base

        /** Length whose "not declared" marker is [Dp.isSpecified] being false. */
        fun specified(value: Dp, base: Dp, bit: Long): Dp =
            if (reset(bit) || value.isSpecified) value else base

        /** Optional layer (border): a reset clears it even when it is null. */
        fun <T> layered(value: T?, base: T?, bit: Long): T? =
            if (reset(bit)) value else value ?: base

        return BlockStyle(
            margin = BoxBorders(
                top = boxed(other.margin.top, margin.top, BlockStyleReset.MARGIN_TOP),
                bottom = boxed(other.margin.bottom, margin.bottom, BlockStyleReset.MARGIN_BOTTOM),
                left = boxed(other.margin.left, margin.left, BlockStyleReset.MARGIN_LEFT),
                right = boxed(other.margin.right, margin.right, BlockStyleReset.MARGIN_RIGHT)
            ),
            padding = BoxBorders(
                top = boxed(other.padding.top, padding.top, BlockStyleReset.PADDING_TOP),
                bottom = boxed(other.padding.bottom, padding.bottom, BlockStyleReset.PADDING_BOTTOM),
                left = boxed(other.padding.left, padding.left, BlockStyleReset.PADDING_LEFT),
                right = boxed(other.padding.right, padding.right, BlockStyleReset.PADDING_RIGHT)
            ),
            width = sized(other.width, width, BlockStyleReset.WIDTH),
            maxWidth = sized(other.maxWidth, maxWidth, BlockStyleReset.MAX_WIDTH),
            height = sized(other.height, height, BlockStyleReset.HEIGHT),
            backgroundColor = if (other.backgroundColor.isSpecified) other.backgroundColor else this.backgroundColor,
            borderTop = layered(other.borderTop, borderTop, BlockStyleReset.BORDER_TOP),
            borderRight = layered(other.borderRight, borderRight, BlockStyleReset.BORDER_RIGHT),
            borderBottom = layered(other.borderBottom, borderBottom, BlockStyleReset.BORDER_BOTTOM),
            borderLeft = layered(other.borderLeft, borderLeft, BlockStyleReset.BORDER_LEFT),
            borderTopLeftRadius = boxed(other.borderTopLeftRadius, borderTopLeftRadius, BlockStyleReset.RADIUS_TOP_LEFT),
            borderTopRightRadius = boxed(other.borderTopRightRadius, borderTopRightRadius, BlockStyleReset.RADIUS_TOP_RIGHT),
            borderBottomRightRadius = boxed(other.borderBottomRightRadius, borderBottomRightRadius, BlockStyleReset.RADIUS_BOTTOM_RIGHT),
            borderBottomLeftRadius = boxed(other.borderBottomLeftRadius, borderBottomLeftRadius, BlockStyleReset.RADIUS_BOTTOM_LEFT),
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
            minWidth = specified(other.minWidth, minWidth, BlockStyleReset.MIN_WIDTH),
            minHeight = specified(other.minHeight, minHeight, BlockStyleReset.MIN_HEIGHT),
            maxHeight = specified(other.maxHeight, maxHeight, BlockStyleReset.MAX_HEIGHT),
            overflow = other.overflow ?: this.overflow,
            breakBefore = other.breakBefore ?: this.breakBefore,
            breakAfter = other.breakAfter ?: this.breakAfter,
            breakInside = other.breakInside ?: this.breakInside,
            widows = if (other.widows != 2) other.widows else this.widows,
            orphans = if (other.orphans != 2) other.orphans else this.orphans,
            visibility = other.visibility ?: this.visibility,
            objectFit = other.objectFit ?: this.objectFit,
            objectPosition = other.objectPosition ?: this.objectPosition,
            backgroundImage = other.backgroundImage ?: this.backgroundImage,
            writingMode = other.writingMode ?: this.writingMode,
            gap = if (other.gap.isSpecified) other.gap else this.gap,
            rowGap = if (other.rowGap.isSpecified) other.rowGap else this.rowGap,
            columnGap = if (other.columnGap.isSpecified) other.columnGap else this.columnGap,
            alignSelf = other.alignSelf ?: this.alignSelf,
            explicitZero = this.explicitZero or other.explicitZero
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
    val expectedHeight: Int
}

sealed interface TextContentBlock : ContentBlock {
    val content: AnnotatedString
    val startCharOffsetInSource: Int
    val endCharOffsetInSource: Int
    /** Furigana readings over base-text ranges. Base text stays in [content]; readings render above (horizontal) or to the right (vertical). */
    val rubies: List<RubyAnnotation>
}

/**
 * One furigana (ruby) annotation. [baseStart]/[baseEnd] are offsets into the
 * owning block's base text; [reading] is rendered as small annotation text and
 * never participates in selection, search, TTS or pagination measurement.
 */
@Serializable
data class RubyAnnotation(
    @ProtoNumber(1) val baseStart: Int,
    @ProtoNumber(2) val baseEnd: Int,
    @ProtoNumber(3) val reading: String,
    /**
     * EPUB-specified `<rt>` font size as a fraction of the base size (null
     * means the browser default). Threaded through splits/copies unchanged.
     */
    @ProtoNumber(4) val readingScale: Float? = null
) {
    /** Effective reading size fraction: EPUB value or the browser default. */
    fun effectiveReadingScale(): Float =
        readingScale?.takeIf { it.isFinite() && it > 0f } ?: RubyReadingFontScale
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
    @ProtoNumber(8) override val blockIndex: Int,
    @ProtoNumber(9) override val expectedHeight: Int = 0,
    @ProtoNumber(10) override val rubies: List<RubyAnnotation> = emptyList()
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
    @ProtoNumber(9) override val blockIndex: Int,
    @ProtoNumber(10) override val expectedHeight: Int = 0
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
    @ProtoNumber(10) override val expectedHeight: Int = 0,
    @ProtoNumber(11) override val rubies: List<RubyAnnotation> = emptyList()
) : TextContentBlock

@Serializable
data class SpacerBlock(
    @ProtoNumber(1) @Serializable(with = DpSerializer::class) val height: Dp = 8.dp,
    @ProtoNumber(2) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(3) override val elementId: String? = null,
    @ProtoNumber(4) override val cfi: String? = null,
    @ProtoNumber(5) override val blockIndex: Int,
    @ProtoNumber(6) override val expectedHeight: Int = 0
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
    @ProtoNumber(8) override val blockIndex: Int,
    @ProtoNumber(9) override val expectedHeight: Int = 0,
    @ProtoNumber(10) override val rubies: List<RubyAnnotation> = emptyList()
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
    @ProtoNumber(9) override val blockIndex: Int,
    @ProtoNumber(10) override val expectedHeight: Int = 0,
    @ProtoNumber(11) override val rubies: List<RubyAnnotation> = emptyList()
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
    @ProtoNumber(5) override val blockIndex: Int,
    @ProtoNumber(6) override val expectedHeight: Int = 0
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
    @ProtoNumber(10) override val blockIndex: Int,
    @ProtoNumber(11) override val expectedHeight: Int = 0
) : ContentBlock

@Serializable
data class WrappingContentBlock(
    @ProtoNumber(1) val floatedImage: ImageBlock,
    @ProtoNumber(2) val paragraphsToWrap: List<ParagraphBlock>,
    @ProtoNumber(3) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(4) override val elementId: String? = null,
    @ProtoNumber(5) override val cfi: String? = null,
    @ProtoNumber(6) override val blockIndex: Int,
    @ProtoNumber(7) override val expectedHeight: Int = 0
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
    @ProtoNumber(12) val textEmphasis: TextEmphasis? = null,
    @ProtoNumber(13) @Serializable(with = TextUnitSerializer::class) val wordSpacing: TextUnit = TextUnit.Unspecified,
    @ProtoNumber(14) val textDecorationStyle: String? = null,
    @ProtoNumber(15) @Serializable(with = ColorSerializer::class) val textDecorationColor: Color = Color.Unspecified,
    @ProtoNumber(16) @Serializable(with = DpSerializer::class) val textUnderlineOffset: Dp = Dp.Unspecified,
    @ProtoNumber(17) val whiteSpace: String? = null,
    @ProtoNumber(18) val verticalAlign: String? = null,
    @ProtoNumber(19) val customProperties: Map<String, String> = emptyMap(),
    /** Normalized `writing-mode` (`vertical-rl`, `horizontal-tb` or null when unspecified). Includes `-webkit-`/`-epub-` prefixed sources. */
    @ProtoNumber(20) val writingMode: String? = null,
    /** Normalized `text-combine-upright` value (`all`, `digits …` or null). Marks tate-chu-yoko runs. */
    @ProtoNumber(21) val textCombine: String? = null,
    /** Normalized `word-break` value (`normal`, `break-all`, … or null). Includes prefixed sources. */
    @ProtoNumber(22) val wordBreak: String? = null,
    /**
     * Curated CSS declarations of this rule keyed by property name, holding the
     * CSS-wide keyword that was used (`inherit`, `initial`, `unset`, `revert`)
     * or null for an ordinary value.
     *
     * A keyword cannot be resolved while parsing: it means "whatever the parent
     * element ended up with", and the parent is only known when the element is
     * walked. The cascade also needs concrete declarations of the same property
     * to cancel an earlier keyword, hence the null entries.
     */
    @Transient val cascadeDeclarations: Map<String, String?> = emptyMap()
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
            textEmphasis = other.textEmphasis ?: this.textEmphasis,
            wordSpacing = if (other.wordSpacing.isSpecified) other.wordSpacing else this.wordSpacing,
            textDecorationStyle = other.textDecorationStyle ?: this.textDecorationStyle,
            textDecorationColor = if (other.textDecorationColor.isSpecified) other.textDecorationColor else this.textDecorationColor,
            textUnderlineOffset = if (other.textUnderlineOffset.isSpecified) other.textUnderlineOffset else this.textUnderlineOffset,
            whiteSpace = other.whiteSpace ?: this.whiteSpace,
            verticalAlign = other.verticalAlign ?: this.verticalAlign,
            customProperties = this.customProperties + other.customProperties,
            writingMode = other.writingMode ?: this.writingMode,
            textCombine = other.textCombine ?: this.textCombine,
            wordBreak = other.wordBreak ?: this.wordBreak,
            cascadeDeclarations = when {
                other.cascadeDeclarations.isEmpty() -> this.cascadeDeclarations
                this.cascadeDeclarations.isEmpty() -> other.cascadeDeclarations
                else -> this.cascadeDeclarations + other.cascadeDeclarations
            }
        )
    }
}

/**
 * True when this box lays its children out with flex semantics: a CSS flex
 * container, or one of the chant flow boxes the parser builds, whose `display`
 * carries a `reader-chant-*` marker instead of `flex` (the renderers also match
 * that marker to recognise a chant score). Every other block box is normal flow,
 * and `flex-direction` on it is ignored, exactly as a browser ignores it.
 */
val BlockStyle.isFlexContainer: Boolean
    get() {
        val value = display?.trim()?.lowercase() ?: return false
        return value == "flex" || value == "inline-flex" || value.startsWith("reader-chant-")
    }

/**
 * CSS `flex-direction` with its initial value applied. `display: flex` without an
 * explicit `flex-direction` is a row, but renderers tested the raw value for
 * `"row"` and therefore stacked every default flex container as a column.
 */
val BlockStyle.resolvedFlexDirection: String
    get() = flexDirection?.trim()?.lowercase() ?: "row"

/**
 * Flex spacing along the container's main axis: [rowGap] between stacked items,
 * [columnGap] between items in a row, each falling back to the [gap] shorthand.
 */
val BlockStyle.flexMainAxisGap: Dp
    get() {
        val explicit = if (isFlexRow) columnGap else rowGap
        return if (explicit.isSpecified) explicit else gap
    }

/** True when this flex container lays its items out along the inline axis. */
val BlockStyle.isFlexRow: Boolean
    get() = isFlexContainer && (resolvedFlexDirection == "row" || resolvedFlexDirection == "row-reverse")

/**
 * True when CSS `visibility` hides this box while keeping its space
 * (`hidden` and `collapse`). Block containers honor it with an alpha layer;
 * inline boxes need it applied to their text style, since a hidden icon span
 * still occupies its glyph cell.
 */
fun BlockStyle.isVisuallyHidden(): Boolean = visibility == "hidden" || visibility == "collapse"

/** True when this style requests vertical Japanese layout (`vertical-rl` / `vertical-lr`). */
fun CssStyle.isVerticalWriting(): Boolean =
    writingMode == "vertical-rl" || writingMode == "vertical-lr"

/** True when this span is a tate-chu-yoko run (`text-combine-upright: all`). */
fun CssStyle.isTateChuYoko(): Boolean =
    textCombine?.lowercase()?.trim() == "all"

@Serializable
data class CssSelector(
    @ProtoNumber(1) val selector: String,
    @ProtoNumber(2) val specificity: Int
)

@Serializable
data class CssRule(
    @ProtoNumber(1) val selector: CssSelector,
    @ProtoNumber(2) val style: CssStyle,
    @ProtoNumber(3) val pseudoElement: String? = null,
    @ProtoNumber(4) val sourceOrder: Int = 0
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
    @ProtoNumber(5) override val blockIndex: Int,
    @ProtoNumber(6) override val expectedHeight: Int = 0
) : ContentBlock

@Serializable
data class ChantUnitBlock(
    @ProtoNumber(1) @Serializable(with = AnnotatedStringSerializer::class) val neume: AnnotatedString,
    @ProtoNumber(2) @Serializable(with = AnnotatedStringSerializer::class) val lyric: AnnotatedString,
    @ProtoNumber(3) val keepWithNext: Boolean = false,
    @ProtoNumber(4) val underlineBefore: Boolean = false,
    @ProtoNumber(5) val underlineAfter: Boolean = false,
    @ProtoNumber(6) val isDropCap: Boolean = false
)

@Serializable
data class ChantScoreBlock(
    @ProtoNumber(1) val units: List<ChantUnitBlock>,
    @ProtoNumber(2) override val style: BlockStyle = BlockStyle(),
    @ProtoNumber(3) override val elementId: String? = null,
    @ProtoNumber(4) override val cfi: String? = null,
    @ProtoNumber(5) override val blockIndex: Int,
    @ProtoNumber(6) override val expectedHeight: Int = 0
) : ContentBlock

@Serializable
data class OptimizedCssRules(
    @ProtoNumber(1) val byTag: Map<String, List<CssRule>> = emptyMap(),
    @ProtoNumber(2) val byClass: Map<String, List<CssRule>> = emptyMap(),
    @ProtoNumber(3) val byId: Map<String, List<CssRule>> = emptyMap(),
    @ProtoNumber(4) val otherComplex: List<CssRule> = emptyList(),
    @ProtoNumber(5) val allRules: List<CssRule> = emptyList()
) {
    fun merge(other: OptimizedCssRules): OptimizedCssRules {
        fun mergeMap(
            m1: Map<String, List<CssRule>>,
            m2: Map<String, List<CssRule>>
        ): Map<String, List<CssRule>> {
            if (m1.isEmpty()) return m2
            if (m2.isEmpty()) return m1

            val result = LinkedHashMap(m1)
            for ((key, value) in m2) {
                val existing = result[key]
                if (existing != null) {
                    result[key] = existing + value
                } else {
                    result[key] = value
                }
            }
            return result
        }

        return OptimizedCssRules(
            byTag = mergeMap(this.byTag, other.byTag),
            byClass = mergeMap(this.byClass, other.byClass),
            byId = mergeMap(this.byId, other.byId),
            otherComplex = this.otherComplex + other.otherComplex,
            allRules = this.toFlatList() + other.toFlatList()
        )
    }

    fun toFlatList(): List<CssRule> {
        return allRules.ifEmpty { byTag.values.flatten() + byClass.values.flatten() + byId.values.flatten() + otherComplex }
    }
}

data class OptimizedCssParseResult(
    val rules: OptimizedCssRules,
    val fontFaces: List<FontFaceInfo>
)
