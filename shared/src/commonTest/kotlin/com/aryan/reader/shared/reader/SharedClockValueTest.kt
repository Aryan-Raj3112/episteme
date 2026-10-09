package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `EPUB RS §9` clock values (its own H.4).
 *
 * The grammar is deliberately exercised in full rather than only on the two shapes real books use
 * (`H:MM:SS.mmm` and `HH:MM:SS.mmm`), because every way of getting it wrong is a silent
 * mis-sync: a clip that starts at the wrong paragraph, or one that runs to EOF.
 *
 * The two shapes that appear in every ReadBeyond book are covered by `realAudiobookShapes`.
 */
class SharedClockValueTest {

    private fun ms(raw: String) = parseSharedClockValueMs(raw)

    // --- the colon forms ---------------------------------------------------------------------

    @Test
    fun `the shape every real audiobook uses`() {
        // All 2315 pars in the reference book use one of these two.
        assertEquals(0L, ms("0:00:00.000"))
        assertEquals(3720L, ms("0:00:03.720"))
        assertEquals(3720L, ms("00:00:03.720"))
        assertEquals(3_723_000L, ms("1:02:03"))
        assertEquals(3_723_000L, ms("01:02:03.000"))
    }

    /**
     * Hours are not fixed-width and not capped at 24. A parser that validates hours as `2DIGIT`
     * rejects a 50-hour audiobook outright, and one that caps at 23 wraps it.
     */
    @Test
    fun `hours are unbounded and not two digits`() {
        assertEquals(449_976_000L, ms("124:59:36"))
        assertEquals(3_723_000L, ms("1:02:03"))
        assertEquals(3_600_123_000L, ms("1000:02:03"))
    }

    /**
     * `h:mm:ss:mmm` is a distinct alternative to a fractional second, not a 4-digit seconds field.
     * Handling only the fractional form loses the last component entirely.
     */
    @Test
    fun `a trailing colon-milliseconds field is honoured`() {
        assertEquals(3_723_004L, ms("1:02:03:004"))
        // Reading the last field as seconds instead gives 1:02:04:004 -- off by a second, and still
        // a plausible-looking clock value, which is what makes it worth a test.
        assertEquals(3_723_004L, ms("1:02:03:004"))
        assertEquals(0L, ms("0:00:00:000"))
        // The two forms are mutually exclusive; both in one value is malformed, not additive.
        assertNull(ms("1:02:03.5:004"))
    }

    @Test
    fun `a partial clock value has no hours`() {
        assertEquals(56_780L, ms("00:56.78"))
        assertEquals(3_000L, ms("00:03"))
        assertEquals(3_000L, ms("0:03"))
    }

    @Test
    fun `fractional seconds are milliseconds padded to three digits`() {
        assertEquals(500L, ms("0:00:00.5"))
        assertEquals(780L, ms("0:00:00.78"))
        assertEquals(50L, ms("0:00:00.05"))
        assertEquals(5L, ms("0:00:00.005"))
        // Beyond three digits is sub-millisecond; the extra precision is not representable and is
        // dropped rather than rounded into the wrong millisecond.
        assertEquals(5L, ms("0:00:00.0059"))
    }

    // --- the timecount forms -----------------------------------------------------------------

    @Test
    fun `a bare number is seconds not milliseconds`() {
        assertEquals(12_345L, ms("12.345"))
        assertEquals(12_000L, ms("12"))
        assertEquals(0L, ms("0"))
    }

    @Test
    fun `every metric is honoured and ms wins over s`() {
        assertEquals(2_345L, ms("2345ms"))
        assertEquals(76_200L, ms("76.2s"))
        assertEquals(780_000L, ms("13min"))
        assertEquals(25_200_750L, ms("7.75h"))
        // `2345ms` read as `2345s` would be off by 1000x -- the classic metric-ordering bug.
        assertEquals(2_345L, ms("2345ms"))
    }

    @Test
    fun `metrics are case insensitive`() {
        assertEquals(2_345L, ms("2345MS"))
        assertEquals(780_000L, ms("13MIN"))
        assertEquals(3_600_000L, ms("1H"))
    }

    // --- rejection ----------------------------------------------------------------------------

    /**
     * Null, not zero. `RS §9.2.2` treats a missing `clipEnd` as "to the end of the media", which is
     * a different decision from a malformed value, so the two must not collapse into the same answer.
     */
    @Test
    fun `malformed values are null rather than zero`() {
        for (raw in listOf(
            "", "   ", "abc", "-5", "1:2:3:4:5", "1:", ":30", "::", "1:xx:30",
            "12.", ".5", "1.2.3", "h", "ms", "12:xx", "1:02:03:", "--5"
        )) {
            assertNull(ms(raw), "expected null for '$raw'")
        }
    }

    @Test
    fun `out of range fields are rejected`() {
        // Minutes and seconds are 2DIGIT, so 60+ is not a clock value.
        assertNull(ms("0:60:00"))
        assertNull(ms("0:00:60"))
        assertNull(ms("1:02:99"))
        // The millisecond field of the 4-part form is exactly 3 digits.
        assertNull(ms("1:02:03:1000"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        // Attribute values in real files carry stray whitespace from templating.
        assertEquals(3_720L, ms("  0:00:03.720  "))
        assertEquals(2_345L, ms("\t2345ms\n"))
    }
}