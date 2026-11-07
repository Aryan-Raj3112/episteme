package com.aryan.reader.paginatedreader

import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * Image render decisions shared by both native stacks (B1 step 3, image block
 * kind).
 *
 * Android's paginated + vertical branches and shared's `SemanticImage`
 * branches carried byte-identical copies of three decisions: the dark-mode
 * invert matrix, the float/align placement and the object-fit scale. They are
 * pure mappings over [BlockStyle], so they live here once. Sizing stays out —
 * base width is already single-sourced and the unsized-image fallback height
 * genuinely diverges (250.dp Android vs `nativeFontSize × 8` shared), which
 * needs a measure+render gate and is recorded below, not papered over.
 */

/** Dark-mode invert matrix. Returns a fresh copy; callers hand it to a `ColorMatrix`. */
fun nativeInvertColorMatrix(): FloatArray = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

fun nativeImageContentAlignment(blockStyle: BlockStyle): Alignment = when {
    blockStyle.float == "right" || blockStyle.horizontalAlign == "right" || blockStyle.horizontalAlign == "end" ->
        Alignment.CenterEnd
    blockStyle.float == "left" || blockStyle.horizontalAlign == "left" || blockStyle.horizontalAlign == "start" ->
        Alignment.CenterStart
    else -> Alignment.Center
}

fun nativeImageContentScale(blockStyle: BlockStyle): ContentScale = when (blockStyle.objectFit) {
    "cover" -> ContentScale.Crop
    "fill" -> ContentScale.FillBounds
    "contain", "scale-down" -> ContentScale.Fit
    else -> ContentScale.Fit
}

/**
 * Font-relative math-SVG dimension in dp-space. The dp-space parser behind
 * `SemanticMath.toSharedNativeSvgImage` covers px/pt/unitless only, so em/ex
 * fell to the viewBox fallback while Android resolves them against the font
 * size. This fills that gap through Android's own [parseSvgDimension]
 * (physical px converted back to dp-space, matching how intrinsic dims are
 * read downstream). Percent needs a container width the call site has not
 * measured, so it stays null — recorded, not forced.
 */
fun nativeMathSvgDimensionDp(
    dimension: String?,
    fontSizeSp: Float,
    density: Density,
): Float? {
    val lower = dimension?.trim()?.lowercase() ?: return null
    if (!lower.endsWith("em") && !lower.endsWith("ex")) return null
    if (density.density <= 0f) return null
    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    // `lower` is already trimmed, so unit suffixes match despite source casing.
    return parseSvgDimension(lower, fontSizePx, containerWidthPx = 0, density = density)
        ?.div(density.density)
        ?.takeIf { it > 0f }
}
