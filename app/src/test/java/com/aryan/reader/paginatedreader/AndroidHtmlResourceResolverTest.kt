package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidHtmlResourceResolverTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `resolvePath returns files inside extraction root`() {
        val root = temp.newFolder("book")
        val image = File(root, "OEBPS/images/picture.png").apply {
            parentFile?.mkdirs()
            writeText("image")
        }

        assertEquals(
            image.canonicalPath,
            AndroidHtmlResourceResolver.resolvePath(
                chapterAbsPath = "OEBPS/chapter.xhtml",
                extractionBasePath = root.absolutePath,
                src = "images/picture.png"
            )
        )
    }

    @Test
    fun `resolvePath rejects paths that escape extraction root`() {
        val root = temp.newFolder("book")
        File(root.parentFile, "outside.png").writeText("outside")

        assertNull(
            AndroidHtmlResourceResolver.resolvePath(
                chapterAbsPath = "OEBPS/chapter.xhtml",
                extractionBasePath = root.absolutePath,
                src = "../../outside.png"
            )
        )
    }
}
