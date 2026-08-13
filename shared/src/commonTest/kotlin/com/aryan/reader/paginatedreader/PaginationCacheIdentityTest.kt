package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PaginationCacheIdentityTest {
    @Test
    fun chapterIdentityDependsOnContentNotExtractionTimestamp() {
        val first = sharedPaginationChapterContentVersion(
            chapterPath = "OEBPS/chapter.xhtml",
            htmlFilePath = "chapter.xhtml",
            htmlContent = "",
            plainTextCharacterCount = 5,
            plainTextHash = "hello".hashCode(),
            backingFileLength = 25,
            backingFileCrc32 = 42,
        )
        val same = sharedPaginationChapterContentVersion(
            chapterPath = "OEBPS/chapter.xhtml",
            htmlFilePath = "chapter.xhtml",
            htmlContent = "",
            plainTextCharacterCount = 5,
            plainTextHash = "hello".hashCode(),
            backingFileLength = 25,
            backingFileCrc32 = 42,
        )
        val changed = sharedPaginationChapterContentVersion(
            chapterPath = "OEBPS/chapter.xhtml",
            htmlFilePath = "chapter.xhtml",
            htmlContent = "",
            plainTextCharacterCount = 5,
            plainTextHash = "hello".hashCode(),
            backingFileLength = 25,
            backingFileCrc32 = 43,
        )

        assertEquals(first, same)
        assertNotEquals(first, changed)
    }

    @Test
    fun fontSignatureIgnoresExtractionDirectoryAndInputOrder() {
        val regular = FontFaceInfo("Lord", "/tmp/a/OEBPS/Fonts/Lord-Regular.ttf", FontWeight.Normal, FontStyle.Normal)
        val bold = FontFaceInfo("Lord", "/tmp/a/OEBPS/Fonts/Lord-Bold.ttf", FontWeight.Bold, FontStyle.Normal)
        val movedRegular = regular.copy(src = "/new/extraction/OEBPS/Fonts/Lord-Regular.ttf")
        val movedBold = bold.copy(src = "/new/extraction/OEBPS/Fonts/Lord-Bold.ttf")

        assertEquals(
            sharedPaginationFontSignature(listOf(regular, bold)),
            sharedPaginationFontSignature(listOf(movedBold, movedRegular)),
        )
    }
}
