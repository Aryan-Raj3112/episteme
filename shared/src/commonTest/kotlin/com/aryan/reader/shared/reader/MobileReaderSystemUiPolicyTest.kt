package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import com.aryan.reader.shared.SystemUiMode
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileReaderSystemUiPolicyTest {
    @Test
    fun epubDefaultKeepsStatusVisibleButSynchronizesNavigationToChrome() {
        assertEquals(MobileReaderSystemBarsVisibility(true, false), mobileEpubSystemBarsVisibility(SystemUiMode.DEFAULT, false))
        assertEquals(MobileReaderSystemBarsVisibility(true, true), mobileEpubSystemBarsVisibility(SystemUiMode.DEFAULT, true))
    }

    @Test
    fun epubSyncAndHiddenPreserveAndroidBehavior() {
        assertEquals(MobileReaderSystemBarsVisibility(false, false), mobileEpubSystemBarsVisibility(SystemUiMode.SYNC, false))
        assertEquals(MobileReaderSystemBarsVisibility(true, true), mobileEpubSystemBarsVisibility(SystemUiMode.SYNC, true))
        assertEquals(MobileReaderSystemBarsVisibility(false, false), mobileEpubSystemBarsVisibility(SystemUiMode.HIDDEN, true))
    }

    @Test
    fun pdfDefaultShowsBothWhileSyncUsesStandardChromeVisibility() {
        assertEquals(MobileReaderSystemBarsVisibility(true, true), mobilePdfSystemBarsVisibility(SystemUiMode.DEFAULT, false))
        assertEquals(MobileReaderSystemBarsVisibility(false, false), mobilePdfSystemBarsVisibility(SystemUiMode.SYNC, false))
        assertEquals(MobileReaderSystemBarsVisibility(true, true), mobilePdfSystemBarsVisibility(SystemUiMode.SYNC, true))
        assertEquals(MobileReaderSystemBarsVisibility(false, false), mobilePdfSystemBarsVisibility(SystemUiMode.HIDDEN, true))
    }

    @Test
    fun epubStatusFollowsChromeWhileNavigationAlwaysMatchesSurfaceScrims() {
        assertEquals(
            MobileReaderSystemBarBackdrops(
                statusBar = MobileReaderSystemBarBackdrop.CHROME,
                navigationBar = MobileReaderSystemBarBackdrop.CHROME,
            ),
            mobileEpubSystemBarBackdrops(readerChromeVisible = true),
        )
        assertEquals(
            MobileReaderSystemBarBackdrops(
                statusBar = MobileReaderSystemBarBackdrop.READER_CONTENT,
                navigationBar = MobileReaderSystemBarBackdrop.CHROME,
            ),
            mobileEpubSystemBarBackdrops(readerChromeVisible = false),
        )
    }

    @Test
    fun pdfBarsOnlyClaimTheirOwnSystemStrip() {
        assertEquals(
            MobileReaderSystemBarBackdrops(
                statusBar = MobileReaderSystemBarBackdrop.CHROME,
                navigationBar = MobileReaderSystemBarBackdrop.CHROME,
            ),
            mobilePdfSystemBarBackdrops(topBarVisible = true, bottomBarVisible = true),
        )
        assertEquals(
            MobileReaderSystemBarBackdrops(
                statusBar = MobileReaderSystemBarBackdrop.CHROME,
                navigationBar = MobileReaderSystemBarBackdrop.PLATFORM_DEFAULT,
            ),
            mobilePdfSystemBarBackdrops(topBarVisible = true, bottomBarVisible = false),
        )
        assertEquals(
            MobileReaderSystemBarBackdrops(
                statusBar = MobileReaderSystemBarBackdrop.PLATFORM_DEFAULT,
                navigationBar = MobileReaderSystemBarBackdrop.PLATFORM_DEFAULT,
            ),
            mobilePdfSystemBarBackdrops(topBarVisible = false, bottomBarVisible = false),
        )
    }

    @Test
    fun darkChromeUsesLightIconsEvenWhenReaderContentIsLight() {
        val flags = mobileReaderSystemBarIconFlags(
            backdrops = mobileEpubSystemBarBackdrops(readerChromeVisible = true),
            chromeBackground = Color(0xFF121212),
            contentBackground = Color.White,
        )
        assertEquals(false, flags.lightStatusBarAppearance)
        assertEquals(false, flags.lightNavigationBarAppearance)
    }

    @Test
    fun lightChromeUsesDarkIconsEvenWhenReaderContentIsDark() {
        val flags = mobileReaderSystemBarIconFlags(
            backdrops = mobileEpubSystemBarBackdrops(readerChromeVisible = true),
            chromeBackground = Color.White,
            contentBackground = Color(0xFF121212),
        )
        assertEquals(true, flags.lightStatusBarAppearance)
        assertEquals(true, flags.lightNavigationBarAppearance)
    }

    @Test
    fun epubWithoutChromeMatchesReaderContentNotChrome() {
        val flags = mobileReaderSystemBarIconFlags(
            backdrops = mobileEpubSystemBarBackdrops(readerChromeVisible = false),
            chromeBackground = Color(0xFF121212),
            contentBackground = Color.White,
        )
        assertEquals(true, flags.lightStatusBarAppearance)
        assertEquals(false, flags.lightNavigationBarAppearance)
    }

    @Test
    fun pdfBarWithoutChromeLeavesThePlatformAppearanceAlone() {
        val flags = mobileReaderSystemBarIconFlags(
            backdrops = mobilePdfSystemBarBackdrops(topBarVisible = false, bottomBarVisible = true),
            chromeBackground = Color.White,
            contentBackground = Color.White,
        )
        assertEquals(null, flags.lightStatusBarAppearance)
        assertEquals(true, flags.lightNavigationBarAppearance)
    }

    @Test
    fun pdfDocumentPresentationPreservesLoadingErrorReadyPriority() {
        assertEquals(MobilePdfDocumentPresentation.LOADING, selectMobilePdfDocumentPresentation(true, true, true, 2))
        assertEquals(MobilePdfDocumentPresentation.ERROR, selectMobilePdfDocumentPresentation(false, true, true, 2))
        assertEquals(MobilePdfDocumentPresentation.READY, selectMobilePdfDocumentPresentation(false, false, true, 2))
        assertEquals(MobilePdfDocumentPresentation.EMPTY, selectMobilePdfDocumentPresentation(false, false, true, 0))
        assertEquals(MobilePdfDocumentPresentation.EMPTY, selectMobilePdfDocumentPresentation(false, false, false, 2))
    }

    @Test
    fun pdfVerticalAlwaysShowPadsBelowStatusBar() {
        assertEquals(true, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.DEFAULT, false, true, false))
        assertEquals(true, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.DEFAULT, true, true, false))
    }

    @Test
    fun pdfVerticalSyncNeverPadsBelowStatusBar() {
        assertEquals(false, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.SYNC, false, true, false))
        assertEquals(false, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.SYNC, true, true, false))
    }

    @Test
    fun pdfVerticalNeverPadsWhenHiddenPaginationOrSplit() {
        assertEquals(false, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.HIDDEN, true, true, false))
        assertEquals(false, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.DEFAULT, true, false, false))
        assertEquals(false, shouldPadPdfVerticalContentBelowStatusBar(SystemUiMode.DEFAULT, true, true, true))
    }
}
