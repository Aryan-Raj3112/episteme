package com.aryan.reader.epubreader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aryan.reader.androidSharedStringResolver
import com.aryan.reader.shared.EpubOverflowMenuSection
import com.aryan.reader.shared.ui.LocalSharedStringResolver
import com.aryan.reader.shared.ui.SharedReaderEpubOverflowCallbacks
import com.aryan.reader.shared.ui.SharedReaderEpubOverflowSection
import com.aryan.reader.shared.ui.SharedReaderEpubOverflowState
import com.aryan.reader.shared.ui.SharedReaderOverflowMenuState
import com.aryan.reader.shared.ui.sharedReaderEpubOverflowLabels
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The EPUB overflow ("more") menu's rows must tint their leading icons.
 *
 * Reported: some icons in the reader's morevert menu were black on a black background in dark mode.
 * The renderer passed `Color.Unspecified` as the icon tint, which `Icon` reads as "do not tint" — it
 * then paints the vector's own fill, and every Material icon vector is black. Rows now take the
 * menu's own leading-icon colour instead, so painting a row with a distinctive scheme shows it.
 */
@RunWith(AndroidJUnit4::class)
class EpubOverflowMenuIconTintTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun overflowRowsTintTheirLeadingIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val iconColor = Color.Red
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSharedStringResolver provides androidSharedStringResolver(context)) {
                // Text stays on `onSurface`, which the menu's leading icons never use — so any red
                // pixel in the capture can only come from a tinted icon.
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        onSurfaceVariant = iconColor,
                        onSurface = Color(0xFF1D1B20),
                    )
                ) {
                    Box(
                        Modifier
                            .width(280.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("OverflowRow")
                    ) {
                        SharedReaderEpubOverflowSection(
                            section = EpubOverflowMenuSection.CUSTOMIZE_TOOLBAR,
                            menuState = SharedReaderOverflowMenuState(),
                            state = SharedReaderEpubOverflowState(),
                            labels = sharedReaderEpubOverflowLabels(),
                            callbacks = SharedReaderEpubOverflowCallbacks(onCustomizeTools = {}),
                        )
                    }
                }
            }
        }

        val bitmap = composeTestRule.onNodeWithTag("OverflowRow").captureToImage().asAndroidBitmap()
        var tintedPixels = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                if (red > 180 && green < 90 && blue < 90) tintedPixels++
            }
        }

        // An untiled 20dp icon is thousands of pixels; the old black fill left none.
        assertThat(tintedPixels).isGreaterThan(20)
    }
}
