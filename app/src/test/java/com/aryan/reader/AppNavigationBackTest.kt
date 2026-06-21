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
}