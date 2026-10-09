package com.aryan.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.ui.AppIcon
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The in-app app icon has to belong to the app's theme, not to the launcher.
 *
 * It used to be `R.mipmap.ic_launcher`: a blue-on-white raster baked at five
 * densities, which ignores light/dark and every dynamic or custom seed colour
 * the user has picked. It is now the launcher icon's own fan artwork with
 * every gradient stop repainted in the theme, so the two things that can
 * silently go wrong are that the shipped colours come back (the repaint is
 * dropped), and that the fan stops filling the plate it is drawn on (the
 * cropped viewport is lost and it collapses to a speck).
 */
@RunWith(AndroidJUnit4::class)
class AppIconTest {

    @get:Rule val composeTestRule = createComposeRule()

    // Two primaries far apart in hue, so "follows the theme" is observable
    // in the painted pixels rather than in the resolved scheme.
    private val lightPrimary = Color(0xFFE91E63)   // pink
    private val darkPrimary = Color(0xFF3F51B5)    // indigo
    private val lightPlate = Color(0xFFE1E4D5)
    private val darkPlate = Color(0xFF44483D)

    @Test
    fun theFanAndThePlateAreBothTakenFromTheTheme() {
        val scheme = mutableStateOf(
            lightColorScheme(primary = lightPrimary, surfaceVariant = lightPlate)
        )
        composeTestRule.setContent {
            MaterialTheme(scheme.value) {
                Box {
                    AppIcon(contentDescription = "App Icon", size = 96.dp)
                }
            }
        }
        val light = paintedColours()
        composeTestRule.runOnUiThread {
            scheme.value = darkColorScheme(primary = darkPrimary, surfaceVariant = darkPlate)
        }
        composeTestRule.waitForIdle()
        val dark = paintedColours()

        // Asserted on rendered pixels rather than on the resolved colours, because the
        // failure this guards against is in what gets painted: an unrepainted fan still
        // reports the right scheme. Each theme's plate must be there...
        assertThat(light).contains(lightPlate.toArgb())
        assertThat(dark).contains(darkPlate.toArgb())

        // ...and the fan must be painted in that theme's own hue. Every saturated pixel
        // is held to the stronger of the two tests below rather than to a fixed window:
        // anti-aliased blends of the pale bands into the plate are ill-conditioned (their
        // hue can sit ~20 degrees off the theme's, while a shipped blue on this pink
        // primary would sit ~130 off), so what is asserted is that each pixel belongs to
        // its own theme, and that the fan as a whole is squarely in that hue.
        assertFanFollows(light, lightPrimary, darkPrimary)
        assertFanFollows(dark, darkPrimary, lightPrimary)
    }

    /**
     * The painted fan belongs to [primary] rather than to [other]: every saturated
     * pixel is nearer [primary]'s hue, and together they sit on it.
     */
    private fun assertFanFollows(
        painted: Set<Int>,
        primary: Color,
        other: Color,
    ) {
        val hues = saturatedHues(painted)
        assertThat(hues.size).isGreaterThan(0)
        for (hue in hues) {
            assertThat(hueDistance(hue, hueOf(primary)))
                .isLessThan(hueDistance(hue, hueOf(other)))
        }
        assertThat(hueDistance(hues.average().toFloat(), hueOf(primary)))
            .isLessThan(MEAN_HUE_TOLERANCE)
    }

    @Test
    fun theFanFillsThePlateProportionallyAtTheSizeTheAvatarUses() {
        // 32dp in a circle is the tightest slot in the app, and the one that broke
        // before the viewport was cropped: sized against the launcher's 108dp canvas
        // the fan draws at 54/108 of the slot and reads as a 16x10dp speck. Measured
        // at the real slot rather than a large one, because that is the size at which
        // the crop and the fraction have to hold.
        val plate = 32.dp
        composeTestRule.setContent {
            MaterialTheme(lightColorScheme(primary = lightPrimary, surfaceVariant = lightPlate)) {
                Box {
                    AppIcon(
                        contentDescription = "App Icon",
                        size = plate,
                        shape = CircleShape,
                    )
                }
            }
        }

        val plateBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val fanBounds = composeTestRule
            .onNodeWithContentDescription("App Icon", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertThat(fanBounds.width).isGreaterThan(0)
        // most of the plate, so the fan reads as an icon rather than as a speck...
        assertThat(fanBounds.width).isGreaterThan(plateBounds.width / 2)
        // ...and all of it, with a margin, so it does not crowd or overflow the plate
        assertThat(fanBounds.width).isLessThan(plateBounds.width)
        // 54 : 32.8 -- the fan is wider than tall, and its own proportions decide the height
        assertThat(fanBounds.height.toFloat() / fanBounds.width).isWithin(0.02f).of(0.607f)
    }

    @Test
    fun theFanFillsTheCanvasItIsDrawnIn() {
        // The fan's placement is a Compose transform, and the one way to get it
        // wrong is silent: pivot the scale on the middle of the canvas instead
        // of the origin and the fan slides off the plate and is clipped -- fine
        // in a diff, broken on screen. Measured from painted pixels against the
        // canvas the composable laid out, so a transform that misplaces the
        // artwork fails here even though the canvas itself is still the right
        // size and shape.
        val plate = 96.dp
        composeTestRule.setContent {
            MaterialTheme(lightColorScheme(primary = lightPrimary, surfaceVariant = lightPlate)) {
                Box {
                    AppIcon(contentDescription = "App Icon", size = plate)
                }
            }
        }
        val canvas = composeTestRule
            .onNodeWithContentDescription("App Icon", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        // Every pixel inside the canvas that is not (near enough) the plate is fan ink.
        // The scan stays inside the canvas because the plate's own rounded corners are
        // transparent, not plate-coloured, and would otherwise read as ink.
        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (x in canvas.left.toInt() until canvas.right.toInt()) {
            for (y in canvas.top.toInt() until canvas.bottom.toInt()) {
                val ink = pixels[x, y]
                val distance = abs(ink.red - lightPlate.red) +
                    abs(ink.green - lightPlate.green) +
                    abs(ink.blue - lightPlate.blue)
                if (distance > INK_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        assertThat(right).isGreaterThan(left)
        // the fan spans its canvas, edge to edge, rather than floating inside it
        assertThat((right - left).toFloat() / canvas.width).isAtLeast(0.9f)
        assertThat(left.toFloat()).isAtMost(canvas.left + canvas.width * 0.05f)
        assertThat(right.toFloat()).isAtLeast(canvas.right - canvas.width * 0.05f)
        // ...and it is centred on it, which is what a misplaced transform loses first
        assertThat((left + right) / 2f).isWithin(canvas.width * 0.02f)
            .of((canvas.left + canvas.right) / 2f)
        // ...at the artwork's own proportions
        assertThat((bottom - top).toFloat() / (right - left)).isWithin(0.03f).of(0.607f)
    }

    /** Every colour actually painted, so anti-aliased edges do not make this brittle. */
    private fun paintedColours(): Set<Int> {
        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()
        return buildSet {
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) add(pixels[x, y].toArgb())
            }
        }
    }

    /**
     * The HSL hue of every painted colour saturated enough to be fan rather than
     * plate or an anti-aliased blend between them.
     */
    private fun saturatedHues(painted: Set<Int>): Set<Float> = painted
        .map { Color(it) }
        .filter { it.hueAndSaturation().second > 0.4f }
        .map { it.hueAndSaturation().first }
        .toSet()

    /** The HSL hue of a colour, in degrees. */
    private fun hueOf(color: Color): Float = color.hueAndSaturation().first

    /** Hue is circular: 359 degrees and 1 degree are two degrees apart. */
    private fun hueDistance(a: Float, b: Float): Float {
        val direct = abs(a - b)
        return min(direct, 360f - direct)
    }

    /**
     * The HSL hue in degrees and saturation of a colour. A copy of
     * [com.aryan.reader.shared.ui.hueAndSaturation], which is `internal` to the
     * shared module and therefore out of reach from here. It has to stay a copy
     * -- saturation clamped included -- or the hues asserted on below drift from
     * the ones the icon is actually painted in.
     */
    private fun Color.hueAndSaturation(): Pair<Float, Float> {
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
        return ((hue + 360f) % 360f) to saturation.coerceIn(0f, 1f)
    }

    private companion object {
        /** How far the fan's hues may sit from its theme primary on average; the
         * per-pixel check is "nearer this theme than the other one", which leaves
         * room for anti-aliased blends, and this pins the bulk to the theme. */
        const val MEAN_HUE_TOLERANCE = 5f
        /** How far a pixel may sit from the plate before it counts as fan ink. */
        const val INK_THRESHOLD = 0.08f
    }
}
