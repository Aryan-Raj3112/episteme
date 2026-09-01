package com.aryan.reader.paginatedreader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aryan.reader.shared.reader.paintOnlyColorOverlayText
import com.aryan.reader.shared.reader.withoutForegroundColorSpans
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual Android text renderer, rather than only checking the
 * transformed annotations. A selection rectangle around the acute mark would
 * also include the base glyph; this test proves that the character-level
 * overlay leaves the lower half of the base glyph black.
 */
@RunWith(AndroidJUnit4::class)
class PaintOnlyColorRenderingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun combiningMarkOverlayDoesNotRecolorBaseGlyph() {
        lateinit var baseLayout: TextLayoutResult
        val source = buildAnnotatedString {
            append("a\u0301")
            addStyle(SpanStyle(color = Color.Red), 1, 2)
        }
        val style = TextStyle(fontSize = 64.sp, color = Color.Black)
        val shapingText = source.withoutForegroundColorSpans()
        val overlayText = source.paintOnlyColorOverlayText(baseColor = Color.Black)

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .testTag(TEST_TAG)
                    .requiredWidth(180.dp)
                    .requiredHeight(110.dp)
                    .background(Color.White)
            ) {
                Text(
                    text = shapingText,
                    style = style,
                    onTextLayout = { baseLayout = it }
                )
                if (overlayText.isNotEmpty()) {
                    Text(
                        text = overlayText,
                        modifier = Modifier.matchParentSize(),
                        style = style.copy(color = Color.Transparent)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val layout = baseLayout
        val baseBox = layout.getBoundingBox(0)
        val pixels = composeTestRule
            .onNodeWithTag(TEST_TAG)
            .captureToImage()
            .toPixelMap()
        val redPixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val argb = pixels[x, y].toArgb()
                val red = android.graphics.Color.red(argb)
                val green = android.graphics.Color.green(argb)
                val blue = android.graphics.Color.blue(argb)
                if (red > 180 && green < 100 && blue < 100) {
                    redPixels += x to y
                }
            }
        }

        assertThat(redPixels).isNotEmpty()
        val lowerBaseStart = ((baseBox.top + baseBox.bottom) / 2f).roundToInt()
        val redPixelsOnBase = redPixels.count { (_, y) -> y >= lowerBaseStart }
        assertThat(redPixelsOnBase).isEqualTo(0)
    }

    private companion object {
        const val TEST_TAG = "paintOnlyColorRendering"
    }
}
