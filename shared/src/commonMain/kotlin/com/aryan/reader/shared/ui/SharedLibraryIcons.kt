package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Library glyphs that the vendored Material Symbols set does not carry.
 *
 * Same construction as `androidx.compose.material.icons.filled` in this module (24dp box, 960-unit
 * grid, Symbols geometry) so it is indistinguishable from the icons next to it, but kept out of that
 * package because it is an app-level glyph rather than a copy of Google's file.
 */
object SharedLibraryIcons {

    /**
     * A bulleted list: three dots against three rules.
     *
     * The bars reuse the exact rows and extents of `FormatListNumbered` (x 360..840, rows 200/440/680),
     * so switching between the two never shifts the icon's optical weight.
     */
    val ListBulleted: ImageVector by lazy {
        ImageVector.Builder(
            name = "ListBulleted",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Bullets, centred on the same column the numbered glyph uses for its digits.
                moveTo(200f, 180f)
                arcToRelative(60f, 60f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 120f)
                arcToRelative(60f, 60f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -120f)
                close()
                moveTo(200f, 420f)
                arcToRelative(60f, 60f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 120f)
                arcToRelative(60f, 60f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -120f)
                close()
                moveTo(200f, 660f)
                arcToRelative(60f, 60f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = 120f)
                arcToRelative(60f, 60f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 0f, dy1 = -120f)
                close()
                // Rules.
                moveTo(360f, 760f)
                lineTo(360f, 680f)
                lineTo(840f, 680f)
                lineTo(840f, 760f)
                close()
                moveTo(360f, 520f)
                lineTo(360f, 440f)
                lineTo(840f, 440f)
                lineTo(840f, 520f)
                close()
                moveTo(360f, 280f)
                lineTo(360f, 200f)
                lineTo(840f, 200f)
                lineTo(840f, 280f)
                close()
            }
        }.build()
    }

    /**
     * A 2x2 grid of rounded tiles - the "grid view" counterpart to [ListBulleted].
     *
     * Same tile geometry as the Symbols `grid_view` (four 300-unit tiles on the 960 grid, 64-unit
     * corners), so the two view-mode glyphs sit at the same optical weight.
     */
    val GridView: ImageVector by lazy {
        ImageVector.Builder(
            name = "GridView",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                roundedTile(left = 120f, top = 120f)
                roundedTile(left = 540f, top = 120f)
                roundedTile(left = 120f, top = 540f)
                roundedTile(left = 540f, top = 540f)
            }
        }.build()
    }

    private const val TileSize = 300f
    private const val TileCorner = 64f

    private fun androidx.compose.ui.graphics.vector.PathBuilder.roundedTile(left: Float, top: Float) {
        val right = left + TileSize
        val bottom = top + TileSize
        val c = TileCorner
        moveTo(left + c, top)
        lineTo(right - c, top)
        arcTo(c, c, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + c)
        lineTo(right, bottom - c)
        arcTo(c, c, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right - c, y1 = bottom)
        lineTo(left + c, bottom)
        arcTo(c, c, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left, y1 = bottom - c)
        lineTo(left, top + c)
        arcTo(c, c, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left + c, y1 = top)
        close()
    }
}
