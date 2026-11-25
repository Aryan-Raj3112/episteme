package com.aryan.reader.shared.ui

import com.aryan.reader.shared.READER_SEARCH_BLOCK_BOUNDARY_TAGS
import com.aryan.reader.shared.READER_SEARCH_SKIPPED_TAGS
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileEpubNavigationPayloadTest {

    @Test
    fun `small navigation chunk stays inline for instant landing`() {
        val script = sharedMobileEpubNavigationScript(
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
            fragment = null,
            targetChunkIndex = 2,
            targetChunkHtml = "<p>small</p>",
        )

        assertTrue(script.contains("provideChunk(2,"))
        assertFalse(script.contains("requestChunk(2)"))
    }

    @Test
    fun `large navigation chunk uses bridge instead of inline payload`() {
        val bigChunk = "<div>" + "word ".repeat(30_000) + "</div>"
        val script = sharedMobileEpubNavigationScript(
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
            fragment = null,
            targetChunkIndex = 4,
            targetChunkHtml = bigChunk,
        )

        assertTrue(script.contains("requestChunk(4)"))
        assertFalse(script.contains("word word word word word word word word word word"))
        assertTrue(script.contains("setInterval"))
    }

    @Test
    fun `large search chunk uses bridge with retry`() {
        val bigChunk = "<div>" + "alpha ".repeat(30_000) + "</div>"
        val result = SharedMobileEpubSearchResult(
            chapterIndex = 0,
            chapterTitle = "Chapter",
            chunkIndex = 7,
            occurrenceIndex = 0,
            snippet = "alpha",
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
        )
        val script = sharedMobileEpubSearchNavigationScript(result, "alpha", bigChunk)

        assertTrue(script.contains("requestChunk(7)"))
        assertTrue(script.contains("setInterval"))
    }

    @Test
    fun `search navigation script mirrors the shared matcher tables`() {
        val result = SharedMobileEpubSearchResult(
            chapterIndex = 0,
            chapterTitle = "Chapter",
            chunkIndex = 3,
            occurrenceIndex = 1,
            snippet = "needle",
            matchQuote = "beta needle again",
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
        )
        val script = sharedMobileEpubSearchNavigationScript(result, "needle", "<p>needle</p>")

        // The script carries its own copy of the boundary/skipped tag tables
        // (no Kotlin objects inside a JS string), so a member added on only one
        // side would silently desynchronise the occurrence ordinals.
        READER_SEARCH_BLOCK_BOUNDARY_TAGS.forEach { tag ->
            assertTrue(script.contains("$tag:1,") || script.contains("$tag:1}"), "missing boundary tag $tag")
        }
        READER_SEARCH_SKIPPED_TAGS.forEach { tag ->
            assertTrue(script.contains("$tag:1,") || script.contains("$tag:1}"), "missing skipped tag $tag")
        }
        // Word-start matching, quote-anchored occurrence and a single mark class.
        assertTrue(script.contains("wordChar"))
        assertTrue(script.contains("compactQuote"))
        assertTrue(script.contains("reader-ios-search-hit"))
    }

    @Test
    fun `large scroll to end uses bridge with delayed rescroll`() {
        val bigChunk = "<div>" + "omega ".repeat(30_000) + "</div>"
        val script = sharedMobileEpubScrollToEndScript(9, bigChunk)

        assertTrue(script.contains("requestChunk(9)"))
        assertTrue(script.contains("setTimeout(scrollEnd, 500)"))
        assertTrue(bigChunk.length > ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars)
    }

    @Test
    fun `restore navigation never approximates and keeps retrying the exact target`() {
        val script = sharedMobileEpubNavigationScript(
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
            fragment = null,
            targetChunkIndex = null,
            targetChunkHtml = null,
            preferExact = true,
        )

        // The reopen restore must not fall back to a ratio or chapter-top
        // scroll: against a still-settling document those land on the chapter
        // top and overwrite the exact landing (the reported reset). It retries
        // exact-only and reports the unresolved case instead.
        assertTrue(script.contains("exactOnly: true"))
        assertFalse(script.contains("source: 'ios_mobile_fallback'"))
        assertFalse(script.contains("'content_ratio'"))
        assertTrue(script.contains("setInterval"))
        assertTrue(script.contains("web_navigation_exact_unresolved"))
    }

    @Test
    fun `explicit navigation keeps approximate feedback after the exact retry budget`() {
        val script = sharedMobileEpubNavigationScript(
            locator = ReaderLocator(chapterIndex = 0, startOffset = 0),
            fragment = null,
            targetChunkIndex = null,
            targetChunkHtml = null,
        )

        assertFalse(script.contains("exactOnly: true"))
        assertTrue(script.contains("source: 'ios_mobile'"))
        assertTrue(script.contains("source: 'ios_mobile_fallback'"))
        assertTrue(script.contains("setInterval"))
    }
}
