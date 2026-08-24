package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfDrawerModelsTest {
    @Test
    fun `drawer follows Android benchmark order when tabs are available`() {
        assertEquals(
            listOf(
                PdfDrawerSection.TABS,
                PdfDrawerSection.CHAPTERS,
                PdfDrawerSection.BOOKMARKS,
                PdfDrawerSection.HIGHLIGHTS,
                PdfDrawerSection.PAGES,
            ),
            pdfDrawerSections(
                PdfDrawerCapabilities(tabsEnabled = true, hasOpenTabs = true),
            ),
        )
    }

    @Test
    fun `tabs require both enabled capability and an open tab`() {
        val expectedWithoutTabs = listOf(
            PdfDrawerSection.CHAPTERS,
            PdfDrawerSection.BOOKMARKS,
            PdfDrawerSection.HIGHLIGHTS,
            PdfDrawerSection.PAGES,
        )

        assertEquals(
            expectedWithoutTabs,
            pdfDrawerSections(PdfDrawerCapabilities(tabsEnabled = false, hasOpenTabs = true)),
        )
        assertEquals(
            expectedWithoutTabs,
            pdfDrawerSections(PdfDrawerCapabilities(tabsEnabled = true, hasOpenTabs = false)),
        )
        assertEquals(expectedWithoutTabs, pdfDrawerSections())
    }
}
