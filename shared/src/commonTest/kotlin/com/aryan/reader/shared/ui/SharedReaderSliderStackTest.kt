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

    /**
     * The narration card must clear everything under it, not just the toolbar.
     *
     * It used to share the read-aloud overlay's lift — inset + 45.dp + 16.dp — which reserves for
     * the toolbar alone. Against the jump bar's `[45, 85]` that put the card's own lower ~24.dp
     * underneath it, and the jump bar is declared after the card in both readers, so with no
     * `zIndex` anywhere it simply painted on top. This is the arithmetic that overlap was.
     */
    @Test
    fun epubNarrationCardClearsTheJumpBar() {
        val bottomChrome = sharedMobileEpubBottomChromePadding(20.dp)
        // What the card used to get: 45 + 20 + 16 = 81.dp, against a jump bar occupying 65..105.
        assertEquals(
            81.dp,
            sharedMobileEpubMediaOverlayBottomPadding(
                bottomChromePadding = bottomChrome,
                chromeVisible = true
            )
        )
        // With the jump bar up, the card's bottom edge has to clear its top edge at 105.dp.
        assertEquals(
            105.dp + SharedReaderNarrationBarGap,
            sharedMobileEpubMediaOverlayBottomPadding(
                bottomChromePadding = bottomChrome,
                jumpBarVisible = true,
                chromeVisible = true
            )
        )
    }

    /**
     * And a bottom page info bar, which sits between the toolbar and the card in the bottom column.
     *
     * A page info bar at the *top* is nowhere near the card, so it must not lift it — that is the
     * whole reason the reserve is a parameter rather than a constant.
     */
    @Test
    fun epubNarrationCardClearsABottomPageInfoBarButNotATopOne() {
        val bottomChrome = sharedMobileEpubBottomChromePadding(0.dp)
        assertEquals(
            bottomChrome + 25.dp + SharedReaderNarrationBarGap,
            sharedMobileEpubMediaOverlayBottomPadding(
                bottomChromePadding = bottomChrome,
                pageInfoReserve = 25.dp,
                chromeVisible = true
            )
        )
        // Both at once: toolbar + page info + jump bar + gap.
        assertEquals(
            45.dp + 25.dp + SharedReaderJumpBarHeight + SharedReaderNarrationBarGap,
            sharedMobileEpubMediaOverlayBottomPadding(
                bottomChromePadding = bottomChrome,
                pageInfoReserve = 25.dp,
                jumpBarVisible = true,
                chromeVisible = true
            )
        )
    }

    /**
     * Hidden chrome means a fixed inset, not a stack that is not there.
     *
     * The card is chrome-gated so this is only reached during the exit animation, which is exactly
     * why it has to be a number rather than a dereference of something the reader no longer has.
     */
    @Test
    fun epubNarrationCardFallsBackWhenChromeIsHidden() {
        assertEquals(
            32.dp,
            sharedMobileEpubMediaOverlayBottomPadding(
                bottomChromePadding = sharedMobileEpubBottomChromePadding(20.dp),
                pageInfoReserve = 25.dp,
                jumpBarVisible = true,
                chromeVisible = false
            )
        )
    }
}
