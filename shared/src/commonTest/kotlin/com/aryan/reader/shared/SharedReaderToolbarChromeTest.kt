package com.aryan.reader.shared

import com.aryan.reader.shared.ui.SharedReaderBarEdge
import com.aryan.reader.shared.ui.SharedReaderToolbarPlacement
import com.aryan.reader.shared.ui.SharedReaderOverflowMenuState
import com.aryan.reader.shared.ui.sharedEpubToolbarTools
import com.aryan.reader.shared.ui.sharedPdfToolbarTools
import com.aryan.reader.shared.ui.sharedReaderBarOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedReaderToolbarChromeTest {
    @Test
    fun overflowMenuOpenAndDismissCollapseEveryNestedSection() {
        val state = SharedReaderOverflowMenuState()
        state.hiddenToolsExpanded.value = true
        state.readingModeExpanded.value = true
        state.ttsSettingsExpanded.value = true
        state.fileActionsExpanded.value = true

        state.open()

        assertTrue(state.menuExpanded.value)
        assertFalse(state.hiddenToolsExpanded.value)
        assertFalse(state.readingModeExpanded.value)
        assertFalse(state.ttsSettingsExpanded.value)
        assertFalse(state.fileActionsExpanded.value)

        state.hiddenToolsExpanded.value = true
        state.dismiss()

        assertFalse(state.menuExpanded.value)
        assertFalse(state.hiddenToolsExpanded.value)
    }

    @Test
    fun `top and bottom bars retain Android slide directions`() {
        assertEquals(-200, sharedReaderBarOffset(SharedReaderBarEdge.TOP, 200))
        assertEquals(200, sharedReaderBarOffset(SharedReaderBarEdge.BOTTOM, 200))
    }

    @Test
    fun `epub toolbar projection preserves order and legacy enum-name sets`() {
        val order = listOf(ReaderTool.SEARCH, ReaderTool.THEME, ReaderTool.SLIDER, ReaderTool.FILE_INFO)
        val toolbarTools = setOf(ReaderTool.SEARCH, ReaderTool.THEME, ReaderTool.SLIDER)

        assertEquals(
            listOf(ReaderTool.THEME),
            sharedEpubToolbarTools(
                toolOrder = order,
                toolbarTools = toolbarTools,
                hiddenToolNamesOrIds = setOf(ReaderTool.SEARCH.name),
                bottomToolNamesOrIds = setOf(ReaderTool.SLIDER.name),
                placement = SharedReaderToolbarPlacement.TOP,
            ),
        )
        assertEquals(
            listOf(ReaderTool.SLIDER),
            sharedEpubToolbarTools(
                toolOrder = order,
                toolbarTools = toolbarTools,
                hiddenToolNamesOrIds = setOf(ReaderTool.SEARCH.id),
                bottomToolNamesOrIds = setOf(ReaderTool.SLIDER.id),
                placement = SharedReaderToolbarPlacement.BOTTOM,
            ),
        )
    }

    @Test
    fun `pdf toolbar projection excludes overflow tools and accepts legacy names`() {
        val order = listOf(PdfReaderTool.SEARCH, PdfReaderTool.FILE_INFO, PdfReaderTool.THEME)

        assertEquals(
            listOf(PdfReaderTool.THEME),
            sharedPdfToolbarTools(
                toolOrder = order,
                hiddenToolNamesOrIds = setOf(PdfReaderTool.SEARCH.name),
                bottomToolNamesOrIds = emptySet(),
                placement = SharedReaderToolbarPlacement.TOP,
            ),
        )
    }
}
