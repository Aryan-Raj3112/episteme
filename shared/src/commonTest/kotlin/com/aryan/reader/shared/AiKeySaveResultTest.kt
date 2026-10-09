package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiKeySaveResultTest {
    @Test
    fun blankInputIsRejectedBeforeAnythingIsWritten() {
        assertEquals(AiKeySaveError.BLANK, (normalizeAiKeyEntry("") as AiKeySaveResult.Invalid).reason)
        assertEquals(AiKeySaveError.BLANK, (normalizeAiKeyEntry("   ") as AiKeySaveResult.Invalid).reason)
        assertNull(normalizedAiKeyEntry("  "))
    }

    @Test
    fun aPastedBearerPrefixIsStrippedRatherThanStored() {
        assertEquals("abc123", normalizedAiKeyEntry("Bearer abc123"))
        assertEquals("abc123", normalizedAiKeyEntry("bearer abc123"))
        assertEquals("abc123", normalizedAiKeyEntry("  abc123  "))
    }

    @Test
    fun aKeyContainingSpacesIsRejectedAsMalformed() {
        // The most common real failure: pasting a line of prose or a key with a
        // stray newline. Storing it produces a credential that can only fail
        // later, with a much less obvious error.
        val result = normalizeAiKeyEntry("my key is abc123")
        assertIs<AiKeySaveResult.Invalid>(result)
        assertEquals(AiKeySaveError.MALFORMED, result.reason)
        assertTrue(aiKeySaveErrorMessage(AiKeySaveError.MALFORMED).isNotBlank())
    }

    @Test
    fun ordinaryKeysOfAnyLengthAreAccepted() {
        // No prefix/length guessing: provider key formats differ and a wrong
        // guess would reject a valid key.
        assertTrue(normalizeAiKeyEntry("sk-abc123XYZ_-.").isSaved)
        assertTrue(normalizeAiKeyEntry("AIzaSyABCDEFGHIJKLMNOP").isSaved)
        assertTrue(normalizeAiKeyEntry("short").isSaved)
        assertEquals("AIzaSyABCDEFGHIJKLMNOP", normalizedAiKeyEntry("AIzaSyABCDEFGHIJKLMNOP"))
    }

    @Test
    fun everyFailureHasUserFacingCopy() {
        for (error in AiKeySaveError.entries) {
            val message = aiKeySaveErrorMessage(error)
            assertTrue(message.isNotBlank(), "missing message for $error")
        }
    }
}
