package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonPayloadFallbackPolicyTest {

    @Test
    fun isEmptyJsonPayload_detectsAbsentOrZeroEntryPayloads() {
        assertTrue(isEmptyJsonPayload(null))
        assertTrue(isEmptyJsonPayload(""))
        assertTrue(isEmptyJsonPayload("   "))
        assertTrue(isEmptyJsonPayload("[]"))
        assertTrue(isEmptyJsonPayload("[ ]"))
        assertTrue(isEmptyJsonPayload("[\n]"))
        assertTrue(isEmptyJsonPayload("{} "))
        assertTrue(isEmptyJsonPayload("  {  }  "))
        assertTrue(isEmptyJsonPayload("null"))
        assertTrue(isEmptyJsonPayload(" null "))
        assertFalse(isEmptyJsonPayload("nul"))
        assertFalse(isEmptyJsonPayload("[[]]"))
        assertFalse(isEmptyJsonPayload("[ x ]"))
        assertFalse(isEmptyJsonPayload("[{\"pageIndex\":0}]"))
        assertFalse(isEmptyJsonPayload("""[{"a":1}]"""))
    }

    @Test
    fun keepNonEmptyJson_prefersCandidateWithEntries() {
        assertEquals(
            """[{"a":1}]""",
            keepNonEmptyJson(preferred = """[{"a":1}]""", fallback = """[{"b":2}]"""),
        )
    }

    @Test
    fun keepNonEmptyJson_fallsBackWhenPreferredIsEmptyOrMissing() {
        assertEquals("""[{"b":2}]""", keepNonEmptyJson(preferred = "[]", fallback = """[{"b":2}]"""))
        assertEquals("""[{"b":2}]""", keepNonEmptyJson(preferred = null, fallback = """[{"b":2}]"""))
        assertEquals("""[{"b":2}]""", keepNonEmptyJson(preferred = "  ", fallback = """[{"b":2}]"""))
    }

    @Test
    fun keepNonEmptyJson_keepsEmptyFallbackWhenBothAreEmpty() {
        assertEquals("[]", keepNonEmptyJson(preferred = "[]", fallback = "[]"))
        assertNull(keepNonEmptyJson(preferred = null, fallback = null))
        assertEquals("[]", keepNonEmptyJson(preferred = null, fallback = "[]"))
    }
}
