package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.PdfSplitWorkspaceAction
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.PdfSplitWorkspaceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPdfSplitWorkspaceTest {
    @Test
    fun persistedWorkspaceRoundTripsWithoutRendererSessionIds() {
        val original = PdfSplitWorkspaceState().reduce(
            PdfSplitWorkspaceAction.Open(
                primary = com.aryan.reader.shared.PdfSplitPaneState("one", "/tmp/one.pdf"),
                secondary = com.aryan.reader.shared.PdfSplitPaneState("two", "/tmp/two.pdf"),
            ),
        )

        persistIosPdfSplitWorkspace(original)
        val restored = loadIosPdfSplitWorkspace()
        persistIosPdfSplitWorkspace(PdfSplitWorkspaceState())

        assertEquals(original.primary?.bookId, restored.primary?.bookId)
        assertEquals(original.secondary?.uriString, restored.secondary?.uriString)
        assertEquals(original.focusedPane, restored.focusedPane)
        assertNotEquals(original.primary?.sessionId, restored.primary?.sessionId)
    }

    @Test
    fun paneIdentityRequiresAPdfPath() {
        val nonPdf = BookItem(
            id = "epub",
            path = "/tmp/book.epub",
            type = FileType.EPUB,
            displayName = "Book.epub",
            timestamp = 1L,
        )
        val noPath = BookItem(
            id = "pdf",
            path = null,
            type = FileType.PDF,
            displayName = "Book.pdf",
            timestamp = 1L,
        )

        assertNull(iosPdfSplitPaneState(nonPdf))
        assertNull(iosPdfSplitPaneState(noPath))
    }

    @Test
    fun restoringWithMissingPrimaryPromotesTheAvailableSecondary() {
        val persisted = PdfSplitWorkspaceState().reduce(
            PdfSplitWorkspaceAction.Open(
                primary = PdfSplitPaneState("missing", "opds-pse://missing"),
                secondary = PdfSplitPaneState("survivor", "opds-pse://survivor"),
            ),
        )
        val survivor = BookItem(
            id = "survivor",
            path = "opds-pse://survivor",
            type = FileType.PDF,
            displayName = "Survivor.pdf",
            timestamp = 1L,
        )

        val restored = restoreIosPdfSplitWorkspaceWithRecovery(persisted, listOf(survivor))

        assertEquals("survivor", restored.workspace.primary?.bookId)
        assertNull(restored.workspace.secondary)
        assertTrue(restored.missingPanes.contains(com.aryan.reader.shared.PdfSplitPane.PRIMARY))
    }

    @Test
    fun restoringWithMissingSecondaryKeepsThePrimary() {
        val persisted = PdfSplitWorkspaceState().reduce(
            PdfSplitWorkspaceAction.Open(
                primary = PdfSplitPaneState("survivor", "opds-pse://survivor"),
                secondary = PdfSplitPaneState("missing", "opds-pse://missing"),
            ),
        )
        val survivor = BookItem(
            id = "survivor",
            path = "opds-pse://survivor",
            type = FileType.PDF,
            displayName = "Survivor.pdf",
            timestamp = 1L,
        )

        val restored = restoreIosPdfSplitWorkspaceWithRecovery(persisted, listOf(survivor))

        assertEquals("survivor", restored.workspace.primary?.bookId)
        assertNull(restored.workspace.secondary)
        assertTrue(restored.missingPanes.contains(com.aryan.reader.shared.PdfSplitPane.SECONDARY))
    }
}
