package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookmarkPersistencePolicyTest {
    @Test
    fun `blank and empty bookmark json are equivalent noops`() {
        assertTrue(isBookmarkJsonEmpty(null))
        assertTrue(isBookmarkJsonEmpty(""))
        assertTrue(isBookmarkJsonEmpty("   "))
        assertTrue(isBookmarkJsonEmpty("[]"))
        assertFalse(isBookmarkJsonEmpty("""[{"pageIndex":1}]"""))
    }

    @Test
    fun `bookmark noop guard matches annotation behavior`() {
        assertTrue(bookmarkJsonEquivalentForNoop(null, "[]"))
        assertTrue(bookmarkJsonEquivalentForNoop("", "[]"))
        assertTrue(bookmarkJsonEquivalentForNoop("[]", "[]"))
        assertTrue(bookmarkJsonEquivalentForNoop("""[{"pageIndex":1}]""", """[{"pageIndex":1}]"""))
        assertFalse(bookmarkJsonEquivalentForNoop("""[{"pageIndex":1}]""", "[]"))
        assertFalse(bookmarkJsonEquivalentForNoop(null, """[{"pageIndex":1}]"""))
    }

    @Test
    fun `empty snapshot never wipes local bookmarks`() {
        // Tab switch with a stale empty snapshot over good in-memory state.
        assertFalse(
            shouldApplyAnnotationSnapshot(
                hasLocalEdits = false,
                localCount = 2,
                snapshotJson = "[]",
                snapshotCount = 0,
            )
        )
        assertFalse(
            shouldApplyAnnotationSnapshot(
                hasLocalEdits = false,
                localCount = 2,
                snapshotJson = null,
                snapshotCount = 0,
            )
        )
    }

    @Test
    fun `late non-empty snapshot fills fresh state`() {
        // Async open: reader composed with null, DB value arrives later.
        assertTrue(
            shouldApplyAnnotationSnapshot(
                hasLocalEdits = false,
                localCount = 0,
                snapshotJson = """[{"pageIndex":1}]""",
                snapshotCount = 1,
            )
        )
    }

    @Test
    fun `local edits win over any snapshot`() {
        // Unsaved toggles and intentional delete-all must survive tab switches.
        assertFalse(
            shouldApplyAnnotationSnapshot(
                hasLocalEdits = true,
                localCount = 3,
                snapshotJson = """[{"pageIndex":1}]""",
                snapshotCount = 1,
            )
        )
        assertFalse(
            shouldApplyAnnotationSnapshot(
                hasLocalEdits = true,
                localCount = 0,
                snapshotJson = """[{"pageIndex":1}]""",
                snapshotCount = 1,
            )
        )
    }

    @Test
    fun `non-empty snapshot restores dropped state without edits`() {
        assertTrue(
            shouldApplyAnnotationSnapshot(
                hasLocalEdits = false,
                localCount = 0,
                snapshotJson = """[{"pageIndex":1}]""",
                snapshotCount = 1,
            )
        )
    }

    @Test
    fun `empty save is skipped until local state exists`() {
        // Fresh reader: nothing loaded, nothing authored. Persisting this empty
        // set is what wiped highlights/bookmarks on open.
        assertTrue(shouldSkipEmptyAnnotationSave(hasLocalState = false, localCount = 0))
        // Once local state exists (loaded or authored), an empty set is a real
        // delete-all and must persist.
        assertFalse(shouldSkipEmptyAnnotationSave(hasLocalState = true, localCount = 0))
        // Non-empty local state is never skipped.
        assertFalse(shouldSkipEmptyAnnotationSave(hasLocalState = false, localCount = 2))
        assertFalse(shouldSkipEmptyAnnotationSave(hasLocalState = true, localCount = 2))
    }

    @Test
    fun `save guard skips redundant and destructive writes but keeps real edits`() {
        // Redundant: identical or both-empty payloads.
        assertTrue(shouldSkipBookmarkSave("[]", "[]"))
        assertTrue(shouldSkipBookmarkSave(null, "[]"))
        assertTrue(shouldSkipBookmarkSave("""[{"pageIndex":1}]""", """[{"pageIndex":1}]"""))
        // Destructive: stale empty snapshot over persisted bookmarks (#556).
        assertTrue(shouldSkipBookmarkSave("""[{"pageIndex":1}]""", "[]"))
        assertTrue(shouldSkipBookmarkSave("""[{"pageIndex":1}]""", ""))
        // Real edits always persist, including intentional delete-all, which the
        // caller routes through the explicit user-edit path instead.
        assertFalse(shouldSkipBookmarkSave(null, """[{"pageIndex":1}]"""))
        assertFalse(shouldSkipBookmarkSave("[]", """[{"pageIndex":1}]"""))
        assertFalse(
            shouldSkipBookmarkSave(
                """[{"pageIndex":1}]""",
                """[{"pageIndex":1},{"pageIndex":2}]""",
            )
        )
    }
}
