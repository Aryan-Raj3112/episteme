package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.aryan.reader.shared.pdf.HighlightNoteBadgeBounds

/**
 * Material "sticky_note_2", the glyph that marks a highlight carrying a note.
 *
 * Kept as path data in shared code, not only as an Android drawable, because the marking surface is
 * a canvas on PDF, an SVG inside an EPUB WebView and a Compose draw pass on the native EPUB readers.
 * One source keeps the three looking the same.
 */
const val SHARED_STICKY_NOTE_2_PATH_DATA: String =
    "M5,19h9V14h5V5H5V19Zm0,2q-0.83,0-1.41-0.59T3,19V5Q3,4.17 3.59,3.59T5,3H19q0.83,0 " +
        "1.41,0.59T21,5V15l-6,6H5Zm2-7V12h5v2H7Zm0-4V8H17v2H7ZM5,19V14V5V19Z"

/** The glyph's own coordinate space; [SHARED_STICKY_NOTE_2_PATH_DATA] is authored in it. */
const val SHARED_STICKY_NOTE_2_VIEWPORT: Float = 24f

private val sharedStickyNoteBadgePath: Path by lazy {
    PathParser().parsePathString(SHARED_STICKY_NOTE_2_PATH_DATA).toPath()
}

/**
 * Paints the note mark for a highlight that has one.
 *
 * The glyph is drawn in the highlight's own colour, at full alpha: the fill underneath is a
 * translucent tint of that colour, so the mark reads as a denser piece of the highlight it belongs
 * to rather than as an unrelated badge, and a recolour moves both at once.
 */
fun DrawScope.drawHighlightNoteBadge(bounds: HighlightNoteBadgeBounds, color: Color) {
    val size = bounds.width
    if (!size.isFinite() || size <= 0f) return
    val factor = size / SHARED_STICKY_NOTE_2_VIEWPORT
    translate(bounds.left, bounds.top) {
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(sharedStickyNoteBadgePath, color)
        }
    }
}
