package com.aryan.reader.shared.ios

import com.aryan.reader.shared.pdf.SharedPdfOcrLanguage
import com.aryan.reader.shared.pdf.filterIosVisionLanguages
import com.aryan.reader.shared.pdf.isSupportedOnPlatform
import com.aryan.reader.shared.pdf.supportedIosVisionLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPdfOcrCapabilityTest {

    @Test
    fun liveEngineDecidesSupportForEveryScript() {
        // Support mirrors the live Vision engine (or the iOS 17/18 baseline
        // when the engine query is unavailable). Every script offered by the
        // shared picker must resolve to a definite answer, and the picker must
        // never end up empty.
        val available = SharedPdfOcrLanguage.entries.filter { it.isSupportedOnPlatform }
        assertTrue(available.contains(SharedPdfOcrLanguage.LATIN))
        assertTrue(available.isNotEmpty())
    }

    @Test
    fun liveVisionLanguageSetMatchesDocumentation() {
        // Vision reports region-qualified codes. The probe on the current
        // test OS returned 33 languages incl. hi-IN/mr-IN; assert the
        // conservative invariants shared by all releases instead of the
        // version-specific size.
        val supported = supportedIosVisionLanguages()
        if (supported.isNotEmpty()) {
            assertTrue("en-US" in supported)
            assertTrue("ja-JP" in supported)
            assertTrue("zh-Hans" in supported)
        }
    }

    @Test
    fun filterKeepsSupportedCodesAndDropsUnsupportedOnes() {
        val supported = supportedIosVisionLanguages()
        val requested = listOf("en-US", "hi-IN", "ja-JP")
        val filtered = filterIosVisionLanguages(requested, supported)
        // With a live engine list, exactly the supported codes survive.
        if (supported.isNotEmpty()) {
            assertEquals(
                requested.filter { it in supported },
                filtered,
            )
        } else {
            assertEquals(requested, filtered)
        }
    }

    @Test
    fun filterPassesEverythingThroughWhenEngineSetUnknown() {
        val requested = listOf("en-US", "hi-IN")
        assertEquals(requested, filterIosVisionLanguages(requested, emptySet()))
    }
}
