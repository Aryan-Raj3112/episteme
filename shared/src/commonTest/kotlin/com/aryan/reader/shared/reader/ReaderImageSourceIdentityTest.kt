package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity item B9.
 *
 * The defect these guard: Android's `EpubReaderImageReference` and the shared
 * `ReaderImageReference` each resolved their source pair in a *different* order,
 * so one image could be named from `originalSource`, typed from `sourcePath`, and
 * byte-loaded from `sourcePath` — three sources for one image. Both now go
 * through `ReaderImageSourceIdentity`.
 */
class ReaderImageSourceIdentityTest {

    @Test
    fun `resolved source wins and declared is only a fallback`() {
        val resolved = ReaderImageSourceIdentity(
            resolved = "/books/OEBPS/images/cover.jpg",
            declared = "cover.png",
        )
        assertEquals("/books/OEBPS/images/cover.jpg", resolved.effective)
        assertEquals("cover.jpg", resolved.sourceName())
        assertEquals("jpg", resolved.extension())
        assertEquals("image/jpeg", resolved.mimeType())

        // Blank resolved falls through to the author's src.
        assertEquals(
            "cover.png",
            ReaderImageSourceIdentity(resolved = "   ", declared = "cover.png").effective,
        )
        assertEquals("cover.png", ReaderImageSourceIdentity(resolved = null, declared = "cover.png").effective)
    }

    @Test
    fun `name type and filename all read the same source`() {
        // The regression in one assertion: the old code derived the name from
        // `originalSource` and the extension from `sourcePath`.
        val identity = ReaderImageSourceIdentity(
            resolved = "/books/OEBPS/art/hero.png",
            declared = "hero.jpg",
        )
        assertEquals("hero.png", identity.sourceName())
        assertEquals("png", identity.extension())
        assertEquals("image/png", identity.mimeType())
        assertEquals("hero.png", identity.suggestedDownloadFileName(altText = null, index = 0))
    }

    @Test
    fun `inline data sources have no name but keep their declared type`() {
        val png = ReaderImageSourceIdentity(resolved = "data:image/png;base64,AAAA")
        assertTrue(png.isInlineDataUri)
        assertNull(png.sourceName())
        assertEquals("png", png.extension())
        assertEquals("image/png", png.mimeType())

        // A data URI with no file extension still types correctly, via the MIME.
        // "jpeg" normalizes to the "jpg" extension, matching Android's original mapping.
        assertEquals("jpg", ReaderImageSourceIdentity(resolved = "data:image/jpeg,RAW").extension())
        assertEquals(
            "image/jpeg",
            ReaderImageSourceIdentity(resolved = "data:image/jpeg,RAW").mimeType(),
        )
    }

    @Test
    fun `unknown sources fall back to the image wildcard and a png file name`() {
        val unknown = ReaderImageSourceIdentity(resolved = "/books/OEBPS/blob")
        assertEquals("blob", unknown.sourceName())
        assertNull(unknown.extension())
        assertEquals("image/&#42;".replace("&#42;", "*"), unknown.mimeType())
        assertEquals("blob.png", unknown.suggestedDownloadFileName(altText = null, index = 0))
    }

    @Test
    fun `fragments and query strings never leak into the name`() {
        assertEquals(
            "plate.png",
            ReaderImageSourceIdentity(resolved = "/a/b/plate.png#page12").sourceName(),
        )
        assertEquals(
            "plate.png",
            ReaderImageSourceIdentity(resolved = "https://cdn/plate.png?v=2").sourceName(),
        )
        // Backslashes are normalized so a Windows-style path still yields a name.
        assertEquals(
            "plate.png",
            ReaderImageSourceIdentity(resolved = "C:\\books\\plate.png").sourceName(),
        )
    }

    @Test
    fun `alt text wins the download name and illegal characters are replaced`() {
        val identity = ReaderImageSourceIdentity(resolved = "/a/b/pic.jpg")
        assertEquals("Cover art.jpg", identity.suggestedDownloadFileName(altText = "Cover art", index = 3))
        assertEquals(
            "a_b_c.jpg",
            identity.suggestedDownloadFileName(altText = "a/b:c", index = 3),
        )
        // No usable alt text and no name -> positional fallback.
        assertEquals("image-4.png", ReaderImageSourceIdentity(resolved = " ").suggestedDownloadFileName(null, 3))
    }

    @Test
    fun `every recognised image format maps to a type`() {
        val expected = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "bmp" to "image/bmp",
            "svg" to "image/svg+xml",
            "avif" to "image/avif",
        )
        for ((extension, mime) in expected) {
            val identity = ReaderImageSourceIdentity(resolved = "/a/b/pic.$extension")
            assertEquals(extension, identity.extension(), "extension for .$extension")
            assertEquals(mime, identity.mimeType(), "mime for .$extension")
        }
        // A non-image extension is not an image.
        assertNull(ReaderImageSourceIdentity(resolved = "/a/b/notes.txt").extension())
    }

    @Test
    fun `a shared epub resource url reports the extension of its entry`() {
        // The URL has no extension of its own; the entry name lives after the book id.
        val identity = ReaderImageSourceIdentity(resolved = "epubres://book-1/OEBPS/images/fig%20one.png")
        assertEquals("png", identity.extension())
        assertEquals("image/png", identity.mimeType())
    }
}
