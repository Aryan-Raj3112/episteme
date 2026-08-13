package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderOverflowMenuTest {
    @Test
    fun epubSectionsPreserveAndroidOrderingAndGroupedTtsVisibility() {
        val sections = epubOverflowMenuSections(
            hiddenTools = setOf(
                ReaderTool.TTS_SETTINGS.name,
                ReaderTool.TTS_REPLACEMENTS.name,
                ReaderTool.FILE_INFO.name,
            ),
            hasHiddenToolbarTools = true,
            hasToggleReflow = true,
            hasDeleteReflow = true,
        )

        assertEquals(EpubOverflowMenuSection.CUSTOMIZE_TOOLBAR, sections.first())
        assertEquals(EpubOverflowMenuSection.HIDDEN_TOOLS, sections[1])
        assertTrue(sections.indexOf(EpubOverflowMenuSection.VIEW_ORIGINAL_PDF) < sections.indexOf(EpubOverflowMenuSection.DELETE_TEXT_VIEW))
        assertFalse(EpubOverflowMenuSection.TTS_SETTINGS in sections)
        assertFalse(EpubOverflowMenuSection.FILE_INFO in sections)
    }

    @Test
    fun pdfSectionsPreserveFlavorAndFileCapabilityRules() {
        val nonPdfSections = pdfOverflowMenuSections(
            hiddenTools = setOf(PdfReaderTool.SHARE.name),
            hasHiddenToolbarTools = false,
            isPro = false,
            effectiveFileType = FileType.CBZ,
        )
        val proPdfSections = pdfOverflowMenuSections(
            hiddenTools = emptySet(),
            hasHiddenToolbarTools = true,
            isPro = true,
            effectiveFileType = FileType.PDF,
            canPrintDocument = true,
        )

        assertFalse(PdfOverflowMenuSection.OCR_LANGUAGE in nonPdfSections)
        assertFalse(PdfOverflowMenuSection.FILE_ACTIONS in nonPdfSections)
        assertTrue(PdfOverflowMenuSection.HIDDEN_TOOLS in proPdfSections)
        assertTrue(PdfOverflowMenuSection.OCR_LANGUAGE in proPdfSections)
        assertTrue(PdfOverflowMenuSection.FILE_ACTIONS in proPdfSections)
        assertEquals(PdfOverflowMenuSection.FILE_INFO, proPdfSections.last())
    }
}
