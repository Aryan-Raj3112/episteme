package com.aryan.reader.shared.docparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedOfficeHtmlChaptersTest {

    @Test
    fun splitsOxideFragmentOnHeadingsWithTitles() {
        val fragment = "<p>Intro</p>\n" +
            "<h1><span style=\"font-family:'Liberation Serif'\">Alpha</span></h1>\n" +
            "<p>alpha body</p>\n" +
            "<H2 CLASS=\"x\">Beta &amp; Co</H2>\n" +
            "<p>beta body</p>"

        val chapters = SharedOfficeHtmlChapters.split(fragment)

        assertEquals(listOf(null, "Alpha", "Beta & Co"), chapters.map { it.title })
        assertTrue("<p>Intro</p>" in chapters[0].html)
        assertTrue("<p>alpha body</p>" in chapters[1].html)
        assertTrue("<p>beta body</p>" in chapters[2].html)
        // Raw engine output passes through untouched, including inline spans.
        assertTrue("<span style=\"font-family:'Liberation Serif'\">Alpha</span>" in chapters[1].html)
    }

    @Test
    fun fragmentWithoutHeadingsIsOneUntitledChapter() {
        val fragment = "<p>Just body</p><hr /><p>More</p>"

        val chapters = SharedOfficeHtmlChapters.split(fragment)

        assertEquals(1, chapters.size)
        assertEquals(null, chapters.single().title)
        assertEquals(fragment, chapters.single().html)
    }

    @Test
    fun unclosedHeadingStaysBodyText() {
        val fragment = "<p>Before</p><h1>Oops<p>After</p>"

        val chapters = SharedOfficeHtmlChapters.split(fragment)

        assertEquals(1, chapters.size)
        assertEquals(fragment, chapters.single().html)
    }

    @Test
    fun barrenHeadingKeepsItsOwnChapter() {
        val fragment = "<h1>Solo</h1><h1>Next</h1><p>body</p>"

        val chapters = SharedOfficeHtmlChapters.split(fragment)

        assertEquals(listOf("Solo", "Next"), chapters.map { it.title })
        assertTrue("<p>body</p>" in chapters[1].html)
    }
}
