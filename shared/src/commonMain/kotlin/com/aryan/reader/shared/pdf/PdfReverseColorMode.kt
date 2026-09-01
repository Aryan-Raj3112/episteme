package com.aryan.reader.shared.pdf

/**
 * Colour transform used by the PDF reverse theme.
 *
 * The first four values mirror Okular's render modes added in commit
 * aa6833483e9a87b151e38dd82b695ea9f5ce7b8d (28 March 2020):
 * https://invent.kde.org/graphics/okular/-/commit/aa6833483e9a87b151e38dd82b695ea9f5ce7b8d
 *
 * [RGB] is Reader's historical per-channel negative. The remaining modes
 * use the same HSL/HSY constructions and coefficients as Okular. Keeping the
 * transform here makes Android and iOS use one algorithm; platform renderers
 * only provide the bitmap/shader adapter.
 */
enum class PdfReverseColorMode(val id: String) {
    RGB("rgb"),
    LIGHTNESS("lightness"),
    LUMA_SRGB_LINEAR("luma_srgb_linear"),
    LUMA_SYMMETRIC("luma_symmetric");

    companion object {
        fun fromId(id: String?): PdfReverseColorMode =
            entries.firstOrNull { it.id == id } ?: RGB
    }
}

/** A pixel-space region whose source colors must bypass the page transform. */
data class PdfReverseColorRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

fun invertPdfArgbIfUnprotected(
    argb: Int,
    x: Int,
    y: Int,
    mode: PdfReverseColorMode,
    protectedRects: List<PdfReverseColorRect>,
): Int = if (
    protectedRects.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
) {
    argb
} else {
    invertPdfArgb(argb, mode)
}

/** Applies the selected Okular-compatible transform to an ARGB pixel. */
fun invertPdfArgb(argb: Int, mode: PdfReverseColorMode): Int {
    val alpha = (argb ushr 24) and 0xFF
    val red = (argb ushr 16) and 0xFF
    val green = (argb ushr 8) and 0xFF
    val blue = argb and 0xFF
    // This function is called once for every pixel of every transformed page.
    // Keep the hot path allocation-free: the previous implementation created
    // one IntArray for the mode and another from each lightness/luma helper.
    // A few visible pages at 1.5x resolution could therefore allocate tens of
    // megabytes during a single fling and trigger long stop-the-world GCs.
    return (alpha shl 24) or invertPdfRgb(red, green, blue, mode)
}

/** Packed 0x00RRGGBB output for the allocation-sensitive renderer hot path. */
private fun invertPdfRgb(
    red: Int,
    green: Int,
    blue: Int,
    mode: PdfReverseColorMode,
): Int {
    return when (mode) {
        PdfReverseColorMode.RGB -> packPdfRgb(255 - red, 255 - green, 255 - blue)
        PdfReverseColorMode.LIGHTNESS -> {
            val minimum = minOf(red, green, blue)
            val redChroma = red - minimum
            val greenChroma = green - minimum
            val blueChroma = blue - minimum
            val invertedCommon = 255 - maxOf(redChroma, greenChroma, blueChroma) - minimum
            packPdfRgb(
                redChroma + invertedCommon,
                greenChroma + invertedCommon,
                blueChroma + invertedCommon,
            )
        }
        PdfReverseColorMode.LUMA_SRGB_LINEAR -> invertPdfLumaRgb(
            red, green, blue, 0.2126f, 0.7152f, 0.0722f,
        )
        PdfReverseColorMode.LUMA_SYMMETRIC -> invertPdfLumaRgb(
            red, green, blue, 0.3333f, 0.3334f, 0.3333f,
        )
    }
}

private fun packPdfRgb(red: Int, green: Int, blue: Int): Int =
    (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)

/** Returns R, G, B bytes after Okular's cylindrical-HSL lightness inversion. */
fun invertPdfLightnessPixel(red: Int, green: Int, blue: Int): IntArray {
    // HSL lightness is L = m + C / 2. Keeping hue and chroma fixed means the
    // inverted common component is m' = 255 - C - m; no HSL conversion is
    // necessary once the hue sector (max/min) has been established.
    val m = minOf(red, green, blue)
    val r = red - m
    val g = green - m
    val b = blue - m
    val chroma = maxOf(r, g, b)
    val invertedCommon = 255 - chroma - m
    return intArrayOf(r + invertedCommon, g + invertedCommon, b + invertedCommon)
}

private fun invertPdfLumaRgb(
    red: Int,
    green: Int,
    blue: Int,
    redLuma: Float,
    greenLuma: Float,
    blueLuma: Float,
): Int {
    if (red == green && green == blue) {
        val inverse = 255 - red
        return packPdfRgb(inverse, inverse, inverse)
    }

    val y = red * redLuma + green * greenLuma + blue * blueLuma
    val inverseY = 255f - y
    val common = minOf(red, green, blue)
    val r = red - common
    val g = green - common
    val b = blue - common

    val fullChromaY = when {
        r >= b && b >= g -> 255f * redLuma + 255f * blueLuma * b / r
        r >= g && g >= b -> 255f * redLuma + 255f * greenLuma * g / r
        g >= r && r >= b -> 255f * greenLuma + 255f * redLuma * r / g
        g >= b && b >= r -> 255f * greenLuma + 255f * blueLuma * b / g
        b >= g && g >= r -> 255f * blueLuma + 255f * greenLuma * g / b
        else -> 255f * blueLuma + 255f * redLuma * r / b
    }

    val maxChroma = if (y >= fullChromaY) {
        inverseY / (255f - fullChromaY)
    } else {
        y / fullChromaY
    }
    val inverseMaxChroma = if (inverseY >= fullChromaY) {
        y / (255f - fullChromaY)
    } else {
        inverseY / fullChromaY
    }
    val scale = inverseMaxChroma / maxChroma
    val rInverted = r * scale
    val gInverted = g * scale
    val bInverted = b * scale
    val invertedCommon = inverseY - (redLuma * rInverted + greenLuma * gInverted + blueLuma * bInverted)
    return packPdfRgb(
        (rInverted + invertedCommon + 0.5f).toInt(),
        (gInverted + invertedCommon + 0.5f).toInt(),
        (bInverted + invertedCommon + 0.5f).toInt(),
    )
}

/**
 * Inverts luma while retaining hue and saturation using Okular's bicone-HCY
 * algorithm stretched to cylindrical HSY. Coefficients must sum to one.
 */
fun invertPdfLumaPixel(
    red: Int,
    green: Int,
    blue: Int,
    redLuma: Float,
    greenLuma: Float,
    blueLuma: Float,
): IntArray {
    if (red == green && green == blue) {
        val inverse = 255 - red
        return intArrayOf(inverse, inverse, inverse)
    }

    val y = red * redLuma + green * greenLuma + blue * blueLuma
    val inverseY = 255f - y
    val common = minOf(red, green, blue)
    val r = red - common
    val g = green - common
    val b = blue - common

    // Luma at the outer corner of this pixel's hue triangle. This is the
    // six-sector piecewise interpolation used by Okular's page painter.
    val fullChromaY = when {
        r >= b && b >= g -> 255f * redLuma + 255f * blueLuma * b / r
        r >= g && g >= b -> 255f * redLuma + 255f * greenLuma * g / r
        g >= r && r >= b -> 255f * greenLuma + 255f * redLuma * r / g
        g >= b && b >= r -> 255f * greenLuma + 255f * blueLuma * b / g
        b >= g && g >= r -> 255f * blueLuma + 255f * greenLuma * g / b
        else -> 255f * blueLuma + 255f * redLuma * r / b
    }

    val maxChroma = if (y >= fullChromaY) {
        inverseY / (255f - fullChromaY)
    } else {
        y / fullChromaY
    }
    val inverseMaxChroma = if (inverseY >= fullChromaY) {
        y / (255f - fullChromaY)
    } else {
        inverseY / fullChromaY
    }
    val scale = inverseMaxChroma / maxChroma
    val rInverted = r * scale
    val gInverted = g * scale
    val bInverted = b * scale
    val invertedCommon = inverseY - (redLuma * rInverted + greenLuma * gInverted + blueLuma * bInverted)
    return intArrayOf(
        (rInverted + invertedCommon + 0.5f).toInt(),
        (gInverted + invertedCommon + 0.5f).toInt(),
        (bInverted + invertedCommon + 0.5f).toInt(),
    )
}

/** The exact coefficient pair used by Okular's two luma modes. */
fun PdfReverseColorMode.lumaCoefficients(): FloatArray? = when (this) {
    PdfReverseColorMode.LUMA_SRGB_LINEAR -> floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    PdfReverseColorMode.LUMA_SYMMETRIC -> floatArrayOf(0.3333f, 0.3334f, 0.3333f)
    else -> null
}
