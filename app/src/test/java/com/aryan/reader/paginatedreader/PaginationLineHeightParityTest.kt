package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.AndroidEpubFormatBenchmark
import com.aryan.reader.shared.SharedNativePaginationNormalLineHeightMultiplier
import com.aryan.reader.shared.sharedPaginationLineHeightMultiplier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Android's native pagination line-height conversion is the benchmark the shared reader converts
 * for too (iOS/desktop pagination), so it must keep matching that rule exactly.
 *
 * The reader stores a WebView/CSS line-height multiplier: 1.0x is the app's normal CSS line height
 * and renders as [SharedNativePaginationNormalLineHeightMultiplier], every other position renders as
 * its own value.
 */
class PaginationLineHeightParityTest {

    @Test
    fun `android pagination keeps the web view normal multiplier rule`() {
        assertEquals(1.2f, WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER)
        assertEquals(
            SharedNativePaginationNormalLineHeightMultiplier,
            WEB_VIEW_NORMAL_LINE_HEIGHT_MULTIPLIER
        )
        assertEquals(1.2f, paginationLineHeightMultiplierForWebViewSetting(1f), 0f)
        // Every other slider position is its own value, not a scaled one.
        assertEquals(1.1f, paginationLineHeightMultiplierForWebViewSetting(1.1f), 0.0001f)
        assertEquals(1.5f, paginationLineHeightMultiplierForWebViewSetting(1.5f), 0.0001f)
        assertEquals(3f, paginationLineHeightMultiplierForWebViewSetting(3f), 0.0001f)
        assertEquals(0.7f, paginationLineHeightMultiplierForWebViewSetting(0.7f), 0.0001f)
    }

    @Test
    fun `shared pagination converts the same slider positions to the same multipliers`() {
        listOf(0.7f, 1f, 1.1f, 1.2f, 1.5f, 2f, 3f).forEach { slider ->
            assertEquals(
                "slider $slider",
                paginationLineHeightMultiplierForWebViewSetting(slider).toDouble(),
                sharedPaginationLineHeightMultiplier(
                    AndroidEpubFormatBenchmark.baseLineSpacing * slider
                ).toDouble(),
                0.0001
            )
        }
    }
}
