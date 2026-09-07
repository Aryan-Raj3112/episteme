package com.aryan.reader.data

import org.junit.Assert.assertEquals
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

    @Test
    fun `vanished source during backup still writes fresh content instead of crashing`() {
        val target = VanishingSourceFile(tempFolder.root, "annotation_test.json")

        target.writeJsonAtomically("""{"ink":[]}""") // must not throw

        assertEquals("""{"ink":[]}""", File(tempFolder.root, "annotation_test.json").readText())
    }

    @Test
    fun `sequential writes rotate through backup and persist latest content`() {
        val target = File(tempFolder.root, "annotation_test.json")

        target.writeJsonAtomically("""{"v":1}""")
        assertEquals("""{"v":1}""", target.readText())

        target.writeJsonAtomically("""{"v":2}""")
        assertEquals("""{"v":2}""", target.readText())
    }
}
