package com.aryan.reader.shared.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubDrawerModelsTest {
    @Test
    fun `drawer follows Android benchmark order when tabs are available`() {
        assertEquals(
            listOf(
                EpubDrawerSection.TABS,
                EpubDrawerSection.CHAPTERS,
                EpubDrawerSection.BOOKMARKS,
                EpubDrawerSection.ANNOTATIONS,
                EpubDrawerSection.IMAGES,
            ),
            epubDrawerSections(
                EpubDrawerCapabilities(tabsEnabled = true, hasOpenTabs = true),
            ),
        )
    }

    @Test
    fun `tabs require both enabled capability and an open tab`() {
        val expectedWithoutTabs = listOf(
            EpubDrawerSection.CHAPTERS,
            EpubDrawerSection.BOOKMARKS,
            EpubDrawerSection.ANNOTATIONS,
            EpubDrawerSection.IMAGES,
        )

        assertEquals(
            expectedWithoutTabs,
            epubDrawerSections(EpubDrawerCapabilities(tabsEnabled = false, hasOpenTabs = true)),
        )
        assertEquals(
            expectedWithoutTabs,
            epubDrawerSections(EpubDrawerCapabilities(tabsEnabled = true, hasOpenTabs = false)),
        )
        assertEquals(expectedWithoutTabs, epubDrawerSections())
    }

    @Test
    fun `every section exposes a label key so hosts cannot drift in wording`() {
        assertTrue(epubDrawerSections().all { it.stringKey.isNotBlank() && it.fallbackLabel.isNotBlank() })
    }
}
