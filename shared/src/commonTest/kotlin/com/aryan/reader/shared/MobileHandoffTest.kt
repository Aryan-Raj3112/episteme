package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MobileHandoffTest {
    private val file = MobileHandoffFileIdentity(
        name = "book.epub",
        path = "/tmp/request/book.epub",
        contentId = "sha256-book",
        size = 42L,
        lastModifiedTimestamp = 99L,
    )

    @Test
    fun codecRoundTripKeepsFileAndFullTtsLocator() {
        val target = MobileHandoffTtsTarget(
            bookId = "book",
            sourceCfi = "epubcfi(/6/4)",
            startOffset = 12,
            chapterIndex = 3,
            pageIndex = 9,
            blockIndex = 2,
            charOffset = 7,
        )
        val envelope = MobileHandoffEnvelope(
            requests = listOf(
                MobileHandoffMapper.externalFile(
                    requestId = "external-1",
                    file = file,
                    openMode = MobileHandoffOpenMode.TEMPORARY,
                    cleanupPolicy = MobileHandoffCleanupPolicy.DELETE_ON_CONSUME,
                    autoOpen = false,
                    createdAtMs = 123L,
                ),
                MobileHandoffMapper.ttsTarget(
                    requestId = "tts-1",
                    target = target,
                    createdAtMs = 124L,
                ),
            ),
        )

        val decoded = MobileHandoffCodec.decodeOrEmpty(MobileHandoffCodec.encode(envelope))

        assertEquals(envelope, decoded)
        assertEquals(target, decoded.requests[1].ttsTarget)
        assertEquals(file, decoded.requests[0].files.single())
    }

    @Test
    fun codecRoundTripKeepsPlaybackSourceAndAudiobookRoutingFlag() {
        val audiobookTarget = MobileHandoffTtsTarget(
            bookId = "book",
            playbackSource = TTS_PLAYBACK_SOURCE_AUDIOBOOK,
        )
        val readerTarget = MobileHandoffTtsTarget(
            bookId = "book",
            playbackSource = "READER",
        )
        val legacyTarget = MobileHandoffTtsTarget(bookId = "book")

        val decoded = MobileHandoffCodec.decodeOrEmpty(
            MobileHandoffCodec.encode(
                MobileHandoffEnvelope(
                    requests = listOf(
                        MobileHandoffMapper.ttsTarget(requestId = "tts-audiobook", target = audiobookTarget),
                        MobileHandoffMapper.ttsTarget(requestId = "tts-reader", target = readerTarget),
                        MobileHandoffMapper.ttsTarget(requestId = "tts-legacy", target = legacyTarget),
                    ),
                )
            )
        )

        assertEquals(TTS_PLAYBACK_SOURCE_AUDIOBOOK, decoded.requests[0].ttsTarget?.playbackSource)
        assertTrue(decoded.requests[0].ttsTarget?.isAudiobookListening == true)
        assertEquals("READER", decoded.requests[1].ttsTarget?.playbackSource)
        assertFalse(decoded.requests[1].ttsTarget?.isAudiobookListening == true)
        assertEquals(null, decoded.requests[2].ttsTarget?.playbackSource)
        assertFalse(decoded.requests[2].ttsTarget?.isAudiobookListening == true)
    }

    @Test
    fun replayUsesTtsPrecedenceThenFifoWithinKind() {
        val importRequest = MobileHandoffRequest(
            requestId = "import",
            kind = MobileHandoffRequestKind.IMPORT_BATCH,
            files = listOf(file),
            createdAtMs = 1L,
        )
        val externalRequest = MobileHandoffMapper.externalFile(
            requestId = "external",
            file = file,
            openMode = MobileHandoffOpenMode.LIBRARY_COPY,
            cleanupPolicy = MobileHandoffCleanupPolicy.KEEP,
            createdAtMs = 2L,
        )
        val ttsRequest = MobileHandoffMapper.ttsTarget(
            requestId = "tts",
            target = MobileHandoffTtsTarget(bookId = "book", chapterIndex = 1),
            createdAtMs = 3L,
        )

        val envelope = listOf(importRequest, externalRequest, ttsRequest)
            .fold(MobileHandoffEnvelope(), MobileHandoffReducer::enqueue)

        assertEquals("tts", MobileHandoffReducer.replay(envelope)?.requestId)
        val withoutTts = MobileHandoffReducer.consume(envelope, "tts")
        assertEquals("external", MobileHandoffReducer.replay(withoutTts)?.requestId)
    }

    @Test
    fun duplicateRequestIdAndFileIdentityDoNotReplayTwice() {
        val first = MobileHandoffMapper.externalFile(
            requestId = "native-callback",
            file = file,
            openMode = MobileHandoffOpenMode.TEMPORARY,
            cleanupPolicy = MobileHandoffCleanupPolicy.DELETE_ON_CONSUME,
        )
        val duplicateWithDifferentId = first.copy(requestId = "second-callback")
        val once = MobileHandoffReducer.enqueue(MobileHandoffEnvelope(), first)
        val twice = MobileHandoffReducer.enqueue(once, duplicateWithDifferentId)

        assertEquals(1, twice.requests.size)
        assertEquals("native-callback", twice.requests.single().requestId)
    }

    @Test
    fun failedRequestRetainsMetadataAndCanBeRetried() {
        val request = MobileHandoffRequest(
            requestId = "import",
            kind = MobileHandoffRequestKind.IMPORT_BATCH,
            files = listOf(file),
            failedCount = 2,
            message = "2 files failed",
            metadata = mapOf("source" to "picker"),
        )
        val failed = MobileHandoffReducer.fail(
            MobileHandoffReducer.enqueue(MobileHandoffEnvelope(), request),
            requestId = "import",
            message = "provider unavailable",
            nowMs = 10L,
        )
        val retried = MobileHandoffReducer.retryFailed(failed)
        val replay = MobileHandoffReducer.replay(retried)

        assertNotNull(replay)
        assertEquals(3, replay.failedCount)
        assertEquals(1, replay.attemptCount)
        assertEquals("provider unavailable", replay.message)
        assertEquals(mapOf("source" to "picker"), replay.metadata)
        assertEquals(MobileHandoffRequestState.PENDING, replay.state)
    }

    @Test
    fun deleteOnFailureRequestIsRemovedAfterFailure() {
        val request = MobileHandoffMapper.externalFile(
            requestId = "temporary-failure",
            file = file,
            openMode = MobileHandoffOpenMode.TEMPORARY,
            cleanupPolicy = MobileHandoffCleanupPolicy.DELETE_ON_FAILURE,
        )
        val failed = MobileHandoffReducer.fail(
            MobileHandoffReducer.enqueue(MobileHandoffEnvelope(), request),
            requestId = request.requestId,
            message = "unsupported",
        )

        assertTrue(failed.requests.isEmpty())
    }

    @Test
    fun consumeRemovesOnlyItsRequestRegardlessOfCleanupPolicy() {
        val temporary = MobileHandoffMapper.externalFile(
            requestId = "temporary",
            file = file,
            openMode = MobileHandoffOpenMode.TEMPORARY,
            cleanupPolicy = MobileHandoffCleanupPolicy.DELETE_ON_CONSUME,
        )
        val library = temporary.copy(
            requestId = "library",
            openMode = MobileHandoffOpenMode.LIBRARY_COPY,
            cleanupPolicy = MobileHandoffCleanupPolicy.KEEP,
        )
        val consumed = MobileHandoffReducer.consume(
            listOf(temporary, library).fold(MobileHandoffEnvelope(), MobileHandoffReducer::enqueue),
            "temporary",
        )

        assertFalse(consumed.requests.any { it.requestId == "temporary" })
        assertEquals(MobileHandoffRequestState.PENDING, consumed.requests.single().state)

        val consumedLibrary = MobileHandoffReducer.consume(consumed, "library")
        assertTrue(consumedLibrary.requests.isEmpty())
    }

    @Test
    fun lifecycleInactiveRequiresFinalFlush() {
        assertEquals(MobilePdfLifecycleAction.NORMAL_SAVE, mobilePdfLifecycleAction(isActive = true))
        assertEquals(MobilePdfLifecycleAction.FINAL_FLUSH, mobilePdfLifecycleAction(isActive = false))
        assertTrue(mobilePdfLifecycleAction(false) == MobilePdfLifecycleAction.FINAL_FLUSH)
    }
}
