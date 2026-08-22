package com.aryan.reader.shared

/**
 * AVFoundation has no OGG/Opus demuxer, so those imports cannot play on iOS even
 * though the shared contract accepts them. They are rejected at import with
 * explicit feedback instead of failing inside the player.
 */
internal actual val sharedDecodableAudiobookExtensions: Set<String> =
    SharedAudiobookFormats.supportedExtensions - setOf("ogg", "opus")
