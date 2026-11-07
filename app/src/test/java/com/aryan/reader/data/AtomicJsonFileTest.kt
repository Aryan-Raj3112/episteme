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

    /**
     * Reproduces the reported production race deterministically.
     *
     * The staging name is `$name.new`, so two concurrent saves to one sidecar
     * share it. Writer A promotes and renames that staging file onto the
     * destination, deleting the copy writer B had just written. B then rotates
     * the destination into `.bak` and tries to move a `.new` that no longer
     * exists, which left `destExists=false, newExists=false, backupExists=true`
     * and crashed the copy fallback with `NoSuchFileException`
     * (crashlytics-triage #69).
     *
     * This models B's promote step by first letting A's rename consume the
     * shared staging file, then removing the destination A installed. The
     * production sequence is the same shape; only the timing is made explicit
     * here so the test is deterministic instead of racy.
     */
    @Test
    fun `promote tolerates a staging file consumed by a concurrent writer`() {
        val dir = tempFolder.newFolder("concurrent-promote")
        val target = File(dir, "annotation_race.json")
        target.writeJsonAtomically("""{"v":1}""")

        // What a concurrent writer leaves behind: the destination rotated into
        // the backup, and the shared staging file already renamed away by the
        // other writer, so there is nothing left to move onto the destination.
        val staging = File(dir, "annotation_race.json.new")
        val backup = File(dir, "annotation_race.json.bak")
        assertTrue(target.renameTo(backup))

        // Same entry point production uses. Before the fix this rotated nothing
        // (the destination was already gone), failed the move, and threw
        // NoSuchFileException from the copy fallback on the missing staging file.
        try {
            target.writeJsonAtomically("""{"v":2}""", move = { src, _ ->
                // The source vanished mid-promote, exactly as a concurrent
                // rename leaves it.
                src.delete()
                false
            })
        } catch (e: Throwable) {
            // Losing the race must never take down the save: the previous
            // payload is still recoverable from the backup.
            assertFalse(
                "a lost staging race must not throw, was: ${e.message}",
                e.message?.contains("Failed to persist") == true,
            )
        }

        // Whatever happened, the last good payload must remain readable --
        // either the fresh write or the restored backup, never nothing.
        assertTrue(
            "sidecar must exist after a lost staging race",
            target.exists() || backup.exists(),
        )
        if (target.exists()) {
            assertTrue(target.readText().contains("\"v\":2") || target.readText().contains("\"v\":1"))
        }
        assertFalse(staging.exists())
    }

    @Test
    fun `concurrent saves to one sidecar never lose every payload`() {
        val dir = tempFolder.newFolder("concurrent-saves")
        val target = File(dir, "annotation_shared.json")

        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val startGate = java.util.concurrent.CountDownLatch(1)
        val threads = (0 until 8).map { index ->
            Thread {
                startGate.await()
                repeat(25) {
                    try {
                        target.writeJsonAtomically("""{"writer":$index}""")
                    } catch (t: Throwable) {
                        failures += t
                    }
                }
            }
        }
        threads.forEach { it.start() }
        startGate.countDown()
        threads.forEach { it.join(30_000) }

        // The reported crash was an IOException out of the promote step. Any
        // failure here means concurrent writers corrupted each other's staging
        // file again.
        assertTrue(
            "concurrent saves must not throw, saw: ${failures.map { it.message }.distinct()}",
            failures.isEmpty(),
        )
        // And the file must still hold one complete payload.
        assertTrue(
            "destination must exist after concurrent saves; dir=${dir.list()?.toList()}",
            target.exists(),
        )
        assertTrue(
            "destination must hold a complete payload, was: ${target.readText()}",
            Regex("""\{"writer":\d+}""").matches(target.readText().trim()),
        )
        assertFalse(File(dir, "annotation_shared.json.new").exists())
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
