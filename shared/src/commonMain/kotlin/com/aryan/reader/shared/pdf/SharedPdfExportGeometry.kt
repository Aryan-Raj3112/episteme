package com.aryan.reader.shared.pdf

import kotlin.math.max
import kotlin.math.min

/**
 * PDFium image-object matrix (`FPDFImageObj_SetMatrix`) coefficients.
 *
 * Maps the image unit square into mediabox (y-up) page space:
 * `(0,0) → (e,f)`, `(1,0) → (e+a,f+b)`, `(0,1) → (e+c,f+d)`.
 * PDF sample row 0 is the image top (`y = 1`).
 */
data class SharedPdfImageMatrix(
    val a: Double,
    val b: Double,
    val c: Double,
    val d: Double,
    val e: Double,
    val f: Double,
)

/** Rectangle in mediabox (y-up) points, ordered so left ≤ right and bottom ≤ top. */
data class SharedPdfMediaboxRect(
    val left: Float,
    val bottom: Float,
    val right: Float,
    val top: Float,
)

/**
 * Converts `FPDFPage_GetRotation`'s quarter-turn code (0..3 clockwise, or -1 on error)
 * into degrees clockwise (0, 90, 180, or 270). Unknown codes map to 0.
 */
fun sharedPdfRotationDegreesFromPdfiumCode(rotationCode: Int): Int =
    when (rotationCode) {
        1 -> 90
        2 -> 180
        3 -> 270
        else -> 0
    }

/**
 * Mediabox size implied by a display (viewer) page size after applying
 * [rotationDegrees] clockwise for viewing. `FPDF_GetPageWidthF/Height` and
 * Android `PdfRenderer.Page` dimensions already include that rotation.
 */
fun sharedPdfMediaboxSize(
    displayWidth: Float,
    displayHeight: Float,
    rotationDegrees: Int,
): Pair<Float, Float> =
    when (normalizeRotationDegrees(rotationDegrees)) {
        90, 270 -> displayHeight to displayWidth
        else -> displayWidth to displayHeight
    }

/**
 * Maps a display-space normalized point (y-down, origin top-left of the page as
 * the viewer shows it after page rotation) into mediabox points (y-up).
 *
 * [displayWidth]/[displayHeight] must be the rotated page size.
 * [rotationDegrees] is the clockwise page rotation shown to the viewer (0/90/180/270).
 *
 * Returns null when the display size is non-positive or rotation is not a right angle.
 */
fun sharedPdfDisplayPointToMediabox(
    xNorm: Float,
    yNorm: Float,
    displayWidth: Float,
    displayHeight: Float,
    rotationDegrees: Int,
): Pair<Float, Float>? {
    if (displayWidth <= 0f || displayHeight <= 0f) return null
    val dx = xNorm.coerceIn(0f, 1f) * displayWidth
    val dy = yNorm.coerceIn(0f, 1f) * displayHeight
    val (mediaboxWidth, mediaboxHeight) = sharedPdfMediaboxSize(displayWidth, displayHeight, rotationDegrees)
    return when (normalizeRotationDegrees(rotationDegrees)) {
        0 -> dx to (displayHeight - dy)
        90 -> dy to dx
        180 -> (displayWidth - dx) to dy
        270 -> (mediaboxWidth - dy) to (mediaboxHeight - dx)
        else -> null
    }
}

/**
 * Maps a display-space normalized rect (y-down) into an ordered mediabox rect.
 * Returns null for non-positive display sizes, invalid rotations, or empty rects.
 */
fun sharedPdfDisplayRectToMediabox(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    displayWidth: Float,
    displayHeight: Float,
    rotationDegrees: Int,
): SharedPdfMediaboxRect? {
    if (displayWidth <= 0f || displayHeight <= 0f) return null
    if (normalizeRotationDegrees(rotationDegrees) !in intArrayOf(0, 90, 180, 270)) return null
    val displayLeft = min(left, right).coerceIn(0f, 1f)
    val displayRight = max(left, right).coerceIn(0f, 1f)
    val displayTop = min(top, bottom).coerceIn(0f, 1f)
    val displayBottom = max(top, bottom).coerceIn(0f, 1f)
    if (displayRight <= displayLeft || displayBottom <= displayTop) return null

    val corners = listOf(
        sharedPdfDisplayPointToMediabox(displayLeft, displayTop, displayWidth, displayHeight, rotationDegrees),
        sharedPdfDisplayPointToMediabox(displayRight, displayTop, displayWidth, displayHeight, rotationDegrees),
        sharedPdfDisplayPointToMediabox(displayLeft, displayBottom, displayWidth, displayHeight, rotationDegrees),
        sharedPdfDisplayPointToMediabox(displayRight, displayBottom, displayWidth, displayHeight, rotationDegrees),
    ).mapNotNull { it }
    if (corners.isEmpty()) return null

    val xs = corners.map { it.first }
    val ys = corners.map { it.second }
    val rect = SharedPdfMediaboxRect(
        left = xs.min(),
        bottom = ys.min(),
        right = xs.max(),
        top = ys.max(),
    )
    return rect.takeIf { it.right > it.left && it.top > it.bottom }
}

/**
 * Builds the image matrix that places a top-down display-space bitmap into
 * mediabox coordinates so that, after the viewer applies [rotationDegrees]
 * clockwise page rotation, the content appears upright at the same display
 * location as the input rect.
 *
 * [left]/[top]/[right]/[bottom] are display-normalized (y-down) bounds of the
 * bitmap before transparent-margin crop (or the tight crop bounds). Returns
 * null when the rect is degenerate or the display size / rotation is invalid.
 */
fun sharedPdfRasterImageMatrix(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    displayWidth: Float,
    displayHeight: Float,
    rotationDegrees: Int,
): SharedPdfImageMatrix? {
    if (displayWidth <= 0f || displayHeight <= 0f) return null
    val rotation = normalizeRotationDegrees(rotationDegrees)
    if (rotation !in intArrayOf(0, 90, 180, 270)) return null

    val dx0 = min(left, right).coerceIn(0f, 1f) * displayWidth
    val dx1 = max(left, right).coerceIn(0f, 1f) * displayWidth
    val dy0 = min(top, bottom).coerceIn(0f, 1f) * displayHeight
    val dy1 = max(top, bottom).coerceIn(0f, 1f) * displayHeight
    val width = dx1 - dx0
    val height = dy1 - dy0
    if (width <= 0f || height <= 0f) return null

    val (mediaboxWidth, mediaboxHeight) = sharedPdfMediaboxSize(displayWidth, displayHeight, rotation)
    // Display y-up coords of the rect (origin bottom-left of the viewed page).
    val displayLeft = dx0
    val displayBottom = displayHeight - dy1

    return when (rotation) {
        0 -> SharedPdfImageMatrix(
            a = width.toDouble(),
            b = 0.0,
            c = 0.0,
            d = height.toDouble(),
            e = displayLeft.toDouble(),
            f = displayBottom.toDouble(),
        )
        // Pre-rotate image -90° (CW page rotation is undone by content rotated CCW).
        90 -> SharedPdfImageMatrix(
            a = 0.0,
            b = width.toDouble(),
            c = -height.toDouble(),
            d = 0.0,
            e = (mediaboxWidth - displayBottom).toDouble(),
            f = displayLeft.toDouble(),
        )
        180 -> SharedPdfImageMatrix(
            a = -width.toDouble(),
            b = 0.0,
            c = 0.0,
            d = -height.toDouble(),
            e = (mediaboxWidth - displayLeft).toDouble(),
            f = (displayHeight - displayBottom).toDouble(),
        )
        else -> SharedPdfImageMatrix(
            a = 0.0,
            b = -width.toDouble(),
            c = height.toDouble(),
            d = 0.0,
            e = displayBottom.toDouble(),
            f = (mediaboxHeight - displayLeft).toDouble(),
        )
    }
}

private fun normalizeRotationDegrees(rotationDegrees: Int): Int {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    return when (normalized) {
        0, 90, 180, 270 -> normalized
        else -> -1
    }
}
