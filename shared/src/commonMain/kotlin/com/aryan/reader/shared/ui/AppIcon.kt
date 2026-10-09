package com.aryan.reader.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One painted band of the fan, in the order the artwork paints them. */
internal data class AppIconLayer(
    val pathData: String,
    val paint: AppIconPaint,
)

internal sealed interface AppIconPaint {
    /** The gradient's stops, in artwork order. */
    val stops: List<AppIconStop>

    /** A linear gradient, in source coordinates. */
    data class Linear(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val stops: List<AppIconStop>,
    ) : AppIconPaint

    /**
     * A radial gradient whose gradient transform is a diagonal scale -- the
     * artwork's shade. The layer is drawn inside
     * `withTransform { translate(translateX, translateY); scale(scaleX, scaleY) }`:
     * the layer's path is already counter-transformed into gradient space,
     * so the transform maps it back onto the artwork while the gradient,
     * centred at the origin with [radius], maps onto the same ellipse the
     * artwork's `gradientTransform` describes.
     */
    data class Radial(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val translateX: Float,
        val translateY: Float,
        val scaleX: Float,
        val scaleY: Float,
        override val stops: List<AppIconStop>,
    ) : AppIconPaint
}

/** One stop of a gradient: its offset, the artwork's colour, its transparency. */
internal data class AppIconStop(
    val offset: Float,
    val colour: Color,
    val alpha: Float = 1f,
)

/**
 * The app's own icon: the launcher icon's fan, repainted in the app theme.
 *
 * Every colour the launcher icon ships with is a decision the app's theme has
 * already made differently: the fan is blue on white, hard-coded, baked into a
 * raster at five densities. That is fine on a home screen, where it sits on the
 * launcher's own background, and wrong everywhere inside the app -- it ignores
 * light/dark and any dynamic or custom seed colour the user has picked.
 *
 * So the in-app icon keeps the fan's artwork -- its four bands, their
 * gradients, their proportions, all generated from the one source in
 * `scripts/generate_app_icons.py` -- but every gradient stop is repainted in
 * the app theme ([inHueOf]): the hue and saturation come from the theme's
 * primary while each stop keeps its own lightness, so the pale-to-deep ladder
 * that gives the fan its reading survives and the colour follows the theme
 * exactly as a flat tint would. The plate behind it is a themed surface, which
 * is what makes it read as an icon rather than as loose artwork.
 *
 * Pure Compose, so it lives in shared and works on every host; iOS simply has
 * no in-app app-icon slot that uses it yet.
 */
@Composable
fun AppIcon(
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = LAUNCHER_CORNER_PERCENT),
    plateColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(plateColor),
        contentAlignment = Alignment.Center,
    ) {
        ThemedFan(
            contentDescription = contentDescription,
            // height comes from the artwork's own aspect ratio, so the fan is
            // never distorted and the generated file stays the single source
            // of its proportions
            modifier = Modifier
                .fillMaxWidth(MARK_WIDTH_FRACTION)
                .aspectRatio(AppIconArtwork.viewportWidth / AppIconArtwork.viewportHeight),
        )
    }
}

@Composable
private fun ThemedFan(contentDescription: String, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    // Paths and brushes are pure functions of the artwork and the theme's
    // primary, so they are built once per primary rather than per draw.
    val layers = remember(primary) {
        AppIconArtwork.layers.map { it.painted(primary) }
    }
    Canvas(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {
        // The artwork is authored in source coordinates; the canvas is laid out
        // to the artwork's own viewport, so one scale factor maps between them
        // on every host and at every density. Both transforms pivot on the
        // origin -- `scale` otherwise pivots on the middle of the canvas, which
        // would slide the fan off the plate.
        val unit = size.width / AppIconArtwork.viewportWidth
        withTransform({
            translate(
                AppIconArtwork.sourceTranslateX * unit,
                AppIconArtwork.sourceTranslateY * unit,
            )
            scale(
                AppIconArtwork.sourceScale * unit,
                AppIconArtwork.sourceScale * unit,
                pivot = Offset.Zero,
            )
        }) {
            layers.forEach { layer ->
                val radial = layer.radialTransform
                if (radial == null) {
                    drawPath(layer.path, layer.brush)
                } else {
                    // the shade layer: its path is already counter-transformed
                    // into gradient space, so this transform maps both the path
                    // and the radial back onto the artwork
                    withTransform({
                        translate(radial.translateX, radial.translateY)
                        scale(radial.scaleX, radial.scaleY, pivot = Offset.Zero)
                    }) {
                        drawPath(layer.path, layer.brush)
                    }
                }
            }
        }
    }
}

/** One artwork layer, ready to draw: its path parsed and its brush themed. */
private data class PaintedLayer(
    val path: Path,
    val brush: Brush,
    val radialTransform: RadialTransform? = null,
)

/** The diagonal transform a radial gradient layer is drawn inside. */
private data class RadialTransform(
    val translateX: Float,
    val translateY: Float,
    val scaleX: Float,
    val scaleY: Float,
)

private fun AppIconLayer.painted(primary: Color): PaintedLayer {
    val path = PathParser().parsePathString(pathData).toPath()
    val brush = paint.brush { stop -> stop.colour.inHueOf(primary, stop.alpha) }
    val radial = (paint as? AppIconPaint.Radial)?.let {
        RadialTransform(it.translateX, it.translateY, it.scaleX, it.scaleY)
    }
    return PaintedLayer(path, brush, radial)
}

private fun AppIconPaint.brush(themed: (AppIconStop) -> Color): Brush = when (this) {
    is AppIconPaint.Linear -> Brush.linearGradient(
        *stops.map { it.offset to themed(it) }.toTypedArray(),
        start = Offset(startX, startY),
        end = Offset(endX, endY),
    )
    is AppIconPaint.Radial -> Brush.radialGradient(
        *stops.map { it.offset to themed(it) }.toTypedArray(),
        center = Offset(centerX, centerY),
        radius = radius,
    )
}

/**
 * The artwork's colour, re-hued into the app theme.
 *
 * The fan is painted in a ladder of one hue: pale bands where the pages open,
 * deep bands where they close. Giving each stop the theme primary's hue and
 * saturation while keeping its own lightness keeps that ladder -- and with it
 * the reading of the artwork -- while the icon follows light/dark, dynamic
 * colour and any custom seed, exactly as a flat tint would.
 */
internal fun Color.inHueOf(primary: Color, alpha: Float = this.alpha): Color {
    val (hue, saturation) = primary.hueAndSaturation()
    return Color.hsl(hue, saturation, hslLightness(), alpha)
}

/** The HSL lightness of a colour, in 0..1. */
internal fun Color.hslLightness(): Float =
    (maxOf(red, green, blue) + minOf(red, green, blue)) / 2f

/** The HSL hue in degrees and the HSL saturation of a colour. */
internal fun Color.hueAndSaturation(): Pair<Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return 0f to 0f
    val lightness = (max + min) / 2f
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (max) {
        red -> ((green - blue) / delta) % 6f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    // Saturation is a ratio of channel distances, so it can only leave 0..1 by
    // a rounding step rather than by intent -- and it does: on a colour with a
    // channel at 0 or 255 the numerator and the denominator above are
    // mathematically equal (saturation is exactly one), and Float can still
    // make them differ in the last bit, which puts the quotient a hair over
    // one. `Color.hsl` rejects that outright, so such colours -- Material You
    // primaries among them, since its tone-40 shades and tone-80 tints clip a
    // channel -- are pulled back to the value the formula already claims.
    return ((hue + 360f) % 360f) to saturation.coerceIn(0f, 1f)
}

/**
 * The mark's width as a fraction of the plate's.
 *
 * Set by the tightest slot rather than by taste at the largest one. The mark's
 * bounding box is 54:32.8, so at 32dp the whole plate only clears a circle's
 * inscribed square -- 22.6dp -- at 0.68 or below; past that the extremities
 * cross the curve and the plate stops reading as a circle. On the larger plates
 * the same fraction simply leaves more room.
 */
private const val MARK_WIDTH_FRACTION = 0.68f

/** The launcher's own mask, so an in-app plate reads as the icon rather than a rounded box. */
private const val LAUNCHER_CORNER_PERCENT = 31
