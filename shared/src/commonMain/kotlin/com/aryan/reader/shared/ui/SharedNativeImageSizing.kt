package com.aryan.reader.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.imagePageHeightBudgetPx
import com.aryan.reader.paginatedreader.intrinsicImageWidthPx
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.math.roundToInt

/*
 * Render-side image sizing for the native (Compose) EPUB readers.
 *
 * Split out of `SharedNativeVerticalRendering.kt` so the sizing rules sit in one place with the
 * notes explaining where the height bound comes from, which is the part that is easy to get wrong.
 */
internal fun sharedNativeImageRenderSizeDp(
    block: SemanticImage,
    density: Density,
    maxWidth: Dp,
    imageScale: Float,
    maxHeightPx: Float = Float.MAX_VALUE
): Pair<Dp, Dp>? {
    val maxWidthPx = with(density) { maxWidth.toPx() }
    return sharedNativeImageRenderSizePx(
        block = block,
        density = density,
        maxWidthPx = maxWidthPx,
        imageScale = imageScale,
        maxHeightPx = maxHeightPx
    )?.let { (widthPx, heightPx) ->
        with(density) {
            widthPx.toDp() to heightPx.toDp()
        }
    }
}

/**
 * The height budget an image gets on the current surface, in pixels.
 *
 * Android resolves this with `boundedImageMaxHeightDp`: the box's own max height when that is
 * specified, finite and positive, otherwise the contain-fit height the paginator stored in
 * `ImageBlock.expectedHeight`.
 *
 * Neither source survives on the shared paginated reader, and that was measured rather than
 * assumed: `SharedNativeImageBlock`'s `BoxWithConstraints` reported `maxHeight = 0.dp` for
 * **87 of 87** images across a whole book, because the page `Column` hands its children the
 * page's remaining height and clamps a negative remainder to zero. So a `maxHeight` of zero is
 * not "unbounded" and must not be treated as one, and there is no `expectedHeight` on
 * `SemanticImage` to fall back to. Without a fallback the tall map in *The Path to Rome*
 * (687x2246) rendered at 370x1209.6dp on a ~700dp page and overflowed it.
 *
 * The fallback is therefore the page's own height budget — the same
 * `imagePageHeightBudgetPx` the paginator measures with, so measure and render agree by
 * construction. A page height of 0 (a continuous vertical scroll) correctly means "no bound".
 */
@Composable
internal fun sharedNativeImageMaxHeightPx(density: Density, boxMaxHeight: Dp): Float {
    if (boxMaxHeight.isSpecified && boxMaxHeight != Dp.Infinity && boxMaxHeight > 0.dp) {
        return with(density) { boxMaxHeight.toPx() }
    }
    val pageHeightPx = LocalSharedNativePageImageMaxHeightPx.current
    return if (pageHeightPx > 0f) imagePageHeightBudgetPx(pageHeightPx.roundToInt()) else Float.MAX_VALUE
}

/**
 * Page content height in pixels for the surface currently being rendered, or 0 where there is no
 * page (a continuous vertical scroll). Provided by `SharedNativePaginatedPage`.
 */
internal val LocalSharedNativePageImageMaxHeightPx: ProvidableCompositionLocal<Float> =
    compositionLocalOf { 0f }

internal fun sharedNativeImageRenderSizePx(
    block: SemanticImage,
    density: Density,
    maxWidthPx: Float,
    imageScale: Float,
    maxHeightPx: Float = Float.MAX_VALUE
): Pair<Float, Float>? {
    val intrinsicWidth = block.intrinsicWidth
    val intrinsicHeight = block.intrinsicHeight
    if (intrinsicWidth == null || intrinsicHeight == null || intrinsicWidth <= 0f || intrinsicHeight <= 0f) {
        return null
    }

    val style = block.style.blockStyle
    val aspectRatio = intrinsicHeight / intrinsicWidth
    // Parity item B1, the image base width. Android's `computeImageRenderSizePx` falls back to
    // `intrinsicImageWidthPx` when there is no CSS width — the `width` attribute read as **dp**,
    // capped at the available width. Shared used the page width outright, so a small inline logo,
    // ornament or `<svg viewBox>` icon was stretched to the full column on iOS while Android drew
    // it at its intrinsic size. `intrinsicImageWidthPx` is Android's own function (commonMain), so
    // this is a call to the benchmark rather than a re-implementation of it.
    val baseWidthPx = with(density) {
        if (style.width.isPositiveSpecified()) style.width.toPx()
        else intrinsicImageWidthPx(intrinsicWidth, density, maxWidthPx)
    }

    var scaledWidthPx = baseWidthPx * imageScale
    if (style.maxWidth.isPositiveSpecified()) {
        scaledWidthPx = scaledWidthPx.coerceAtMost(with(density) { style.maxWidth.toPx() } * imageScale)
    }
    scaledWidthPx = scaledWidthPx.coerceAtMost(maxWidthPx)

    // Contain-fit, as on Android (`computeImageRenderSizePx`): a width-fit tall image shrinks in
    // width so the aspect ratio survives and it cannot overflow its box. This is the same clamp
    // `measureImageSize` already applies on the measure side, which is what keeps measure == render.
    var scaledHeightPx = scaledWidthPx * aspectRatio
    if (scaledHeightPx > maxHeightPx) {
        scaledWidthPx = (maxHeightPx / aspectRatio).coerceAtLeast(0f)
        scaledHeightPx = scaledWidthPx * aspectRatio
    }

    return scaledWidthPx to scaledHeightPx
}

internal fun sharedNativeImageRenderSizePxOrFallback(
    block: SemanticImage,
    density: Density,
    maxWidthPx: Float,
    imageScale: Float,
    settings: ReaderSettings,
    maxHeightPx: Float = Float.MAX_VALUE
): Pair<Float, Float> {
    sharedNativeImageRenderSizePx(
        block = block,
        density = density,
        maxWidthPx = maxWidthPx,
        imageScale = imageScale,
        maxHeightPx = maxHeightPx
    )?.let { return it }
    val style = block.style.blockStyle
    val widthPx = with(density) {
        when {
            style.width.isPositiveSpecified() -> style.width.toPx()
            style.maxWidth.isPositiveSpecified() -> maxWidthPx.coerceAtMost(style.maxWidth.toPx())
            else -> maxWidthPx
        }
    }.coerceAtLeast(1f)
    val heightPx = with(density) {
        (style.height.takeIfPositiveSpecified() ?: (settings.fontSize * 8f).sp.toDp()).toPx()
    }.coerceAtLeast(1f)
    return widthPx to heightPx
}
