package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The folder-fallback projection path synthesizes a `SyncedFolder` for any book whose source folder
 * is not in the synced list, so unsynced folders still appear in the library's folder filters.
 *
 * The identifier is platform-shaped: Android records a tree URI in `RecentFileItem.sourceFolderUri`,
 * iOS records the folder name in `BookItem.sourceFolder`. Both must reduce to a real display name,
 * because Android used to hardcode the "Local Folder" placeholder here and made every unsynced
 * folder indistinguishable in the UI.
 */
class FolderDisplayNameTest {

    @Test
    fun `an android tree uri shows its last path segment`() {
        // SAF folder identifiers are tree URIs whose final segment is percent-encoded
        // ("primary%3ADocuments" is primary:/Documents). Taking the segment verbatim is what
        // shared already did on iOS, so this is a parity fix rather than a prettifying one:
        // what matters is that Android stops collapsing every unsynced folder to one literal.
        // Decoding the segment into a human label is a separate open question.
        assertEquals(
            "primary%3ADocuments",
            folderDisplayName("content://com.android.externalstorage.documents/tree/primary%3ADocuments"),
        )
    }

    @Test
    fun `a plain file uri shows its final segment`() {
        assertEquals("report.pdf", folderDisplayName("content://media/external/file/report.pdf"))
        assertEquals("books", folderDisplayName("file:///storage/emulated/0/books"))
    }

    @Test
    fun `a bare folder name is returned unchanged`() {
        assertEquals("Research", folderDisplayName("Research"))
    }

    @Test
    fun `windows separators and trailing slashes are normalized`() {
        assertEquals("Papers", folderDisplayName("\\\\NAS\\share\\Papers\\"))
        assertEquals("Papers", folderDisplayName("/mnt/Papers/"))
    }

    @Test
    fun `a blank or root-only identifier keeps the placeholder`() {
        // These are the cases where there is no last segment to show, so the placeholder the UI
        // has always displayed is the right answer rather than a regression.
        assertEquals("Local Folder", folderDisplayName(""))
        assertEquals("Local Folder", folderDisplayName("/"))
        assertEquals("Local Folder", folderDisplayName("   "))
    }
}
