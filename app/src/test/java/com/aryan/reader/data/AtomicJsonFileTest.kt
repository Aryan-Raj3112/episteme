package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the atomic-write rotation against files that vanish mid-save: when
 * two saves to the same sidecar race (or the file is deleted concurrently),
 * `renameTo` fails silently and the source may be gone by the time the
 * backup copy runs. That used to crash the save with NoSuchFileException;
 * now the missing backup is skipped and the fresh write still lands.
 */
class AtomicJsonFileTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Reproduces the race deterministically: claims to exist, never renames. */
    private class VanishingSourceFile(parent: File, name: String) : File(parent, name) {
        override fun exists(): Boolean = true
        override fun renameTo(dest: File): Boolean = false
    }

    /**
     * Reproduces firmwares where [File.renameTo] moves the file but still
     * reports failure. The payload is at the destination and the source is
     * gone, which is what made the delete-and-retry destroy the only good copy
     * (crashlytics-triage #53).
     */
    private class LyingRenameSourceFile(parent: File, name: String) : File(parent, name) {
        override fun renameTo(dest: File): Boolean {
            super.renameTo(dest)
            return false
        }
    }

    /** A rename the filesystem genuinely refuses: nothing moves, source stays. */
    private class RefusingSourceFile(parent: File, name: String) : File(parent, name) {
        override fun renameTo(dest: File): Boolean = false
    }

    @Test
    fun `vanished source during backup still writes fresh content instead of crashing`() {
        val target = VanishingSourceFile(tempFolder.root, "annotation_test.json")

        target.writeJsonAtomically("""{"ink":[]}""") // must not throw

        assertEquals("""{"ink":[]}""", File(tempFolder.root, "annotation_test.json").readText())
    }

    @Test
    fun `rename that moved despite reporting failure is treated as success`() {
        val src = LyingRenameSourceFile(tempFolder.root, "payload.json.new")
        src.writeText("""{"ink":[]}""")
        val dst = File(tempFolder.root, "payload.json")

        assertTrue(
            "a rename that moved the file must not be retried",
            moveByRename(src, dst),
        )
        assertEquals("""{"ink":[]}""", dst.readText())
    }

    @Test
    fun `atomic write survives a rename that moved despite reporting failure`() {
        val dir = tempFolder.newFolder("lying-rename-atomic")
        val target = LyingRenameSourceFile(dir, "annotation_lie.json")

        // End to end: the payload must land and survive, not be deleted by the
        // retry and then fail the copy fallback with NoSuchFileException.
        target.writeJsonAtomically("""{"v":1}""")

        val stored = File(dir, "annotation_lie.json")
        assertEquals("""{"v":1}""", stored.readText())
        assertFalse(File(dir, "annotation_lie.json.new").exists())
    }

    @Test
    fun `genuine rename refusal still falls back so the save lands`() {
        val dir = tempFolder.newFolder("genuine-refusal")
        // The source survives the refusal, so this is the real #27 case rather
        // than the lying-rename case above: the new guard must not swallow it.
        val src = RefusingSourceFile(dir, "payload.json.new").apply { writeText("""{"v":1}""") }
        val dst = File(dir, "payload.json")

        assertFalse(moveByRename(src, dst))
        // The source is intact, which is what lets the copy fallback land.
        assertEquals("""{"v":1}""", src.readText())
    }

    @Test
    fun `sequential writes rotate through backup and persist latest content`() {
        val target = File(tempFolder.root, "annotation_test.json")

        target.writeJsonAtomically("""{"v":1}""")
        assertEquals("""{"v":1}""", target.readText())

        target.writeJsonAtomically("""{"v":2}""")
        assertEquals("""{"v":2}""", target.readText())
    }

    @Test
    fun `rename refusal falls back to copy instead of crashing`() {
        val target = File(tempFolder.root, "annotation_fallback.json")
        target.writeJsonAtomically("""{"v":1}""")

        // Force the rename step to refuse (some firmwares return false from
        // renameTo): the save must still land via copy, with no throw.
        target.writeJsonAtomically("""{"v":2}""", move = { _, _ -> false })

        assertEquals("""{"v":2}""", target.readText())
        assertTrue(!File(tempFolder.root, "annotation_fallback.json.new").exists())
    }

    @Test
    fun `failed staging write preserves existing file`() {
        val target = File(tempFolder.root, "annotation_preserve.json")
        target.writeJsonAtomically("""{"v":1}""")

        // Block the staging file with a directory so the .new write fails:
        // the destination must keep its last good payload, not be deleted.
        val staging = File(tempFolder.root, "annotation_preserve.json.new")
        assertTrue(staging.mkdir())
        try {
            target.writeJsonAtomically("""{"v":2}""")
            fail("expected write failure")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("annotation_preserve.json") == true)
        } finally {
            staging.delete()
        }

        assertEquals("""{"v":1}""", target.readText())
    }
}
