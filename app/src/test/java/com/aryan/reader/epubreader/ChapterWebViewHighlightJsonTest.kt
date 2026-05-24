package com.aryan.reader.epubreader

import com.aryan.reader.shared.ReaderLocator
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterWebViewHighlightJsonTest {

    @Test
    fun `webview highlight json keeps shared locator offsets`() {
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "desktop:6:120:145",
            text = "synced desktop text",
            color = HighlightColor.GREEN,
            chapterIndex = 6,
            locator = ReaderLocator(
                chapterIndex = 6,
                pageIndex = 2,
                startOffset = 120,
                endOffset = 145,
                textQuote = "synced desktop text",
                cfi = "desktop:6:120:145"
            )
        )

        val obj = JSONArray(highlightsJsonForWebView(listOf(highlight))).getJSONObject(0)
        val locator = obj.getJSONObject("locator")

        assertEquals("desktop:6:120:145", obj.getString("cfi"))
        assertEquals("user-highlight-green", obj.getString("cssClass"))
        assertEquals(6, locator.getInt("chapterIndex"))
        assertEquals(120, locator.getInt("startOffset"))
        assertEquals(145, locator.getInt("endOffset"))
        assertEquals("synced desktop text", locator.getString("textQuote"))
    }
}
