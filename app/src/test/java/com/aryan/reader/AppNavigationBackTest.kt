package com.aryan.reader

import com.aryan.reader.shared.ui.SharedMobileAppDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationBackTest {

    @Test
    fun `app back intercepts reader routes because selected file state owns reader navigation`() {
        assertTrue(
            shouldInterceptAppNavBack(
                currentRoute = SharedMobileAppDestination.PDF_VIEWER.route,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        )
        assertTrue(
            shouldInterceptAppNavBack(
                currentRoute = SharedMobileAppDestination.EPUB_READER.route,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        )
    }

    @Test
    fun `app back intercepts non main routes even while transition is settling`() {
        assertFalse(
            shouldInterceptAppNavBack(
                currentRoute = SharedMobileAppDestination.MAIN.route,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        )
        assertFalse(
            shouldInterceptAppNavBack(
                currentRoute = SharedMobileAppDestination.SETTINGS.route,
                hasPreviousBackStackEntry = false,
                isCurrentEntryResumed = true
            )
        )
        assertTrue(
            shouldInterceptAppNavBack(
                currentRoute = SharedMobileAppDestination.SETTINGS.route,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = false
            )
        )
    }

    @Test
    fun `selected reader file route sync does not close utility screens`() {
        assertFalse(shouldSyncSelectedFileRoute(SharedMobileAppDestination.PRO.route))
        assertFalse(shouldSyncSelectedFileRoute(SharedMobileAppDestination.SETTINGS.route))
        assertFalse(shouldSyncSelectedFileRoute(SharedMobileAppDestination.FEEDBACK.route))
    }

    @Test
    fun `selected reader file route sync remains enabled for main and reader routes`() {
        assertTrue(shouldSyncSelectedFileRoute(null))
        assertTrue(shouldSyncSelectedFileRoute(SharedMobileAppDestination.MAIN.route))
        assertTrue(shouldSyncSelectedFileRoute(SharedMobileAppDestination.PDF_VIEWER.route))
        assertTrue(shouldSyncSelectedFileRoute(SharedMobileAppDestination.EPUB_READER.route))
    }
}
