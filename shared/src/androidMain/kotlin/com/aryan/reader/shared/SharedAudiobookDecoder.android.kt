package com.aryan.reader.shared

/**
 * ExoPlayer (Android benchmark) decodes every format the shared import contract accepts.
 */
internal actual val sharedDecodableAudiobookExtensions: Set<String> =
    SharedAudiobookFormats.supportedExtensions
