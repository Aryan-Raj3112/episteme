package com.aryan.reader.shared.ios

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosUtilityNavigationTransitionTest {

    @Test
    fun `opening a utility screen from home slides forward`() {
        assertTrue(iosUtilitySwapForwards(null, IosUtilityScreen.SETTINGS))
        assertTrue(iosUtilitySwapForwards(null, IosUtilityScreen.FONTS))
    }

    @Test
    fun `returning home from a utility screen slides back`() {
        assertFalse(iosUtilitySwapForwards(IosUtilityScreen.SETTINGS, null))
        assertFalse(iosUtilitySwapForwards(IosUtilityScreen.LANGUAGE, null))
    }

    @Test
    fun `language is deeper than settings and its return slides back`() {
        assertTrue(iosUtilitySwapForwards(IosUtilityScreen.SETTINGS, IosUtilityScreen.LANGUAGE))
        assertFalse(iosUtilitySwapForwards(IosUtilityScreen.LANGUAGE, IosUtilityScreen.SETTINGS))
    }

    @Test
    fun `sibling utility screens keep the forward motion`() {
        assertTrue(iosUtilitySwapForwards(IosUtilityScreen.SETTINGS, IosUtilityScreen.FONTS))
        assertTrue(iosUtilitySwapForwards(IosUtilityScreen.ACCOUNT, IosUtilityScreen.PRO))
    }
}
