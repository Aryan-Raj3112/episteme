package com.aryan.reader.shared

/**
 * Shared layout states for the reader TTS overlay.
 *
 * Android exposes these as an explicit three-state control and persists the
 * selected state across reader sessions. Keeping the state in common code
 * lets the mobile readers use the same semantics without sharing platform
 * preference APIs or Compose details.
 */
enum class ReaderTtsOverlaySize {
    LARGE,
    MEDIUM,
    SMALL,
}

/**
 * Compose's horizontal bias equivalent for the Android reader overlay.
 * Large and medium remain on the leading side; the compact overlay moves to
 * the trailing side so it leaves more reading area unobstructed.
 */
fun readerTtsOverlayAlignmentBias(size: ReaderTtsOverlaySize): Float =
    if (size == ReaderTtsOverlaySize.SMALL) 1f else 0f

fun readerTtsOverlayAlternativeSizes(size: ReaderTtsOverlaySize): List<ReaderTtsOverlaySize> =
    ReaderTtsOverlaySize.entries.filter { it != size }

fun resolveReaderTtsOverlaySize(savedName: String?): ReaderTtsOverlaySize =
    ReaderTtsOverlaySize.entries.firstOrNull { it.name == savedName }
        ?: ReaderTtsOverlaySize.LARGE
