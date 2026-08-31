package com.aryan.reader.tts

import android.content.Intent
import com.aryan.reader.shared.MobileHandoffMapper
import com.aryan.reader.shared.MobileHandoffRequest
import com.aryan.reader.shared.MobileHandoffTtsTarget

const val ACTION_OPEN_TTS_SESSION = "com.aryan.reader.tts.OPEN_SESSION"
const val EXTRA_TTS_BOOK_ID = "com.aryan.reader.tts.extra.BOOK_ID"
const val EXTRA_TTS_CHAPTER_INDEX = "com.aryan.reader.tts.extra.CHAPTER_INDEX"
const val EXTRA_TTS_SOURCE_CFI = "com.aryan.reader.tts.extra.SOURCE_CFI"
const val EXTRA_TTS_START_OFFSET = "com.aryan.reader.tts.extra.START_OFFSET"
const val EXTRA_TTS_PAGE_INDEX = "com.aryan.reader.tts.extra.PAGE_INDEX"
const val EXTRA_TTS_REQUEST_ID = "com.aryan.reader.tts.extra.REQUEST_ID"
const val EXTRA_TTS_PLAYBACK_SOURCE = "com.aryan.reader.tts.extra.PLAYBACK_SOURCE"

/** Maps native notification extras into the portable handoff contract. */
fun Intent.toMobileTtsHandoffRequest(): MobileHandoffRequest? {
    if (action != ACTION_OPEN_TTS_SESSION) return null
    val bookId = getStringExtra(EXTRA_TTS_BOOK_ID)?.takeIf { it.isNotBlank() } ?: return null
    val target = MobileHandoffTtsTarget(
        bookId = bookId,
        sourceCfi = getStringExtra(EXTRA_TTS_SOURCE_CFI)?.takeIf { it.isNotBlank() },
        startOffset = getIntExtra(EXTRA_TTS_START_OFFSET, -1).takeIf { it >= 0 },
        chapterIndex = getIntExtra(EXTRA_TTS_CHAPTER_INDEX, -1).takeIf { it >= 0 },
        pageIndex = getIntExtra(EXTRA_TTS_PAGE_INDEX, -1).takeIf { it >= 0 },
        playbackSource = getStringExtra(EXTRA_TTS_PLAYBACK_SOURCE)?.takeIf { it.isNotBlank() },
    )
    return MobileHandoffMapper.ttsTarget(
        requestId = getStringExtra(EXTRA_TTS_REQUEST_ID)?.takeIf { it.isNotBlank() }
            ?: MobileHandoffMapper.stableTtsRequestId(target),
        target = target,
    )
}
