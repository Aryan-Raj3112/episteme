package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The annotation drawer filters are pure state, so the behaviour that matters — that notes and
 * colour combine, that a colour is one swatch regardless of stored opacity, and that the strip
 * orders by usage — is pinned here rather than through the list UI.
 */
class SharedAnnotationFiltersTest {
    private val yellow = 0xFFFBC02D.toInt()
    private val blue = 0xFF1976D2.toInt()

    @Test
    fun `colour identity ignores the stored alpha`() {
        // PDF highlights store their own opacity (0x8C default), EPUB palette colours are opaque.
        assertEquals(sharedAnnotationColorKey(yellow), sharedAnnotationColorKey(0x8CFBC02D.toInt()))
        assertEquals(yellow, sharedAnnotationColorKeyArgb(sharedAnnotationColorKey(0x8CFBC02D.toInt())))
    }

    @Test
    fun `colour options keep one entry per hue and order by usage`() {
        val options = sharedAnnotationColorFilterOptions(
            listOf(blue, yellow, 0x8CFBC02D.toInt())
        )

        assertEquals(listOf(sharedAnnotationColorKey(yellow), sharedAnnotationColorKey(blue)), options)
    }

    @Test
    fun `colour options break usage ties by colour value`() {
        val options = sharedAnnotationColorFilterOptions(listOf(yellow, blue))

        // blue sorts before yellow by ARGB, so an equal-count strip never reorders between passes.
        assertEquals(listOf(sharedAnnotationColorKey(blue), sharedAnnotationColorKey(yellow)), options)
    }

    @Test
    fun `notes and colour filters combine`() {
        val notesWithYellow = SharedAnnotationFilterState(
            notesOnly = true,
            selectedColorKey = sharedAnnotationColorKey(yellow),
        )

        assertTrue(notesWithYellow.matches(yellow, "a note"))
        assertFalse(notesWithYellow.matches(yellow, "  "))
        assertFalse(notesWithYellow.matches(blue, "a note"))
    }

    @Test
    fun `colour filter accepts any opacity of the selected hue`() {
        val yellowOnly = SharedAnnotationFilterState(selectedColorKey = sharedAnnotationColorKey(yellow))

        assertTrue(yellowOnly.matches(0x66FBC02D.toInt(), note = null))
        assertFalse(yellowOnly.matches(blue, note = null))
    }

    @Test
    fun `tapping the selected colour clears it and All clears everything`() {
        val yellowOnly = SharedAnnotationFilterState(selectedColorKey = sharedAnnotationColorKey(yellow))

        assertNull(yellowOnly.withColorToggled(sharedAnnotationColorKey(yellow)).selectedColorKey)
        // Toggling a different colour replaces the selection instead of stacking hues.
        assertEquals(
            sharedAnnotationColorKey(blue),
            yellowOnly.withColorToggled(sharedAnnotationColorKey(blue)).selectedColorKey,
        )

        val everything = yellowOnly.withNotesOnly(true)
        assertTrue(everything.hasActiveFilters)
        assertTrue(everything.cleared().hasActiveFilters.not())
    }
}
