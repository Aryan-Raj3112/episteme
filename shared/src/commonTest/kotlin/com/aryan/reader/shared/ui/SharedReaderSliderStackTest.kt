package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedReaderSliderStackTest {
    @Test
    fun epubSliderSitsAboveJumpBar() {
        val bottomChrome = sharedMobileEpubBottomChromePadding(20.dp)
        assertEquals(65.dp, bottomChrome)

        val jumpBottom = sharedMobileEpubJumpBottomPadding(bottomChrome)
        assertEquals(65.dp, jumpBottom)

        val sliderWithoutJump = sharedMobileEpubSliderBottomPadding(jumpBottom, isJumpVisible = false)
        assertEquals(65.dp, sliderWithoutJump)

        val sliderWithJump = sharedMobileEpubSliderBottomPadding(jumpBottom, isJumpVisible = true)
        assertEquals(105.dp, sliderWithJump)
    }

    @Test
    fun epubJumpClearsPageInfoAndTtsLift() {
        val bottomChrome = sharedMobileEpubBottomChromePadding(0.dp)
        val jumpBottom = sharedMobileEpubJumpBottomPadding(
            bottomChromePadding = bottomChrome,
            pageInfoReserve = 25.dp,
            ttsLift = 68.dp
        )
        assertEquals(45.dp + 25.dp + 68.dp, jumpBottom)

        val sliderBottom = sharedMobileEpubSliderBottomPadding(jumpBottom, isJumpVisible = true)
        assertEquals(jumpBottom + 40.dp, sliderBottom)
    }

    @Test
    fun pdfSliderStacksLikeAndroid() {
        val bottomChrome = sharedMobilePdfBottomChromePadding(20.dp, isSplitPane = false)
        assertEquals(76.dp, bottomChrome)

        assertEquals(
            76.dp,
            sharedMobilePdfSliderBottomPadding(bottomChrome, isJumpVisible = false)
        )
        assertEquals(
            116.dp,
            sharedMobilePdfSliderBottomPadding(bottomChrome, isJumpVisible = true)
        )
    }

    @Test
    fun pdfSplitPaneIgnoresSystemInset() {
        assertEquals(
            56.dp,
            sharedMobilePdfBottomChromePadding(20.dp, isSplitPane = true)
        )
    }
}
