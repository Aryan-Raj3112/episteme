package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLinkAnnotationTest {
    @Test
    fun urlAnnotationAtOffsetFindsLinkInsideRange() {
        val text = linkText()

        assertEquals("chapter2.xhtml#start", text.readerUrlAnnotationAtOffset(4))
    }

    @Test
    fun urlAnnotationAtOffsetFindsLinkAtEndBoundary() {
        val text = linkText()

        assertEquals("chapter2.xhtml#start", text.readerUrlAnnotationAtOffset("Read more".length))
    }

    @Test
    fun urlAnnotationAtOffsetReturnsNullOutsideRange() {
        val text = buildAnnotatedString {
            append("Read more later")
            addStringAnnotation("URL", "chapter2.xhtml#start", 0, "Read more".length)
        }

        assertNull(text.readerUrlAnnotationAtOffset(text.length))
    }

    @Test
    fun readerExternalHrefDetectsCommonExternalSchemesCaseInsensitively() {
        assertTrue("HTTPS://example.com".isReaderExternalHref())
        assertTrue("//example.com/path".isReaderExternalHref())
        assertTrue("mailto:test@example.com".isReaderExternalHref())
        assertTrue("tel:+1234567890".isReaderExternalHref())

        assertFalse("chapter2.xhtml#start".isReaderExternalHref())
        assertFalse("#footnote-1".isReaderExternalHref())
    }

    private fun linkText() = buildAnnotatedString {
        val label = "Read more"
        append(label)
        addStringAnnotation("URL", "chapter2.xhtml#start", 0, label.length)
    }
}
