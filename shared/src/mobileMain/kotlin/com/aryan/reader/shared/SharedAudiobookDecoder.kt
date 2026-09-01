package com.aryan.reader.shared

/**
 * Extensions the current mobile platform's audio decoder can actually play.
 *
 * `SharedAudiobookFormats.supportedExtensions` is the cross-platform import
 * contract (Android/ExoPlayer decodes all of them). Some platform decoders
 * accept fewer formats — iOS AVPlayer has no OGG/Opus demuxer — so imports are
 * split against this set instead of silently failing at play time, mirroring
 * Android's explicit unsupported-file feedback.
 */
internal expect val sharedDecodableAudiobookExtensions: Set<String>

internal data class SharedAudiobookDecodeSplit<T>(
    val decodable: List<T>,
    val unsupported: List<T>,
)

internal fun <T> splitFilesByAudiobookDecodability(
    files: List<T>,
    fileName: (T) -> String,
): SharedAudiobookDecodeSplit<T> {
    val decodable = mutableListOf<T>()
    val unsupported = mutableListOf<T>()
    files.forEach { file ->
        val extension = fileName(file).substringAfterLast('.', "").lowercase()
        if (extension in sharedDecodableAudiobookExtensions) {
            decodable += file
        } else {
            unsupported += file
        }
    }
    return SharedAudiobookDecodeSplit(decodable, unsupported)
}
