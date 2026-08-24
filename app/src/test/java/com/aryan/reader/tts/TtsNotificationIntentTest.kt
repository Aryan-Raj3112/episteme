package com.aryan.reader.tts

import android.content.Intent
import com.aryan.reader.shared.MobileHandoffRequestKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TtsNotificationIntentTest {
    @Test
    fun mapsAllExistingNotificationExtrasToPortableTarget() {
        val request = Intent(ACTION_OPEN_TTS_SESSION)
            .putExtra(EXTRA_TTS_BOOK_ID, "book-1")
            .putExtra(EXTRA_TTS_SOURCE_CFI, "epubcfi(/6/4)")
            .putExtra(EXTRA_TTS_START_OFFSET, 17)
            .putExtra(EXTRA_TTS_CHAPTER_INDEX, 2)
            .putExtra(EXTRA_TTS_PAGE_INDEX, 8)
            .putExtra(EXTRA_TTS_REQUEST_ID, "notification-1")
            .toMobileTtsHandoffRequest()

        assertNotNull(request)
        assertEquals(MobileHandoffRequestKind.TTS_TARGET, request?.kind)
        assertEquals("notification-1", request?.requestId)
        assertEquals("book-1", request?.ttsTarget?.bookId)
        assertEquals("epubcfi(/6/4)", request?.ttsTarget?.sourceCfi)
        assertEquals(17, request?.ttsTarget?.startOffset)
        assertEquals(2, request?.ttsTarget?.chapterIndex)
        assertEquals(8, request?.ttsTarget?.pageIndex)
    }

    @Test
    fun missingBookIdIsIgnoredAndRequestIdIsStableWithoutExtra() {
        assertNull(Intent(ACTION_OPEN_TTS_SESSION).toMobileTtsHandoffRequest())

        val first = Intent(ACTION_OPEN_TTS_SESSION)
            .putExtra(EXTRA_TTS_BOOK_ID, "book-1")
            .putExtra(EXTRA_TTS_CHAPTER_INDEX, 1)
            .toMobileTtsHandoffRequest()
        val second = Intent(ACTION_OPEN_TTS_SESSION)
            .putExtra(EXTRA_TTS_BOOK_ID, "book-1")
            .putExtra(EXTRA_TTS_CHAPTER_INDEX, 1)
            .toMobileTtsHandoffRequest()

        assertEquals(first?.requestId, second?.requestId)
    }
}
