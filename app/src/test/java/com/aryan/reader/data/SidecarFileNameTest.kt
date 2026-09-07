package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.StandardCharsets

/**
 * Guards sidecar file naming: names that fit keep their exact legacy spelling
 * (existing sidecars stay addressable — no migration), while over-long book
 * titles fall back to a truncated, digest-disambiguated name that always fits
 * the filesystem instead of crashing the save with ENAMETOOLONG.
 */
class SidecarFileNameTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `fitting names keep their exact legacy spelling`() {
        assertEquals(
            "annotation_a_b.json",
            AndroidBookArtifactPaths.sidecarName("annotation_", "a_b", "a/b"),
        )
        assertEquals(
            "deleted_annotation_a_b.json",
            AndroidBookArtifactPaths.sidecarName("deleted_annotation_", "a_b", "a/b"),
        )
        assertEquals(
            "highlights_a_b.json",
            AndroidBookArtifactPaths.sidecarName("highlights_", "a_b", "a/b"),
        )
        assertEquals(
            "textboxes_a_b.json",
            AndroidBookArtifactPaths.sidecarName("textboxes_", "a_b", "a/b"),
        )
        assertEquals(
            "rich_doc_ab.json",
            AndroidBookArtifactPaths.sidecarName("rich_doc_", "ab", "ab"),
        )
    }

    @Test
    fun `rich text and reflow paths keep their legacy layout`() {
        val bookId = "local_Some_Book.pdf_abc123"
        assertEquals(
            "rich_doc_local_Some_Book.pdf_abc123.json",
            AndroidBookArtifactPaths.richTextFile(tempFolder.root, bookId).name,
        )
        assertEquals(
            "local_Some_Book.pdf_abc123_reflow.html",
            AndroidBookArtifactPaths.reflowFile(tempFolder.root, bookId).name,
        )
    }

    @Test
    fun `over-long title falls back to a bounded deterministic name`() {
        // Book title from the reported ENAMETOOLONG crash.
        val bookId = "local_El utilitarismo_ Un sistema de la lógica (Libro VI, capítulo -- " +
            "John Stuart Mill, Esperanza Guisán Seijas_) -- Libro de bolsillo; Filosofía, " +
            "3_ª -- isbn13 9788420684321 -- a0a2c01ccc0e36fcbf43933f72eecf83 -- Anna’s Archive.pdf"
        val first = AndroidBookArtifactPaths.sidecarName(
            prefix = "highlights_",
            sanitizedId = bookId.replace("/", "_"),
            stableKey = bookId,
        )
        val second = AndroidBookArtifactPaths.sidecarName(
            prefix = "highlights_",
            sanitizedId = bookId.replace("/", "_"),
            stableKey = bookId,
        )

        assertEquals(first, second)
        assertTrue(first.startsWith("highlights_"))
        assertTrue(first.endsWith(".json"))
        assertTrue(
            "name must fit ext4/f2fs with .new/.bak headroom, was " +
                "${first.toByteArray(StandardCharsets.UTF_8).size} bytes: $first",
            first.toByteArray(StandardCharsets.UTF_8).size <=
                AndroidBookArtifactPaths.MAX_SIDECAR_NAME_BYTES,
        )
        // Still human-recognizable, not a bare digest.
        assertTrue(first.contains("utilitarismo"))
    }

    @Test
    fun `distinct long titles map to distinct names`() {
        fun name(title: String) = AndroidBookArtifactPaths.sidecarName(
            prefix = "highlights_",
            sanitizedId = title.replace("/", "_"),
            stableKey = title,
        )
        val pad = "x".repeat(300)
        assertNotEquals(name("book-A $pad"), name("book-B $pad"))
    }

    @Test
    fun `multibyte truncation never splits a code point`() {
        val bookId = "local_" + "é🎉".repeat(200) + ".pdf"
        val name = AndroidBookArtifactPaths.sidecarName(
            prefix = "highlights_",
            sanitizedId = bookId.replace("/", "_"),
            stableKey = bookId,
        )
        assertTrue(
            name.toByteArray(StandardCharsets.UTF_8).size <=
                AndroidBookArtifactPaths.MAX_SIDECAR_NAME_BYTES,
        )
        // Round-tripping through UTF-8 bytes is only lossless when no surrogate
        // pair or multibyte sequence was cut in half.
        assertEquals(name, String(name.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
    }
}
