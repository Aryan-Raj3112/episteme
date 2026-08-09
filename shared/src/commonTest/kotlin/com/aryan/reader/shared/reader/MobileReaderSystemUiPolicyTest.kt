package com.aryan.reader.shared.reader

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
    fun pdfDocumentPresentationPreservesLoadingErrorReadyPriority() {
        assertEquals(MobilePdfDocumentPresentation.LOADING, selectMobilePdfDocumentPresentation(true, true, true, 2))
        assertEquals(MobilePdfDocumentPresentation.ERROR, selectMobilePdfDocumentPresentation(false, true, true, 2))
        assertEquals(MobilePdfDocumentPresentation.READY, selectMobilePdfDocumentPresentation(false, false, true, 2))
        assertEquals(MobilePdfDocumentPresentation.EMPTY, selectMobilePdfDocumentPresentation(false, false, true, 0))
        assertEquals(MobilePdfDocumentPresentation.EMPTY, selectMobilePdfDocumentPresentation(false, false, false, 2))
    }
}
