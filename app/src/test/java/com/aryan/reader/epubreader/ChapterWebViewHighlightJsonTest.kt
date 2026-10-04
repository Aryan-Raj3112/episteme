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
            colorArgb = 0xFF12ABEF.toInt(),
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
        assertEquals(0xFF12ABEF.toInt(), obj.getInt("colorArgb"))
        // The fill, alpha included, not the bare colour. The marker applies whatever arrives here as an
        // inline `background-color` marked `!important`, so a bare `#RRGGBB` overrode the stylesheet's
        // tinted rule and painted an opaque slab over the text in the WebView only. Asserting the hex
        // here is what kept that in place; it now pins the tint.
        assertEquals("rgba(18,171,239,0.4)", obj.getString("colorCss"))
        assertEquals(6, locator.getInt("chapterIndex"))
        assertEquals(120, locator.getInt("startOffset"))
        assertEquals(145, locator.getInt("endOffset"))
        assertEquals("synced desktop text", locator.getString("textQuote"))
    }
}
