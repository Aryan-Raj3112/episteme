package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.reader.SharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderFormatSliderModelsTest {
    @Test
    fun androidSliderSpecsKeepTheLegacyRangesAndSteps() {
        assertEquals(0.5f, AndroidEpubFormatSliders.fontSize.minimum)
        assertEquals(3f, AndroidEpubFormatSliders.fontSize.maximum)
        assertEquals(1f, AndroidEpubFormatSliders.lineHeight.minimum)
        assertEquals(3f, AndroidEpubFormatSliders.lineHeight.maximum)
        assertEquals(0f, AndroidEpubFormatSliders.paragraphGap.minimum)
        assertEquals(3f, AndroidEpubFormatSliders.paragraphGap.maximum)
        assertEquals(0.5f, AndroidEpubFormatSliders.imageSize.minimum)
        assertEquals(2f, AndroidEpubFormatSliders.imageSize.maximum)
        assertEquals(0.1f, AndroidEpubFormatSliders.fontSize.step)
        assertEquals(0.1f, AndroidEpubFormatSliders.verticalMargin.step)
    }

    @Test
    fun sliderSteppingSnapsLikeAndroidAndClampsAtBothEnds() {
        assertEquals(0.5f, AndroidEpubFormatSliders.fontSize.step(0.51f, -1))
        assertEquals(0.6f, AndroidEpubFormatSliders.fontSize.step(0.51f, 1))
        assertEquals(3f, AndroidEpubFormatSliders.fontSize.step(2.96f, 1))
        assertEquals(1.2f, AndroidEpubFormatSliders.lineHeight.snap(1.24f))
        assertEquals(0f, AndroidEpubFormatSliders.paragraphGap.snap(-10f))
        assertEquals(1f, AndroidEpubFormatSliders.imageSize.clamp(Float.NaN))
    }

    @Test
    fun sliderPositionsRoundTripToSharedEffectiveSettings() {
        val positions = AndroidEpubFormatSliderValues(
            fontSize = 1.5f,
            lineHeight = 1.6f,
            paragraphGap = 0.7f,
            imageSize = 1.8f,
            horizontalMargin = 0.5f,
            verticalMargin = 2f,
        )

        val settings = positions.toReaderSettings()
        val restored = settings.toAndroidEpubFormatSliderValues()

        assertEquals(27, settings.fontSize)
        assertEquals(2.32f, settings.lineSpacing, 0.0001f)
        assertEquals(8, settings.resolvedHorizontalMargin)
        assertEquals(32, settings.resolvedVerticalMargin)
        assertEquals(positions.fontSize, restored.fontSize, 0.0001f)
        assertEquals(positions.lineHeight, restored.lineHeight, 0.0001f)
        assertEquals(positions.paragraphGap, restored.paragraphGap, 0.0001f)
        assertEquals(positions.imageSize, restored.imageSize, 0.0001f)
        assertEquals(positions.horizontalMargin, restored.horizontalMargin, 0.0001f)
        assertEquals(positions.verticalMargin, restored.verticalMargin, 0.0001f)
    }

    @Test
    fun defaultMarginsMatchTheAndroidSixteenDpBenchmark() {
        // Android renders a 1.0x margin as 16 on both surfaces (native 16.dp in
        // PaginatedReader, 16px in epub_reader.js), so fresh shared installs
        // must resolve to 16, not an inflated value.
        assertEquals(16, ReaderSettings().resolvedHorizontalMargin)
        assertEquals(16, ReaderSettings().resolvedVerticalMargin)
        assertEquals(16f, AndroidEpubFormatBenchmark.baseMarginPx)

        val atDefaultPosition = AndroidEpubFormatSliderValues().toReaderSettings()
        assertEquals(16, atDefaultPosition.resolvedHorizontalMargin)
        assertEquals(16, atDefaultPosition.resolvedVerticalMargin)
        val restored = atDefaultPosition.toAndroidEpubFormatSliderValues()
        assertEquals(1f, restored.horizontalMargin, 0.0001f)
        assertEquals(1f, restored.verticalMargin, 0.0001f)
    }

    @Test
    fun spreadGapSliderUsesAbsoluteDpSteps() {
        assertEquals(0f, AndroidEpubFormatSliders.spreadGap.minimum)
        assertEquals(48f, AndroidEpubFormatSliders.spreadGap.maximum)
        assertEquals(2f, AndroidEpubFormatSliders.spreadGap.step)
        assertEquals(20f, AndroidEpubFormatSliders.spreadGap.default)
        assertEquals(22f, AndroidEpubFormatSliders.spreadGap.step(21f, 0))
        assertEquals(24f, AndroidEpubFormatSliders.spreadGap.step(21f, 1))
        assertEquals(20f, AndroidEpubFormatSliders.spreadGap.snap(20.4f))
        assertEquals(48f, AndroidEpubFormatSliders.spreadGap.snap(500f))
    }

    @Test
    fun spreadGapRoundTripsThroughSliderValues() {
        assertEquals(20f, ReaderSettings().pageSpreadGutterDp)
        val settings = AndroidEpubFormatSliderValues(spreadGapDp = 32f).toReaderSettings()
        assertEquals(32f, settings.pageSpreadGutterDp)
        assertEquals(32f, settings.toAndroidEpubFormatSliderValues().spreadGapDp, 0.0001f)
        val updated = ReaderSettings()
            .withAndroidEpubFormatSliderValue(AndroidEpubFormatSlider.SPREAD_GAP, 12f)
        assertEquals(12f, updated.pageSpreadGutterDp)
    }

    @Test
    fun migrationClampsOnlyFormatFieldsAndPreservesReaderPolicy() {
        val original = ReaderSettings(
            fontSize = 100,
            fontWeight = 1500,
            letterSpacing = 2f,
            lineSpacing = 0.1f,
            margin = -20,
            horizontalMargin = -10,
            verticalMargin = 500,
            paragraphSpacing = -1f,
            imageScale = 4f,
            readingMode = ReaderReadingMode.PAGINATED,
            textAlign = SharedReaderTextAlign.JUSTIFY,
            themeId = "sepia",
        )

        val migrated = original.migrateAndroidEpubFormatSettings()

        assertEquals(54, migrated.fontSize)
        assertEquals(1000, migrated.fontWeight)
        assertEquals(0.5f, migrated.letterSpacing)
        assertEquals(1.45f, migrated.lineSpacing, 0.0001f)
        assertEquals(0, migrated.resolvedHorizontalMargin)
        assertEquals(48, migrated.resolvedVerticalMargin)
        assertEquals(48, migrated.margin)
        assertEquals(0f, migrated.paragraphSpacing)
        assertEquals(2f, migrated.imageScale)
        assertEquals(original.readingMode, migrated.readingMode)
        assertEquals(original.textAlign, migrated.textAlign)
        assertEquals(original.themeId, migrated.themeId)
        assertTrue(migrated.horizontalMargin != null)
        assertTrue(migrated.verticalMargin != null)
    }
}
