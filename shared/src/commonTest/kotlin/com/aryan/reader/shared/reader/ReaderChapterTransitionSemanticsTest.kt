package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderChapterTransitionSemanticsTest {

    @Test
    fun `android pull-to-turn storage maps to inverse seamless switch state`() {
        val pullToTurn = ReaderSettings(seamlessChapterNavigation = true)
        assertTrue(pullToTurn.pullToTurnEnabled)
        assertFalse(pullToTurn.seamlessChapterTransitionEnabled)

        val seamless = ReaderSettings(seamlessChapterNavigation = false)
        assertFalse(seamless.pullToTurnEnabled)
        assertTrue(seamless.seamlessChapterTransitionEnabled)
    }

    @Test
    fun `default matches android pull-to-turn behavior`() {
        val defaults = ReaderSettings()
        assertTrue(defaults.pullToTurnEnabled)
        assertFalse(defaults.seamlessChapterTransitionEnabled)
    }
}
