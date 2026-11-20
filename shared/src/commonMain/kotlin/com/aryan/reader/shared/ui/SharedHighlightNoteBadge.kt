package com.aryan.reader.shared.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.aryan.reader.shared.pdf.HighlightNoteBadgeBounds

/**
 * The note mark: a page with a folded corner, drawn as a solid plate.
 *
 * Solid, not an outline, because the mark is a fraction of a line tall: at that size the interior
 * strokes of an outlined glyph disappear and what is left is a hairline border. A filled shape keeps
 * its silhouette, which is the only thing that can carry meaning at thirteen pixels.
 *
 * Kept as path data in shared code rather than only as an Android drawable, because the marking
 * surface is a canvas on PDF, an SVG inside an EPUB WebView and a Compose draw pass on the native
 * EPUB readers. One source keeps the three looking the same.
 */
const val SHARED_NOTE_MARK_BODY_PATH_DATA: String =
    "M5,3 H19 A2,2 0 0 1 21,5 V15 L15,21 H5 A2,2 0 0 1 3,19 V5 A2,2 0 0 1 5,3 Z"

/** The folded corner, so the plate reads as a note rather than as a coloured square. */
const val SHARED_NOTE_MARK_FOLD_PATH_DATA: String = "M15,15 L21,15 L15,21 Z"

/** The glyph's own coordinate space; both paths above are authored in it. */
const val SHARED_NOTE_MARK_VIEWPORT: Float = 24f

/**
 * The note mark's colour, deliberately not the highlight's.
 *
 * A self-coloured mark is invisible exactly where it is most needed: on a yellow highlight it is a
 * slightly denser yellow, and at the size this mark has to be — a fraction of a line — the
 * difference is not legible. The marks readers already know from PDF and notes apps are a single
 * warm gold for this reason, so one colour, the same on every highlight, on every surface.
 */
val HIGHLIGHT_NOTE_MARK_COLOR: Color = Color(0xFFFFB400)

/** [HIGHLIGHT_NOTE_MARK_COLOR] as CSS, for the WebView surfaces. */
const val HIGHLIGHT_NOTE_MARK_CSS_COLOR: String = "#FFB400"

/** The folded corner, darker than the plate so the shape reads at a glance. */
val HIGHLIGHT_NOTE_MARK_FOLD_COLOR: Color = Color(0xFF8A5A00)

/** [HIGHLIGHT_NOTE_MARK_FOLD_COLOR] as CSS, for the WebView surfaces. */
const val HIGHLIGHT_NOTE_MARK_CSS_FOLD: String = "#8A5A00"

/**
 * The hairline around the mark.
 *
 * Gold on a gold highlight is contrast by edge, not by hue, and a thin dark outline supplies that
 * edge at any size. It is drawn inside the glyph's own coordinates so it scales with the mark.
 */
val HIGHLIGHT_NOTE_MARK_OUTLINE_COLOR: Color = Color(0x993A2600)

/** [HIGHLIGHT_NOTE_MARK_OUTLINE_COLOR] as CSS, for the WebView surfaces. */
const val HIGHLIGHT_NOTE_MARK_CSS_OUTLINE: String = "rgba(58,38,0,0.6)"

/** Body outline width in [SHARED_NOTE_MARK_VIEWPORT] units, so it scales with the mark. */
const val HIGHLIGHT_NOTE_MARK_OUTLINE_WIDTH: Float = 1.4f

/** Fold outline width, a touch thinner so the corner does not fill in. */
const val HIGHLIGHT_NOTE_MARK_FOLD_OUTLINE_WIDTH: Float = 1.1f

private val sharedNoteMarkBodyPath: Path by lazy {
    PathParser().parsePathString(SHARED_NOTE_MARK_BODY_PATH_DATA).toPath()
}

private val sharedNoteMarkFoldPath: Path by lazy {
    PathParser().parsePathString(SHARED_NOTE_MARK_FOLD_PATH_DATA).toPath()
}

/**
 * Paints the note mark for a highlight that has one.
 *
 * The colour is fixed rather than taken from the highlight: the mark's job is to be noticed next to
 * text it does not belong to, and a colour inherited from the highlight is the one colour it cannot
 * be seen against.
 */
fun DrawScope.drawHighlightNoteBadge(bounds: HighlightNoteBadgeBounds) {
    val size = bounds.width
    if (!size.isFinite() || size <= 0f) return
    val factor = size / SHARED_NOTE_MARK_VIEWPORT
    translate(bounds.left, bounds.top) {
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(sharedNoteMarkBodyPath, HIGHLIGHT_NOTE_MARK_COLOR)
            drawPath(
                path = sharedNoteMarkBodyPath,
                color = HIGHLIGHT_NOTE_MARK_OUTLINE_COLOR,
                style = Stroke(width = HIGHLIGHT_NOTE_MARK_OUTLINE_WIDTH)
            )
            drawPath(sharedNoteMarkFoldPath, HIGHLIGHT_NOTE_MARK_FOLD_COLOR)
            drawPath(
                path = sharedNoteMarkFoldPath,
                color = HIGHLIGHT_NOTE_MARK_OUTLINE_COLOR,
                style = Stroke(width = HIGHLIGHT_NOTE_MARK_FOLD_OUTLINE_WIDTH)
            )
        }
    }
}
