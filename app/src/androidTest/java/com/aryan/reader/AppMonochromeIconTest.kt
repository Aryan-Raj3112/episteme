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
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-app app icon has to belong to the app's theme, not to the launcher.
 *
 * It used to be `R.mipmap.ic_launcher`: a blue-on-white raster baked at five densities, which
 * ignores light/dark and every dynamic or custom seed colour the user has picked. It is now the
 * monochrome mark tinted from `MaterialTheme`, so the two things that can silently go wrong are
 * that the shipped colour comes back (the tint is dropped), and that the mark stops filling the
 * plate it is drawn on (the cropped viewport is lost and it collapses to a speck).
 */
@RunWith(AndroidJUnit4::class)
class AppMonochromeIconTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val lightMark = Color(0xFF4C662B)
    private val lightPlate = Color(0xFFE1E4D5)
    private val darkMark = Color(0xFFB1D18A)
    private val darkPlate = Color(0xFF44483D)

    @Test
    fun theMarkAndThePlateAreBothTakenFromTheTheme() {
        val scheme = mutableStateOf(
            lightColorScheme(primary = lightMark, surfaceVariant = lightPlate)
        )
        composeTestRule.setContent {
            MaterialTheme(scheme.value) {
                Box {
                    AppMonochromeIcon(contentDescription = "App Icon", size = 96.dp)
                }
            }
        }
        val light = paintedColours()
        composeTestRule.runOnUiThread {
            scheme.value = darkColorScheme(primary = darkMark, surfaceVariant = darkPlate)
        }
        composeTestRule.waitForIdle()
        val dark = paintedColours()

        // Asserted on rendered pixels rather than on the resolved colours, because the failure
        // this guards against is in what gets painted: an untinted vector still reports the
        // right scheme. Each theme's own colours must both be there -- the mark and its plate.
        assertThat(light).contains(lightMark.toArgb())
        assertThat(light).contains(lightPlate.toArgb())
        assertThat(dark).contains(darkMark.toArgb())
        assertThat(dark).contains(darkPlate.toArgb())

        // ...and neither theme leaks the other's, so this is a tint that follows the theme
        // rather than one hard-coded colour that happens to be legible on both
        assertThat(light).doesNotContain(darkMark.toArgb())
        assertThat(dark).doesNotContain(lightMark.toArgb())
    }

    @Test
    fun theMarkFillsThePlateProportionallyAtTheSizeTheAvatarUses() {
        // 32dp in a circle is the tightest slot in the app, and the one that broke before: sized
        // against the launcher's 108dp canvas the glyph draws at 54/108 of the slot and reads as
        // a 16x10dp speck. Measured at the real slot rather than a large one, because that is
        // the size at which the crop and the fraction have to hold.
        val plate = 32.dp
        composeTestRule.setContent {
            MaterialTheme(lightColorScheme(primary = lightMark, surfaceVariant = lightPlate)) {
                Box {
                    AppMonochromeIcon(
                        contentDescription = "App Icon",
                        size = plate,
                        shape = CircleShape,
                    )
                }
            }
        }

        val plateBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val markBounds = composeTestRule
            .onNodeWithContentDescription("App Icon", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        assertThat(markBounds.width).isGreaterThan(0)
        // most of the plate, so the mark reads as an icon rather than as a speck...
        assertThat(markBounds.width).isGreaterThan(plateBounds.width / 2)
        // ...and all of it, with a margin, so it does not crowd or overflow the plate
        assertThat(markBounds.width).isLessThan(plateBounds.width)
        // 54 : 32.8 -- the glyph is wider than tall, and its own proportions decide the height
        assertThat(markBounds.height.toFloat() / markBounds.width).isWithin(0.02f).of(0.607f)
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
}
