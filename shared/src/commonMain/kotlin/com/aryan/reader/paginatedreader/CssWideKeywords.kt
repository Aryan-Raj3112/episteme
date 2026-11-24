package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CSS-wide keywords (`inherit`, `initial`, `unset`, `revert`, `revert-layer`)
 * resolved against the style an element inherited from its parent.
 *
 * The parser records the keyword for the properties it understands in
 * [CssStyle.cascadeDeclarations]; by the time an element is walked the cascade
 * has produced the final own style, so a keyword can overwrite whatever less
 * specific author rules had put there. Books rely on this to cancel a light
 * mode `color: #000 !important` (or a hard-coded padding/border) and fall back
 * to the reader theme, which is the only way an inverted or grey theme can
 * keep the text legible.
 */
fun CssStyle.resolveCssWideKeywords(parent: CssStyle): CssStyle {
    if (cascadeDeclarations.none { it.value != null }) return this
    var resolved = this
    cascadeDeclarations.forEach { (property, keyword) ->
        if (keyword == null) return@forEach
        val fromParent = keyword.resolvesToParent(property in CssWideKeywords.inheritedProperties)
        resolved = when (property) {
            "color" -> resolved.copy(
                spanStyle = resolved.spanStyle.copy(
                    color = if (fromParent) parent.spanStyle.color else Color.Unspecified
                )
            )
            "background-color" -> resolved.withBackgroundColor(
                if (fromParent) parent.blockStyle.backgroundColor else Color.Unspecified
            )
            "background" -> resolved.withBackgroundColor(
                if (fromParent) parent.blockStyle.backgroundColor else Color.Unspecified
            ).let { withColor ->
                withColor.withBlockStyle(
                    withColor.blockStyle.copy(
                        backgroundImage = if (fromParent) parent.blockStyle.backgroundImage else null
                    )
                )
            }
            "background-image" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    backgroundImage = if (fromParent) parent.blockStyle.backgroundImage else null
                )
            )
            "font-family" -> resolved.copy(
                fontFamilies = if (fromParent) parent.fontFamilies else emptyList()
            )
            "font-size" -> resolved.copy(
                fontSize = if (fromParent) parent.fontSize else 16.sp
            )
            "font-weight" -> resolved.copy(
                spanStyle = resolved.spanStyle.copy(
                    fontWeight = if (fromParent) parent.spanStyle.fontWeight else FontWeight.Normal
                )
            )
            "font-style" -> resolved.copy(
                spanStyle = resolved.spanStyle.copy(
                    fontStyle = if (fromParent) parent.spanStyle.fontStyle else FontStyle.Normal
                )
            )
            "font-variant" -> resolved.copy(
                spanStyle = resolved.spanStyle.copy(
                    fontFeatureSettings = if (fromParent) parent.spanStyle.fontFeatureSettings else null
                )
            )
            "line-height" -> resolved.copy(
                paragraphStyle = resolved.paragraphStyle.copy(
                    lineHeight = if (fromParent) parent.paragraphStyle.lineHeight else TextUnit.Unspecified
                )
            )
            "letter-spacing" -> resolved.copy(
                spanStyle = resolved.spanStyle.copy(
                    letterSpacing = if (fromParent) parent.spanStyle.letterSpacing else TextUnit.Unspecified
                )
            )
            "word-spacing" -> resolved.copy(
                wordSpacing = if (fromParent) parent.wordSpacing else TextUnit.Unspecified
            )
            "text-align" -> resolved.copy(
                paragraphStyle = resolved.paragraphStyle.copy(
                    textAlign = if (fromParent) parent.paragraphStyle.textAlign else TextAlign.Start
                )
            )
            "text-indent" -> resolved.copy(
                paragraphStyle = resolved.paragraphStyle.copy(
                    textIndent = if (fromParent) parent.paragraphStyle.textIndent else null
                )
            )
            "text-decoration" -> resolved.copy(
                spanStyle = resolved.spanStyle.copy(
                    textDecoration = if (fromParent) parent.spanStyle.textDecoration else null
                )
            )
            "text-transform" -> resolved.copy(
                textTransform = if (fromParent) parent.textTransform else "none"
            )
            "white-space" -> resolved.copy(
                whiteSpace = if (fromParent) parent.whiteSpace else "normal"
            )
            "hyphens" -> resolved.copy(
                hyphens = if (fromParent) parent.hyphens else "manual"
            )
            "visibility" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    visibility = if (fromParent) parent.blockStyle.visibility else "visible"
                )
            )
            // `display` is stored on both the style and its block style.
            "display" -> resolved.copy(
                display = if (fromParent) parent.display else null
            ).withBlockStyle(
                resolved.blockStyle.copy(
                    display = if (fromParent) parent.blockStyle.display else null
                )
            )
            "writing-mode" -> resolved.copy(
                writingMode = if (fromParent) parent.writingMode else "horizontal-tb"
            )
            "list-style-type" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    listStyleType = if (fromParent) parent.blockStyle.listStyleType else null
                )
            )
            "list-style-image" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    listStyleImage = if (fromParent) parent.blockStyle.listStyleImage else null
                )
            )
            "list-style" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    listStyleType = if (fromParent) parent.blockStyle.listStyleType else null,
                    listStyleImage = if (fromParent) parent.blockStyle.listStyleImage else null
                )
            )
            "margin" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    margin = if (fromParent) parent.blockStyle.margin else BoxBorders(),
                    explicitZero = if (fromParent) {
                        resolved.blockStyle.explicitZero
                    } else {
                        resolved.blockStyle.explicitZero or CssWideKeywords.allMarginResetBits
                    }
                )
            )
            "margin-top", "margin-right", "margin-bottom", "margin-left" ->
                resolved.withBlockStyle(
                    resolved.blockStyle.copyWithMarginSide(
                        property.removePrefix("margin-"),
                        if (fromParent) parent.blockStyle.margin else BoxBorders()
                    )
                )
            "padding" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    padding = if (fromParent) parent.blockStyle.padding else BoxBorders(),
                    explicitZero = if (fromParent) {
                        resolved.blockStyle.explicitZero
                    } else {
                        resolved.blockStyle.explicitZero or CssWideKeywords.allPaddingResetBits
                    }
                )
            )
            "padding-top", "padding-right", "padding-bottom", "padding-left" ->
                resolved.withBlockStyle(
                    resolved.blockStyle.copyWithPaddingSide(
                        property.removePrefix("padding-"),
                        if (fromParent) parent.blockStyle.padding else BoxBorders()
                    )
                )
            "border" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBordersFrom(if (fromParent) parent.blockStyle else null)
            )
            "border-top" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderSidesFrom(
                    if (fromParent) parent.blockStyle else null,
                    top = true
                )
            )
            "border-right" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderSidesFrom(
                    if (fromParent) parent.blockStyle else null,
                    right = true
                )
            )
            "border-bottom" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderSidesFrom(
                    if (fromParent) parent.blockStyle else null,
                    bottom = true
                )
            )
            "border-left" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderSidesFrom(
                    if (fromParent) parent.blockStyle else null,
                    left = true
                )
            )
            "border-radius" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderRadiusFrom(if (fromParent) parent.blockStyle else null)
            )
            "border-top-left-radius" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderRadiusSideFrom(
                    if (fromParent) parent.blockStyle else null,
                    topLeft = true
                )
            )
            "border-top-right-radius" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderRadiusSideFrom(
                    if (fromParent) parent.blockStyle else null,
                    topRight = true
                )
            )
            "border-bottom-right-radius" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderRadiusSideFrom(
                    if (fromParent) parent.blockStyle else null,
                    bottomRight = true
                )
            )
            "border-bottom-left-radius" -> resolved.withBlockStyle(
                resolved.blockStyle.copyBorderRadiusSideFrom(
                    if (fromParent) parent.blockStyle else null,
                    bottomLeft = true
                )
            )
            "border-collapse" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    borderCollapse = if (fromParent) parent.blockStyle.borderCollapse else "separate"
                )
            )
            "border-spacing" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    borderSpacing = if (fromParent) parent.blockStyle.borderSpacing else 0.dp
                )
            )
            "width", "max-width", "min-width", "height", "max-height", "min-height" ->
                resolved.withBlockStyle(
                    resolved.blockStyle.copySizedPropertyFrom(
                        property,
                        if (fromParent) parent.blockStyle else null
                    )
                )
            "float" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(float = if (fromParent) parent.blockStyle.float else "none")
            )
            "clear" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(clear = if (fromParent) parent.blockStyle.clear else "none")
            )
            "position" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(position = if (fromParent) parent.blockStyle.position else "static")
            )
            "overflow" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(overflow = if (fromParent) parent.blockStyle.overflow else "visible")
            )
            "flex-direction" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    flexDirection = if (fromParent) parent.blockStyle.flexDirection else "row"
                )
            )
            "justify-content" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    justifyContent = if (fromParent) parent.blockStyle.justifyContent else "normal"
                )
            )
            "align-items" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    alignItems = if (fromParent) parent.blockStyle.alignItems else "normal"
                )
            )
            "align-self" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    alignSelf = if (fromParent) parent.blockStyle.alignSelf else "auto"
                )
            )
            "gap" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    gap = if (fromParent) parent.blockStyle.gap else 0.dp
                )
            )
            "row-gap" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    rowGap = if (fromParent) parent.blockStyle.rowGap else 0.dp
                )
            )
            "column-gap" -> resolved.withBlockStyle(
                resolved.blockStyle.copy(
                    columnGap = if (fromParent) parent.blockStyle.columnGap else 0.dp
                )
            )
            else -> resolved
        }
    }
    return resolved
}

/** Property names whose keywords [resolveCssWideKeywords] understands. */
object CssWideKeywords {
    val inheritedProperties: Set<String> = setOf(
        "color", "font-family", "font-size", "font-weight", "font-style",
        "font-variant", "line-height", "letter-spacing", "word-spacing",
        "text-align", "text-indent", "text-transform", "text-decoration",
        "white-space", "hyphens", "visibility", "list-style-type",
        "list-style-image", "writing-mode"
    )

    /** Every property the resolver handles, including shorthands. */
    val properties: Set<String> = inheritedProperties + setOf(
        "background", "background-color", "background-image", "display",
        "list-style", "margin", "margin-top", "margin-right", "margin-bottom",
        "margin-left", "padding", "padding-top", "padding-right",
        "padding-bottom", "padding-left", "border", "border-top", "border-right",
        "border-bottom", "border-left", "border-radius", "border-top-left-radius",
        "border-top-right-radius", "border-bottom-right-radius",
        "border-bottom-left-radius", "border-collapse", "border-spacing", "width",
        "max-width", "min-width", "height", "max-height", "min-height", "float",
        "clear", "position", "overflow", "flex-direction", "justify-content",
        "align-items", "align-self", "gap", "row-gap", "column-gap"
    )

    const val allMarginResetBits: Long = BlockStyleReset.MARGIN_TOP or
        BlockStyleReset.MARGIN_RIGHT or BlockStyleReset.MARGIN_BOTTOM or
        BlockStyleReset.MARGIN_LEFT
    const val allPaddingResetBits: Long = BlockStyleReset.PADDING_TOP or
        BlockStyleReset.PADDING_RIGHT or BlockStyleReset.PADDING_BOTTOM or
        BlockStyleReset.PADDING_LEFT
    const val allBorderResetBits: Long = BlockStyleReset.BORDER_TOP or
        BlockStyleReset.BORDER_RIGHT or BlockStyleReset.BORDER_BOTTOM or
        BlockStyleReset.BORDER_LEFT
    const val allRadiusResetBits: Long = BlockStyleReset.RADIUS_TOP_LEFT or
        BlockStyleReset.RADIUS_TOP_RIGHT or BlockStyleReset.RADIUS_BOTTOM_RIGHT or
        BlockStyleReset.RADIUS_BOTTOM_LEFT

    /** [valueLower] must already be trimmed and lowercased. */
    internal fun keywordOrNull(valueLower: String): String? =
        valueLower.takeIf { it in CSS_WIDE_KEYWORDS }
}

private val CSS_WIDE_KEYWORDS = setOf("inherit", "initial", "unset", "revert", "revert-layer")

private fun String.resolvesToParent(inheritedProperty: Boolean): Boolean = when (this) {
    "inherit" -> true
    "initial" -> false
    // `unset`/`revert` mean "the value the cascade would produce without this
    // declaration", i.e. inheritance for inherited properties and the initial
    // value for everything else. There is no UA stylesheet to revert to here.
    else -> inheritedProperty
}

private fun CssStyle.withBlockStyle(style: BlockStyle): CssStyle = copy(blockStyle = style)

private fun CssStyle.withBackgroundColor(color: Color): CssStyle =
    withBlockStyle(blockStyle.copy(backgroundColor = color))

private fun BlockStyle.copyWithMarginSide(side: String, source: BoxBorders): BlockStyle = copy(
    margin = when (side) {
        "top" -> margin.copy(top = source.top)
        "right" -> margin.copy(right = source.right)
        "bottom" -> margin.copy(bottom = source.bottom)
        else -> margin.copy(left = source.left)
    },
    explicitZero = explicitZero or when (side) {
        "top" -> BlockStyleReset.MARGIN_TOP
        "right" -> BlockStyleReset.MARGIN_RIGHT
        "bottom" -> BlockStyleReset.MARGIN_BOTTOM
        else -> BlockStyleReset.MARGIN_LEFT
    }
)

private fun BlockStyle.copyWithPaddingSide(side: String, source: BoxBorders): BlockStyle = copy(
    padding = when (side) {
        "top" -> padding.copy(top = source.top)
        "right" -> padding.copy(right = source.right)
        "bottom" -> padding.copy(bottom = source.bottom)
        else -> padding.copy(left = source.left)
    },
    explicitZero = explicitZero or when (side) {
        "top" -> BlockStyleReset.PADDING_TOP
        "right" -> BlockStyleReset.PADDING_RIGHT
        "bottom" -> BlockStyleReset.PADDING_BOTTOM
        else -> BlockStyleReset.PADDING_LEFT
    }
)

private fun BlockStyle.copyBordersFrom(parent: BlockStyle?): BlockStyle = copy(
    borderTop = parent?.borderTop,
    borderRight = parent?.borderRight,
    borderBottom = parent?.borderBottom,
    borderLeft = parent?.borderLeft,
    explicitZero = explicitZero or CssWideKeywords.allBorderResetBits
)

private fun BlockStyle.copyBorderSidesFrom(
    parent: BlockStyle?,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false,
    left: Boolean = false
): BlockStyle = copy(
    borderTop = if (top) parent?.borderTop else borderTop,
    borderRight = if (right) parent?.borderRight else borderRight,
    borderBottom = if (bottom) parent?.borderBottom else borderBottom,
    borderLeft = if (left) parent?.borderLeft else borderLeft,
    explicitZero = explicitZero or
        (if (top) BlockStyleReset.BORDER_TOP else 0L) or
        (if (right) BlockStyleReset.BORDER_RIGHT else 0L) or
        (if (bottom) BlockStyleReset.BORDER_BOTTOM else 0L) or
        (if (left) BlockStyleReset.BORDER_LEFT else 0L)
)

private fun BlockStyle.copyBorderRadiusFrom(parent: BlockStyle?): BlockStyle = copy(
    borderTopLeftRadius = parent?.borderTopLeftRadius ?: 0.dp,
    borderTopRightRadius = parent?.borderTopRightRadius ?: 0.dp,
    borderBottomRightRadius = parent?.borderBottomRightRadius ?: 0.dp,
    borderBottomLeftRadius = parent?.borderBottomLeftRadius ?: 0.dp,
    explicitZero = explicitZero or CssWideKeywords.allRadiusResetBits
)

private fun BlockStyle.copyBorderRadiusSideFrom(
    parent: BlockStyle?,
    topLeft: Boolean = false,
    topRight: Boolean = false,
    bottomRight: Boolean = false,
    bottomLeft: Boolean = false
): BlockStyle = copy(
    borderTopLeftRadius = if (topLeft) parent?.borderTopLeftRadius ?: 0.dp else borderTopLeftRadius,
    borderTopRightRadius = if (topRight) parent?.borderTopRightRadius ?: 0.dp else borderTopRightRadius,
    borderBottomRightRadius = if (bottomRight) parent?.borderBottomRightRadius ?: 0.dp else borderBottomRightRadius,
    borderBottomLeftRadius = if (bottomLeft) parent?.borderBottomLeftRadius ?: 0.dp else borderBottomLeftRadius,
    explicitZero = explicitZero or
        (if (topLeft) BlockStyleReset.RADIUS_TOP_LEFT else 0L) or
        (if (topRight) BlockStyleReset.RADIUS_TOP_RIGHT else 0L) or
        (if (bottomRight) BlockStyleReset.RADIUS_BOTTOM_RIGHT else 0L) or
        (if (bottomLeft) BlockStyleReset.RADIUS_BOTTOM_LEFT else 0L)
)

private fun BlockStyle.copySizedPropertyFrom(property: String, parent: BlockStyle?): BlockStyle {
    val value = when (property) {
        "width" -> parent?.width
        "max-width" -> parent?.maxWidth
        "min-width" -> parent?.minWidth
        "height" -> parent?.height
        "max-height" -> parent?.maxHeight
        else -> parent?.minHeight
    } ?: Dp.Unspecified
    val bit = when (property) {
        "width" -> BlockStyleReset.WIDTH
        "max-width" -> BlockStyleReset.MAX_WIDTH
        "min-width" -> BlockStyleReset.MIN_WIDTH
        "height" -> BlockStyleReset.HEIGHT
        "max-height" -> BlockStyleReset.MAX_HEIGHT
        else -> BlockStyleReset.MIN_HEIGHT
    }
    return when (property) {
        "width" -> copy(width = value)
        "max-width" -> copy(maxWidth = value)
        "min-width" -> copy(minWidth = value)
        "height" -> copy(height = value)
        "max-height" -> copy(maxHeight = value)
        else -> copy(minHeight = value)
    }.copy(explicitZero = explicitZero or bit)
}