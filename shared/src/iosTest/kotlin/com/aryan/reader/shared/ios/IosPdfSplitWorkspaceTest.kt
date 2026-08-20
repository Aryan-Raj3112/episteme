package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.PdfSplitWorkspaceAction
import com.aryan.reader.shared.PdfSplitWorkspaceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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
}
