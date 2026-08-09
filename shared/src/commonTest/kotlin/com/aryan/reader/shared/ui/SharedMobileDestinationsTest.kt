package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedMobileDestinationsTest {
    @Test
    fun routesRemainCompatibleWithAndroidNavigation() {
        assertEquals("main", SharedMobileAppDestination.MAIN.route)
        assertEquals("pdf_viewer", SharedMobileAppDestination.PDF_VIEWER.route)
        assertEquals("epub_reader", SharedMobileAppDestination.EPUB_READER.route)
        assertEquals("settings_screen_route", SharedMobileAppDestination.SETTINGS.route)
        assertEquals(SharedMobileAppDestination.PRO, SharedMobileAppDestination.fromRoute("pro_screen"))
        assertNull(SharedMobileAppDestination.fromRoute("unknown"))
    }

    @Test
    fun readerAndSelectedFileSyncPoliciesMatchAndroid() {
        assertTrue(SharedMobileAppDestination.PDF_VIEWER.isReader)
        assertTrue(SharedMobileAppDestination.EPUB_READER.isReader)
        assertTrue(SharedMobileAppDestination.MAIN.participatesInSelectedFileSync)
        assertFalse(SharedMobileAppDestination.SETTINGS.participatesInSelectedFileSync)
    }

    @Test
    fun mainPageOrderAndFallbackRemainStable() {
        assertEquals(SharedMobileMainDestination.HOME, SharedMobileMainDestination.fromPageIndex(0))
        assertEquals(SharedMobileMainDestination.LIBRARY, SharedMobileMainDestination.fromPageIndex(1))
        assertEquals(SharedMobileMainDestination.UNIFIED_LIBRARY, SharedMobileMainDestination.fromPageIndex(2))
        assertEquals(SharedMobileMainDestination.HOME, SharedMobileMainDestination.fromPageIndex(99))
    }
}
