package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationBackTest {

    @Test
    fun `app back intercepts reader routes because selected file state owns reader navigation`() {
        assertTrue(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.PDF_VIEWER_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        )
        assertTrue(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.EPUB_READER_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        )
    }

    @Test
    fun `app back does not intercept main missing previous or non resumed entries`() {
        assertFalse(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.MAIN_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = true
            )
        )
        assertFalse(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.SETTINGS_SCREEN_ROUTE,
                hasPreviousBackStackEntry = false,
                isCurrentEntryResumed = true
            )
        )
        assertFalse(
            shouldInterceptAppNavBack(
                currentRoute = AppDestinations.SETTINGS_SCREEN_ROUTE,
                hasPreviousBackStackEntry = true,
                isCurrentEntryResumed = false
            )
        )
    }

    @Test
    fun `selected reader file route sync does not close utility screens`() {
        assertFalse(shouldSyncSelectedFileRoute(AppDestinations.PRO_SCREEN_ROUTE))
        assertFalse(shouldSyncSelectedFileRoute(AppDestinations.SETTINGS_SCREEN_ROUTE))
        assertFalse(shouldSyncSelectedFileRoute(AppDestinations.FEEDBACK_SCREEN_ROUTE))
    }

    @Test
    fun `selected reader file route sync remains enabled for main and reader routes`() {
        assertTrue(shouldSyncSelectedFileRoute(null))
        assertTrue(shouldSyncSelectedFileRoute(AppDestinations.MAIN_ROUTE))
        assertTrue(shouldSyncSelectedFileRoute(AppDestinations.PDF_VIEWER_ROUTE))
        assertTrue(shouldSyncSelectedFileRoute(AppDestinations.EPUB_READER_ROUTE))
    }
}
